package com.lmssmp.sidebar;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Milestone 13: SidebarContentBuilder now returns Optional<SidebarContent>
 * -- empty when the player's current SidebarMode is HIDDEN. This class is
 * where that Optional gets resolved into an actual SidebarManager call:
 * present -> create/update as before; empty -> removeSidebar() if the
 * player currently has a sidebar showing, otherwise nothing at all (an
 * already-hidden player has nothing to remove). SidebarManager itself
 * still has no idea modes exist -- it only ever receives a concrete
 * SidebarContent or a removeSidebar() call, exactly as before.
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
		// it, so a present Optional always creates rather than updates;
		// an empty one (HIDDEN) simply means nothing gets sent at all.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.getPlayer();
			SidebarContentBuilder.buildSidebarContent(player)
					.ifPresent(content -> SidebarManager.createSidebar(player, content));
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
			Optional<SidebarContent> contentOpt = SidebarContentBuilder.buildSidebarContent(player);
			SidebarContent previous = SidebarManager.getLastContent(player);
			String name = player.getGameProfile().name();

			// TEMP debug logging -- safe to delete once this is trusted,
			// kept to one line per player so it's easy to grep and remove.
			if (contentOpt.isEmpty()) {
				if (previous != null) {
					SidebarManager.removeSidebar(player);
					LOGGER.info("[Sidebar] Hidden {}", name);
				} else {
					LOGGER.info("[Sidebar] No changes for {}", name);
				}
				continue;
			}

			SidebarContent content = contentOpt.get();

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
