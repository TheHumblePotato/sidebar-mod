package com.lmssmp.sidebar;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Tracks what a single player's sidebar currently looks like.
 *
 * Milestone 4 only uses this to know whether a player currently has an
 * active sidebar. Milestone 9 (diff-based caching) will compare the old
 * lines stored here against the new lines passed to showSidebar() so we
 * can send score packets only for lines that actually changed, instead
 * of rebuilding the whole sidebar on every call.
 */
final class SidebarState {

	private List<Component> lines = List.of();

	List<Component> lines() {
		return lines;
	}

	void setLines(List<Component> lines) {
		this.lines = lines;
	}

	boolean isActive() {
		return !lines.isEmpty();
	}
}
