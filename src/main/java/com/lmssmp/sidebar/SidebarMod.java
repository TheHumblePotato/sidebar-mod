package com.lmssmp.sidebar;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Milestone 10: the tick loop now builds fresh content every refresh but
 * only calls SidebarManager (and only sends packets) when that content
 * actually differs from what was last rendered for that player --
 * SidebarContent's value-based equals() (a Java record) makes that a
 * plain .equals() call, no manual field comparisons. SidebarManager
 * itself is unchanged: it still rebuilds unconditionally whenever asked,
 * the decision to ask lives entirely here.
 *
 * This is a full rebuild every time content *does* change, not a diff --
 * correctness over efficiency for now, per Milestone 10's scope.
 * Milestone 11 can replace showSidebar's body with line-level diffing
 * without this class needing to change.
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
			SidebarContent content = SidebarContentBuilder.buildSidebarContent(player);
			SidebarContent previous = SidebarManager.getLastContent(player);
			String name = player.getGameProfile().name();

			// TEMP debug logging for Milestone 10 -- safe to delete once
			// change detection is trusted; kept to one line per player so
			// it's easy to grep and easy to remove.
			if (content.equals(previous)) {
				LOGGER.info("[Sidebar] No changes for {}", name);
				continue;
			}

			SidebarManager.showSidebar(player, content);
			LOGGER.info("[Sidebar] Updated {}", name);
		}
	}
}
