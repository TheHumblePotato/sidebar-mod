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
 * No caching yet -- every call walks all loaded entities. Fine given
 * the 4-tick refresh interval; a later milestone can cache this
 * once-per-tick (not once-per-player) if profiling says it's needed.
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
	private static final double SEARCH_RADIUS = 3.0E7;

	/**
	 * Hardcoded generous vertical range instead of calling
	 * Level#getMinBuildHeight()/getMaxBuildHeight() -- those weren't
	 * resolving in this project's environment. Comfortably covers every
	 * build-height range Minecraft has shipped with (including the
	 * -64..320 range) with wide margin; widen further if your world uses
	 * a custom dimension with a taller column.
	 */
	private static final double MIN_Y = -2048;
	private static final double MAX_Y = 2048;

	@Override
	public List<CapturePointEntry> getCapturePoints(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Scoreboard scoreboard = level.getServer().getScoreboard();

		AABB searchBounds = new AABB(
				-SEARCH_RADIUS, MIN_Y, -SEARCH_RADIUS,
				SEARCH_RADIUS, MAX_Y, SEARCH_RADIUS
		);
		
		List<Entity> taggedEntities = level.getEntitiesOfClass(
				Entity.class,
				searchBounds,
				entity -> entity.entityTags().contains(TAG)
		);

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

	private static int readScore(Scoreboard scoreboard, Entity entity, String objectiveName, int fallback) {
		Objective objective = scoreboard.getObjective(objectiveName);
		if (objective == null) {
			return fallback;
		}
		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(entity, objective);
		return info != null ? info.value() : fallback;
	}
}
