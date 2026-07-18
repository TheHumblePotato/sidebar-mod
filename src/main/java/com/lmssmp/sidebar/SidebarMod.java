package com.lmssmp.sidebar;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Milestone 11: the tick loop now picks between SidebarManager's
 * createSidebar (no previous content recorded for this player -- treat
 * it like a fresh join) and updateSidebar (previous content exists and
 * differs from the fresh content just built), instead of always calling
 * one always-rebuild method. The join hook always calls createSidebar,
 * since a freshly connected client has no sidebar on it yet regardless
 * of anything SidebarManager remembers server-side.
 */
public final class SidebarMod implements ModInitializer {

	/** Used as the fabric.mod.json id and the logger name prefix. */
	public static final String MOD_ID = "lmssmp-sidebar";

	private static final Logger LOGGER = LoggerFactory.getLogger("LMSSMP Sidebar");

	/** How often (in server ticks) every online player's sidebar is checked for changes. 20 ticks ~= 1 second. */
	private static final int REFRESH_INTERVAL_TICKS = 20;

	private int ticksSinceLastRefresh = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("[LMSSMP Sidebar] Mod initialized successfully");

		// Build the sidebar as soon as a player's connection is fully set
		// up, so they don't wait up to a full REFRESH_INTERVAL_TICKS for
		// their first sidebar after joining. Touches only that one
		// connection. A fresh connection never has an existing sidebar on
		// it, so this always creates rather than updates.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			SidebarManager.createSidebar(player, SidebarContentBuilder.buildSidebarContent(player));
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

			// TEMP debug logging -- safe to delete once this is trusted,
			// kept to one line per player so it's easy to grep and remove.
			if (content.equals(previous)) {
				LOGGER.info("[Sidebar] No changes for {}", name);
				continue;
			}

			if (previous == null) {
				SidebarManager.createSidebar(player, content);
			} else {
				SidebarManager.updateSidebar(player, previous, content);
			}
			LOGGER.info("[Sidebar] Updated {}", name);
		}
	}
}
