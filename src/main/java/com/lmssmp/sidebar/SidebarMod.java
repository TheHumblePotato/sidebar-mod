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
 * Milestone 13: SidebarContentBuilder returns Optional<SidebarContent>
 * -- empty when the player's current SidebarMode is HIDDEN. This class
 * resolves that Optional into an actual SidebarManager call: present ->
 * create/update as before; empty -> removeSidebar() if the player
 * currently has a sidebar showing, otherwise nothing at all.
 * SidebarManager itself still has no idea modes exist -- it only ever
 * receives a concrete SidebarContent or a removeSidebar() call.
 *
 * Milestone 14:
 * - REFRESH_INTERVAL_TICKS dropped from 20 to 4 (~5x/sec) for snappier
 *   updates. This is safe because updateSidebar/getLastContent already
 *   skip sending anything when nothing changed -- polling more often
 *   means faster detection of real changes, not more packets.
 * - Wires in ScoreboardSidebarModeProvider (real datapack-driven mode)
 *   in place of PlaceholderSidebarModeProvider.
 *
 * Milestone 15:
 * - Wires in RealCapturePointProvider (real tagged armor stands) in
 *   place of the Milestone 8-12 placeholder.
 * - Removed the per-tick TEMP debug logging from the tick loop -- it
 *   was only ever meant to be temporary and is no longer needed now
 *   that the mode/content pipeline is trusted.
 *
 * Firebase sync (post-15):
 * - A second, independent tick counter (ticksSinceLastFirebaseSync)
 *   drives FirebaseSync.sync(server) every FIREBASE_SYNC_INTERVAL_TICKS
 *   (~40s by default). Deliberately separate from the sidebar refresh
 *   counter -- the two run on completely different cadences (5x/sec vs
 *   ~once every 40s) and have nothing to do with each other. This does
 *   not touch anything player-connection-related; it's a read-only
 *   scoreboard/game-state snapshot pushed out over HTTP.
 */
public final class SidebarMod implements ModInitializer {

	/** Used as the fabric.mod.json id and the logger name prefix. */
	public static final String MOD_ID = "lmssmp-sidebar";

	private static final Logger LOGGER = LoggerFactory.getLogger("LMSSMP Sidebar");

	/** How often (in server ticks) every online player's sidebar is checked for changes. 4 ticks ~= 5x/sec. */
	private static final int REFRESH_INTERVAL_TICKS = 20;

	/** How often (in server ticks) global data is pushed to Firebase. 800 ticks ~= 40 seconds. */
	private static final int FIREBASE_SYNC_INTERVAL_TICKS = 800;

	private int ticksSinceLastRefresh = 0;
	private int ticksSinceLastFirebaseSync = 0;

	@Override
	public void onInitialize() {
		LOGGER.info("[LMSSMP Sidebar] Mod initialized successfully");

		SidebarContentBuilder.setSidebarModeProvider(new ScoreboardSidebarModeProvider());
		SidebarContentBuilder.setCapturePointProvider(new RealCapturePointProvider());

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
		if (ticksSinceLastRefresh >= REFRESH_INTERVAL_TICKS) {
			ticksSinceLastRefresh = 0;
			refreshSidebars(server);
		}

		ticksSinceLastFirebaseSync++;
		if (ticksSinceLastFirebaseSync >= FIREBASE_SYNC_INTERVAL_TICKS) {
			ticksSinceLastFirebaseSync = 0;
			FirebaseSync.sync(server);
		}
	}

	private void refreshSidebars(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Optional<SidebarContent> contentOpt = SidebarContentBuilder.buildSidebarContent(player);
			SidebarContent previous = SidebarManager.getLastContent(player);

			if (contentOpt.isEmpty()) {
				if (previous != null) {
					SidebarManager.removeSidebar(player);
				}
				continue;
			}

			SidebarContent content = contentOpt.get();

			if (content.equals(previous)) {
				continue;
			}

			if (previous == null) {
				SidebarManager.createSidebar(player, content);
			} else {
				SidebarManager.updateSidebar(player, previous, content);
			}
		}
	}
}