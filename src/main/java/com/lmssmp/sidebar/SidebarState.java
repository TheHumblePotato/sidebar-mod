package com.lmssmp.sidebar;

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

	private List<String> lines = List.of();

	List<String> lines() {
		return lines;
	}

	void setLines(List<String> lines) {
		this.lines = lines;
	}

	boolean isActive() {
		return !lines.isEmpty();
	}
}
