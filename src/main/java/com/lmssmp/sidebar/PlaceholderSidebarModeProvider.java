package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerPlayer;

/**
 * Milestone 13's default SidebarModeProvider: always EVENTS, which kept
 * behavior identical to Milestone 12. As of Milestone 14, SidebarMod
 * registers ScoreboardSidebarModeProvider instead of this class by
 * default -- this is kept around as a convenient fallback/testing
 * implementation (e.g. for local dev without a datapack installed).
 */
public final class PlaceholderSidebarModeProvider implements SidebarModeProvider {

	@Override
	public SidebarMode getMode(ServerPlayer player) {
		return SidebarMode.EVENTS;
	}
}
