package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerPlayer;

/**
 * Milestone 13's default SidebarModeProvider: always EVENTS, which keeps
 * existing behavior identical to Milestone 12. A later milestone can
 * register a different implementation (e.g. one backed by a real
 * datapack/config value) via
 * SidebarContentBuilder#setSidebarModeProvider without this class or
 * SidebarContentBuilder's mode-dispatch logic needing to change.
 */
public final class PlaceholderSidebarModeProvider implements SidebarModeProvider {

	@Override
	public SidebarMode getMode(ServerPlayer player) {
		return SidebarMode.HIDDEN;
	}
}
