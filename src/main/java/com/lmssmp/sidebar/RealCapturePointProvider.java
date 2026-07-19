package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
/**
 * Replaces PlaceholderCapturePointProvider. Reads control points from
 * real entities tagged "control_point" in the player's level. Each such
 * entity is expected to carry its own per-entity scoreboard scores:
 *
 *   capture_point           -- this point's number (1, 2, 3, ...), same
 *                              value the datapack's
 *                              @n[scores={capture_point=N}] selectors use
 *   capture_point_enabled   -- 0 = hidden/not placed, 1 = shown
 *   capture_point_team      -- 0 = uncaptured, 1-5 = owning team
 *   capture_point_capturing -- 0 = idle, 1 = one team capturing,
 *                              2 = 2+ teams contesting
 *   capture_point_cteam     -- team currently capturing (meaningful
 *                              whenever capturing != 0)
 *   capture_point_time      -- ticks remaining until capture completes
 *
 * Uses Level#getEntitiesOfClass(Class, AABB, Predicate) to find tagged
 * entities -- a long-stable public API -- rather than
 * ServerLevel#getEntities().getAll(), which turned out not to be
 * visible in 26.2's mappings. The search box covers the whole loaded
 * world vertically and out to the vanilla max world-border radius; if
 * your control points can somehow sit outside that, widen SEARCH_RADIUS
 * below.
 *
 * VERIFY (26.2, unobfuscated): this assumes Entity still implements
 * ScoreHolder directly (true since ~1.20.3), so
 * scoreboard.getPlayerScoreInfo(entity, objective) works unwrapped. If
 * that changed, only this class needs updating -- SidebarContentBuilder
 * only ever sees CapturePointEntry.
 *
 * Read-only, same as everywhere else in this mod: never creates,
 * re-tags, or moves anything.
 *
 * PERFORMANCE (two tiers of caching):
 *
 * 1) The per-tick CACHE below stops N online players from redundantly
 *    recomputing the identical global capture-point list N times in the
 *    same tick -- capture points are level-wide, not per-player. This
 *    alone doesn't help with 1 player online, since there's no
 *    cross-player redundancy to remove in that case.
 *
 * 2) ENTITY_CACHE is the fix that actually matters even solo: profiling
 *    showed the real cost isn't the scoreboard reads, it's
 *    getEntitiesOfClass itself -- EntitySectionStorage walks every
 *    non-empty entity section in the loaded world to find the tagged
 *    ones, and that's expensive regardless of how few control points
 *    exist, every time it runs. But control point armor stands are
 *    essentially static -- they aren't spawned or removed moment to
 *    moment -- so there is no reason to redo that world-wide entity scan
 *    5x/sec (the sidebar refresh rate). The entity scan itself is now
 *    only redone every ENTITY_RESCAN_INTERVAL_TICKS; the fast-changing
 *    per-entity scores (team, capturing state, time remaining) are
 *    still read fresh on every call, off the entities found by the last
 *    scan. A cached entity that's since been removed/unloaded is
 *    filtered out cheaply (an isRemoved() check, not a re-scan) so a
 *    despawned control point disappears immediately rather than waiting
 *    for the next rescan; a *newly added* one will take up to
 *    ENTITY_RESCAN_INTERVAL_TICKS to appear, which is the deliberate
 *    trade-off -- tune that constant down if you need faster pickup of
 *    newly placed control points.
 */
public final class RealCapturePointProvider implements CapturePointProvider {

	private static final String TAG = "Capture_Point";

	private static final String OBJ_ORDER = "capture_point";
	private static final String OBJ_ENABLED = "capture_point_enabled";
	private static final String OBJ_TEAM = "capture_point_team";
	private static final String OBJ_CAPTURING = "capture_point_capturing";
	private static final String OBJ_CTEAM = "capture_point_cteam";
	private static final String OBJ_TIME = "capture_point_time";

	/** Vanilla's default max world-border radius, used as the search box half-width. */
	private static final double SEARCH_RADIUS = 10000;

	/**
	 * How often the expensive world-wide entity scan (getEntitiesOfClass)
	 * is redone, in ticks. 100 ticks = 5 seconds. Control point armor
	 * stands are effectively static, so this can be much slower than the
	 * sidebar's own refresh rate without anyone noticing -- lower this if
	 * your setup adds/removes control points at runtime and needs faster
	 * pickup of new ones.
	 */
	private static final long ENTITY_RESCAN_INTERVAL_TICKS = 200;

	/**
	 * Hardcoded generous vertical range instead of calling
	 * Level#getMinBuildHeight()/getMaxBuildHeight() -- those weren't
	 * resolving in this project's environment. Comfortably covers every
	 * build-height range Minecraft has shipped with (including the
	 * -64..320 range) with wide margin; widen further if your world uses
	 * a custom dimension with a taller column.
	 */
	private static final double MIN_Y = -64;
	private static final double MAX_Y = 320;

	/**
	 * One cache entry per ServerLevel, holding the tick it was computed
	 * on plus the resulting list. WeakHashMap so a level that's unloaded
	 * (e.g. server shutdown/reload in a dev environment) doesn't pin
	 * memory forever -- ServerLevel instances aren't reused across
	 * server restarts anyway.
	 */
	private static final Map<ServerLevel, CacheEntry> CACHE = new WeakHashMap<>();

	private record CacheEntry(long tick, List<CapturePointEntry> entries) {
	}

	/**
	 * Separate, longer-lived cache of just the *tagged entities*
	 * themselves (not their scores) -- this is what lets the expensive
	 * getEntitiesOfClass call happen far less often than the per-tick
	 * score reads.
	 */
	private static final Map<ServerLevel, EntityCacheEntry> ENTITY_CACHE = new WeakHashMap<>();

	private record EntityCacheEntry(long tick, List<Entity> entities) {
	}

	@Override
	public List<CapturePointEntry> getCapturePoints(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		long currentTick = level.getServer().getTickCount();

		CacheEntry cached = CACHE.get(level);
		if (cached != null && cached.tick() == currentTick) {
			return cached.entries();
		}

		List<CapturePointEntry> entries = computeCapturePoints(level);
		CACHE.put(level, new CacheEntry(currentTick, entries));
		return entries;
	}

	/**
	 * The score-reading half of the work, unchanged in behavior from
	 * before -- just now fed by taggedEntities(level) instead of doing
	 * its own scan every call.
	 */
	private static List<CapturePointEntry> computeCapturePoints(ServerLevel level) {
		Scoreboard scoreboard = level.getServer().getScoreboard();
		List<Entity> taggedEntities = taggedEntities(level);

		List<CapturePointEntry> entries = new ArrayList<>();

		for (Entity entity : taggedEntities) {
			int order = readScore(scoreboard, entity, OBJ_ORDER, -1);
			if (order < 0) {
				// No order assigned yet -- skip rather than guess a position.
				continue;
			}

			boolean enabled = readScore(scoreboard, entity, OBJ_ENABLED, 0) != 0;
			int team = readScore(scoreboard, entity, OBJ_TEAM, 0);
			int capturing = readScore(scoreboard, entity, OBJ_CAPTURING, 0);
			int cteam = readScore(scoreboard, entity, OBJ_CTEAM, 0);
			int time = readScore(scoreboard, entity, OBJ_TIME, 0);

			entries.add(new CapturePointEntry(order, enabled, team, capturing, cteam, time));
		}

		entries.sort(Comparator.comparingInt(CapturePointEntry::order));
		return entries;
	}

	/**
	 * Returns the tagged control-point entities for this level, rescanning
	 * the world only every ENTITY_RESCAN_INTERVAL_TICKS. Between rescans,
	 * the cached list is filtered for entities that have since been
	 * removed/unloaded (a cheap isRemoved() check per entry, not another
	 * world scan), so a despawned control point still disappears
	 * immediately rather than lingering until the next rescan.
	 */
	private static List<Entity> taggedEntities(ServerLevel level) {
		long currentTick = level.getServer().getTickCount();

		EntityCacheEntry cached = ENTITY_CACHE.get(level);
		if (cached != null && currentTick - cached.tick() < ENTITY_RESCAN_INTERVAL_TICKS) {
			List<Entity> stillPresent = new ArrayList<>(cached.entities().size());
			for (Entity entity : cached.entities()) {
				if (!entity.isRemoved()) {
					stillPresent.add(entity);
				}
			}
			return stillPresent;
		}

		List<Entity> fresh = scanForTaggedEntities(level);
		ENTITY_CACHE.put(level, new EntityCacheEntry(currentTick, fresh));
		return fresh;
	}

	/** The actual world-wide entity scan -- now only called once every ENTITY_RESCAN_INTERVAL_TICKS. */
	private static List<Entity> scanForTaggedEntities(ServerLevel level) {
		AABB searchBounds = new AABB(
				-SEARCH_RADIUS, MIN_Y, -SEARCH_RADIUS,
				SEARCH_RADIUS, MAX_Y, SEARCH_RADIUS
		);

		return level.getEntitiesOfClass(
				Entity.class,
				searchBounds,
				entity -> entity.entityTags().contains(TAG)
		);
	}

	private static int readScore(Scoreboard scoreboard, Entity entity, String objectiveName, int fallback) {
		Objective objective = scoreboard.getObjective(objectiveName);
		if (objective == null) {
			return fallback;
		}
		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(entity, objective);
		return info != null ? info.value() : fallback;
	}
}