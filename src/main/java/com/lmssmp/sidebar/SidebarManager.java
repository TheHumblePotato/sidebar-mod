package com.lmssmp.sidebar;

import net.minecraft.network.chat.Component;
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
 * Renders a SidebarContent (title + ordered Component lines) rather than
 * raw text, so any formatting SidebarContentBuilder attaches to a line
 * (team colors, etc.) reaches the client unchanged. The title is no
 * longer hardcoded here -- it comes from SidebarContent, so a different
 * caller could show a differently-titled sidebar without touching this
 * class.
 *
 * Each line is shown using a stable, internal fake score holder
 * ("line_0", "line_1", ...) derived purely from the line's position in
 * the list, never from its text. That's what keeps two blank lines (or
 * any other duplicate line) from colliding: Minecraft requires every
 * score holder in an objective to be unique, but "line_2" and "line_4"
 * are unique regardless of whether their displayed Components happen to
 * both be empty. It also keeps a line's identity stable even if its text
 * changes on a future update -- which matters once Milestone 9 adds
 * diffing, since diffing needs a stable key to compare against, not the
 * text itself.
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
	 * Shows (or replaces) a player's sidebar with the given content.
	 * Rebuilds the objective from scratch every call -- simple and always
	 * correct, at the cost of a small amount of extra traffic. Milestone 9
	 * will replace this with diff-based updates that only re-send lines
	 * that actually changed.
	 */
	public static void showSidebar(ServerPlayer player, SidebarContent content) {
		removeObjective(player);

		player.connection.send(new ClientboundSetObjectivePacket(
				buildObjective(content.title()),
				ClientboundSetObjectivePacket.METHOD_ADD
		));

		player.connection.send(new ClientboundSetDisplayObjectivePacket(
				DisplaySlot.SIDEBAR,
				buildObjective(content.title())
		));

		List<Component> lines = content.lines();
		for (int i = 0; i < lines.size(); i++) {
			String holder = LINE_HOLDER_PREFIX + i;
			// Higher score sorts higher in the vanilla sidebar, so the
			// first line in the list needs the highest score.
			int score = lines.size() - i;

			player.connection.send(new ClientboundSetScorePacket(
					holder,
					OBJECTIVE_NAME,
					score,
					Optional.of(lines.get(i)),
					Optional.empty()
			));
		}

		STATES.computeIfAbsent(player.getUUID(), id -> new SidebarState())
				.setLines(lines);
	}

	/** Removes the objective (and therefore the sidebar) from one client. */
	public static void clearSidebar(ServerPlayer player) {
		removeObjective(player);
		STATES.remove(player.getUUID());
	}

	private static void removeObjective(ServerPlayer player) {
		player.connection.send(new ClientboundSetObjectivePacket(
				buildObjective(Component.empty()),
				ClientboundSetObjectivePacket.METHOD_REMOVE
		));
	}
}
