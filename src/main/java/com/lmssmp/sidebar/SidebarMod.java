package com.lmssmp.sidebar;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Milestone 5: sidebar content now comes from a real (datapack-owned)
 * scoreboard objective via SidebarContentBuilder, instead of a
 * hard-coded test string. Nothing is registered on the shared world
 * scoreboard by this mod, so no other client ever receives these
 * packets. Later milestones will extend SidebarContentBuilder with
 * teams and control points; this class shouldn't need to change again
 * until content needs to update on more than just join (Milestone 8+).
 */
public final class SidebarMod implements ModInitializer {

	/** Used as the fabric.mod.json id and the logger name prefix. */
	public static final String MOD_ID = "lmssmp-sidebar";

	private static final Logger LOGGER = LoggerFactory.getLogger("LMSSMP Sidebar");

	@Override
	public void onInitialize() {
		LOGGER.info("[LMSSMP Sidebar] Mod initialized successfully");

		// Milestone 5: read real scoreboard content and render it as soon as
		// a player's connection is fully set up. Touches only that one
		// connection.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			SidebarManager.showSidebar(player, SidebarContentBuilder.buildSidebarContent(player));
		});
	}
}
