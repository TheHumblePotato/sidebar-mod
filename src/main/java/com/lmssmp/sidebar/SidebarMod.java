package com.lmssmp.sidebar;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Milestone 4: still only shows the same test content as Milestone 3
 * ("Hello <player name>"), but now goes through the generic
 * SidebarManager#showSidebar(player, lines) API instead of a
 * single-purpose test method. Nothing is registered on the shared world
 * scoreboard, so no other client ever receives these packets. Later
 * milestones will replace the hard-coded line list here with real
 * content read from the datapack's scoreboard/team/armor-stand data.
 */
public final class SidebarMod implements ModInitializer {

	/** Used as the fabric.mod.json id and the logger name prefix. */
	public static final String MOD_ID = "lmssmp-sidebar";

	private static final Logger LOGGER = LoggerFactory.getLogger("LMSSMP Sidebar");

	@Override
	public void onInitialize() {
		LOGGER.info("[LMSSMP Sidebar] Mod initialized successfully");

		// Milestone 4: send the (still static) test sidebar as soon as a
		// player's connection is fully set up. Touches only that one
		// connection.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			SidebarManager.showSidebar(player, List.of(
				"Hello " + name,
				"Milestone 4",
				"Packet Sidebar"
			));
		});
	}
}
