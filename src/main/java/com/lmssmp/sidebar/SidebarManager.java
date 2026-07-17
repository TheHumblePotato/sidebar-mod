package com.lmssmp.sidebar;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

import java.util.Optional;

/**
 * Milestone 3: builds and sends the three vanilla scoreboard packets
 * directly to one player's connection, bypassing the shared world
 * scoreboard entirely (never touches server.getScoreboard()).
 *
 * No player data, teams, or control points yet -- this only proves that
 * per-player text can be shown independently to each client. Later
 * milestones will replace showTestSidebar() with real content and add
 * per-player caching so we only send packets when a line actually changes.
 */
public final class SidebarManager {

	private static final String OBJECTIVE_NAME = "lmssmp_sidebar";

	private SidebarManager() {
	}

	/** Milestone 3 test sidebar: title "LMSSMP", one line "Hello <name>". */
	public static void showTestSidebar(ServerPlayer player) {
		Component title = Component.literal("LMSSMP");
		String lineText = "Hello " + player.getGameProfile().getName();

		// 1. Create the objective and give it a title.
		player.connection.send(new ClientboundSetObjectivePacket(
				OBJECTIVE_NAME,
				ClientboundSetObjectivePacket.METHOD_ADD,
				title,
				ObjectiveCriteria.RenderType.INTEGER,
				Optional.empty()
		));

		// 2. Put that objective in the sidebar slot on this client only.
		player.connection.send(new ClientboundSetDisplayObjectivePacket(
				DisplaySlot.SIDEBAR,
				OBJECTIVE_NAME
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
		player.connection.send(new ClientboundSetObjectivePacket(
				OBJECTIVE_NAME,
				ClientboundSetObjectivePacket.METHOD_REMOVE,
				Component.empty(),
				ObjectiveCriteria.RenderType.INTEGER,
				Optional.empty()
		));
	}
}
