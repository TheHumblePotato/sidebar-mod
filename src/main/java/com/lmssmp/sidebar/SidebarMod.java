package com.lmssmp.sidebar;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Milestone 3: minimal proof of per-player sidebars.
 *
 * On join, each player's connection is sent its own set of scoreboard
 * packets via {@link SidebarManager}. Nothing is registered on the shared
 * world scoreboard, so no other client ever receives these packets.
 * Later milestones will delegate real content (scores, teams, control
 * points) to SidebarManager instead of growing this class.
 */
public final class SidebarMod implements ModInitializer {

	/** Used as the fabric.mod.json id and the logger name prefix. */
	public static final String MOD_ID = "lmssmp-sidebar";

	private static final Logger LOGGER = LoggerFactory.getLogger("LMSSMP Sidebar");

	@Override
	public void onInitialize() {
		LOGGER.info("[LMSSMP Sidebar] Mod initialized successfully");

		// Milestone 3: send the static test sidebar as soon as a player's
		// connection is fully set up. Touches only that one connection.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				SidebarManager.showTestSidebar(handler.getPlayer()));
	}
}
