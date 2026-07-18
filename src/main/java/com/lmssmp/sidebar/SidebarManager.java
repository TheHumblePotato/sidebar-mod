package com.lmssmp.sidebar;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public per-player sidebar API. Builds and sends the vanilla scoreboard
 * packets directly to one player's connection, bypassing the shared
 * world scoreboard entirely -- this class never touches
 * server.getScoreboard(), Scoreboard#setDisplayObjective, or
 * playerManager.broadcast(). The sidebar is client-side only.
 *
 * Milestone 11 splits what used to be one always-rebuild showSidebar()
 * method into three purpose-built ones:
 * - createSidebar: full initial build (join, or after removeSidebar)
 * - updateSidebar: in-place line-level diff against the previous content
 * - removeSidebar: tear the sidebar down entirely
 * The caller (SidebarMod) decides which one applies; this class no
 * longer has an "always rebuild everything" entry point at all.
 *
 * Each line is shown using a stable, internal fake score holder
 * ("line_0", "line_1", ...) derived purely from the line's position in
 * the list, never from its text -- unchanged since Milestone 4, and the
 * reason line-level diffing is possible at all: a line's identity never
 * shifts just because its text did. Line scores are computed as
 * SCORE_BASE - index rather than lines.size() - index, so a line's score
 * (and therefore its packet) never needs to change just because the
 * sidebar grew or shrank elsewhere -- only lines whose Component
 * actually changed get sent.
 *
 * As of 26.2, ClientboundSetObjectivePacket and
 * ClientboundSetDisplayObjectivePacket both take a real Objective
 * instance and read their fields off it, rather than raw name/title/
 * renderType arguments. SCRATCH_SCOREBOARD below is a throwaway
 * Scoreboard that exists only to satisfy Objective's constructor -- it
 * is never registered with the server and never ticked.
 */
public final class SidebarManager {

	private static final String OBJECTIVE_NAME = "lmssmp_sidebar";
	private static final String LINE_HOLDER_PREFIX = "line_";

	/**
	 * Base value line_0's score is computed from; each later line gets
	 * one less. Comfortably above any realistic sidebar length, and
	 * deliberately independent of the current line count -- see class
	 * javadoc.
	 */
	private static final int SCORE_BASE = 1000;

	private static final Scoreboard SCRATCH_SCOREBOARD = new Scoreboard();

	/** One SidebarState per online player, keyed by UUID. */
	private static final Map<UUID, SidebarState> STATES = new ConcurrentHashMap<>();

	private SidebarManager() {
	}

	private static Objective buildObjective(Component title) {
		return new Objective(
				SCRATCH_SCOREBOARD,
				OBJECTIVE_NAME,
				ObjectiveCriteria.DUMMY,
				title,
				ObjectiveCriteria.RenderType.INTEGER,
				false,
				null
		);
	}

	/**
	 * Creates a player's sidebar from nothing: a fresh objective, the
	 * display-slot packet, and one score packet per line. Use this when
	 * the player has no sidebar on their client yet -- on join, or after
	 * removeSidebar(). For an existing sidebar that just changed, use
	 * updateSidebar instead; it never resends the objective-creation or
	 * display-slot packets, which is what keeps the client from ever
	 * losing the sidebar during a normal update.
	 */
	public static void createSidebar(ServerPlayer player, SidebarContent content) {
		Objective objective = buildObjective(content.title());

		player.connection.send(new ClientboundSetObjectivePacket(
				objective,
				ClientboundSetObjectivePacket.METHOD_ADD
		));

		player.connection.send(new ClientboundSetDisplayObjectivePacket(
				DisplaySlot.SIDEBAR,
				objective
		));

		List<Component> lines = content.lines();
		for (int i = 0; i < lines.size(); i++) {
			sendLine(player, i, lines.get(i));
		}

		STATES.computeIfAbsent(player.getUUID(), id -> new SidebarState())
				.setLastContent(content);
	}

	/**
	 * Updates an already-created sidebar in place. Never sends
	 * ClientboundSetObjectivePacket's METHOD_REMOVE/METHOD_ADD and never
	 * resends the display-slot packet -- the objective stays exactly as
	 * the client already knows it, so there's nothing for the client to
	 * lose or briefly stop seeing.
	 *
	 * Lines are compared by position: if oldContent and newContent have
	 * the same (equal) Component at index i, nothing is sent for that
	 * line. If they differ and newContent has a line there, one
	 * ClientboundSetScorePacket is sent for it. If newContent has fewer
	 * lines than oldContent, the now-missing positions get a
	 * ClientboundResetScorePacket, which deletes a single score holder
	 * from an objective without touching any other line. A changed title
	 * uses ClientboundSetObjectivePacket's METHOD_CHANGE, which updates
	 * an existing objective's title in place -- no removal involved.
	 */
	public static void updateSidebar(ServerPlayer player, SidebarContent oldContent, SidebarContent newContent) {
		if (!oldContent.title().equals(newContent.title())) {
			player.connection.send(new ClientboundSetObjectivePacket(
					buildObjective(newContent.title()),
					ClientboundSetObjectivePacket.METHOD_CHANGE
			));
		}

		List<Component> oldLines = oldContent.lines();
		List<Component> newLines = newContent.lines();
		int maxSize = Math.max(oldLines.size(), newLines.size());

		for (int i = 0; i < maxSize; i++) {
			Component oldLine = i < oldLines.size() ? oldLines.get(i) : null;
			Component newLine = i < newLines.size() ? newLines.get(i) : null;

			if (Objects.equals(oldLine, newLine)) {
				continue;
			}

			if (newLine == null) {
				player.connection.send(new ClientboundResetScorePacket(
						LINE_HOLDER_PREFIX + i,
						OBJECTIVE_NAME
				));
			} else {
				sendLine(player, i, newLine);
			}
		}

		STATES.computeIfAbsent(player.getUUID(), id -> new SidebarState())
				.setLastContent(newContent);
	}

	/** Removes the objective (and therefore the sidebar) from one client. */
	public static void removeSidebar(ServerPlayer player) {
		player.connection.send(new ClientboundSetObjectivePacket(
				buildObjective(Component.empty()),
				ClientboundSetObjectivePacket.METHOD_REMOVE
		));
		STATES.remove(player.getUUID());
	}

	private static void sendLine(ServerPlayer player, int index, Component line) {
		player.connection.send(new ClientboundSetScorePacket(
				LINE_HOLDER_PREFIX + index,
				OBJECTIVE_NAME,
				SCORE_BASE - index,
				Optional.of(line),
				Optional.empty()
		));
	}

	/**
	 * Returns the SidebarContent most recently rendered for a player, or
	 * null if none has been shown yet. Lets the caller decide between
	 * createSidebar (null) and updateSidebar (non-null) before touching
	 * any packets.
	 */
	public static SidebarContent getLastContent(ServerPlayer player) {
		SidebarState state = STATES.get(player.getUUID());
		return state != null ? state.lastContent() : null;
	}
}
