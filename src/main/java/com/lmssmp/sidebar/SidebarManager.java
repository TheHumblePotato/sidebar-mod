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

import java.util.Optional;

/**
 * Milestone 3: builds and sends the three vanilla scoreboard packets
 * directly to one player's connection, bypassing the shared world
 * scoreboard entirely (never touches server.getScoreboard()).
 *
 * As of 26.2, ClientboundSetObjectivePacket and
 * ClientboundSetDisplayObjectivePacket both take a real Objective
 * instance and read their fields off it, rather than taking raw
 * name/title/renderType arguments directly. SCRATCH_SCOREBOARD below is
 * a throwaway Scoreboard that exists only to satisfy Objective's
 * constructor -- it is never registered with the server, never ticked,
 * and never used for anything other than building these packets, so
 * this is not the same thing as server.getScoreboard().
 *
 * No player data, teams, or control points yet -- this only proves that
 * per-player text can be shown independently to each client. Later
 * milestones will replace showTestSidebar() with real content and add
 * per-player caching so we only send packets when a line actually changes.
 */
public final class SidebarManager {

	private static final String OBJECTIVE_NAME = "lmssmp_sidebar";

	/** Never registered anywhere -- purely a data holder for Objective's constructor. */
	private static final Scoreboard SCRATCH_SCOREBOARD = new Scoreboard();

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

	/** Milestone 3 test sidebar: title "LMSSMP", one line "Hello <name>". */
	public static void showTestSidebar(ServerPlayer player) {
		Objective objective = buildObjective(Component.literal("LMSSMP"));
		String lineText = "Hello " + player.getGameProfile().name();

		// 1. Create the objective and give it a title.
		player.connection.send(new ClientboundSetObjectivePacket(
				objective,
				ClientboundSetObjectivePacket.METHOD_ADD
		));

		// 2. Put that objective in the sidebar slot on this client only.
		player.connection.send(new ClientboundSetDisplayObjectivePacket(
				DisplaySlot.SIDEBAR,
				objective
		));

		// 3. Add one line. The "score holder" name is the text shown;
		// the number next to it is unused for now.
		player.connection.send(new ClientboundSetScorePacket(
				lineText,
				OBJECTIVE_NAME,
				0,
				Optional.empty(),
				Optional.empty()
		));
	}

	/** Removes the objective (and therefore the sidebar) from one client. */
	public static void clearSidebar(ServerPlayer player) {
		Objective objective = buildObjective(Component.empty());

		player.connection.send(new ClientboundSetObjectivePacket(
				objective,
				ClientboundSetObjectivePacket.METHOD_REMOVE
		));
	}
}
