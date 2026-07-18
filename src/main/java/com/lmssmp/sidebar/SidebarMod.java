package com.lmssmp.sidebar;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Milestone 9: the sidebar no longer only updates on join -- a server
 * tick callback now rebuilds every online player's sidebar roughly once
 * a second, using the exact same pipeline the join hook already used
 * (SidebarContentBuilder -> SidebarContent -> SidebarManager). Nothing
 * is registered on the shared world scoreboard by this mod, so no other
 * client ever receives these packets.
 *
 * This is a full rebuild every refresh, not a diff -- correctness over
 * efficiency for now, per Milestone 9's scope. Milestone 10+ can replace
 * the body of the tick callback with something smarter without this
 * class's structure needing to change.
 */
public final class SidebarMod implements ModInitializer {

	/** Used as the fabric.mod.json id and the logger name prefix. */
	public static final String MOD_ID = "lmssmp-sidebar";

	private static final Logger LOGGER = LoggerFactory.getLogger("LMSSMP Sidebar");

	/** How often (in server ticks) every online player's sidebar rebuilds. 20 ticks ~= 1 second. */
	private static final int REFRESH_INTERVAL_TICKS = 20;

	private int ticksSinceLastRefresh = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("[LMSSMP Sidebar] Mod initialized successfully");

		// Build the sidebar as soon as a player's connection is fully set
		// up, so they don't wait up to a full REFRESH_INTERVAL_TICKS for
		// their first sidebar after joining. Touches only that one
		// connection.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			SidebarManager.showSidebar(player, SidebarContentBuilder.buildSidebarContent(player));
		});

		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	private void onServerTick(MinecraftServer server) {
		ticksSinceLastRefresh++;
		if (ticksSinceLastRefresh < REFRESH_INTERVAL_TICKS) {
			return;
		}
		ticksSinceLastRefresh = 0;

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			SidebarManager.showSidebar(player, SidebarContentBuilder.buildSidebarContent(player));
		}
	}
}
