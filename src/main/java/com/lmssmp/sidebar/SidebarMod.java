package com.lmssmp.sidebar;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Milestone 2: initialization check.
 *
 * Still no sidebar, player, or control point logic -- this only confirms
 * to a server operator (via console log) that Fabric loaded the mod
 * successfully. Later milestones will delegate real work to
 * {@code sidebar.SidebarManager} instead of growing this class.
 */
public final class SidebarMod implements ModInitializer {

	/** Used as the fabric.mod.json id and the logger name prefix. */
	public static final String MOD_ID = "lmssmp-sidebar";

	private static final Logger LOGGER = LoggerFactory.getLogger("LMSSMP Sidebar");

	@Override
	public void onInitialize() {
		// Milestone 2 only confirms the mod loads. Real initialization
		// (SidebarManager, ControlPointManager, ConfigManager, etc.)
		// is added in later milestones, one at a time.
		LOGGER.info("[LMSSMP Sidebar] Mod initialized successfully");
	}
}
