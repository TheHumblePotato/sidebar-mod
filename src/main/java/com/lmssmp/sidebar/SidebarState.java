package com.lmssmp.sidebar;

/**
 * Tracks the most recently rendered SidebarContent for a single player.
 *
 * Milestone 10 uses this so SidebarMod's update loop can compare a
 * freshly-built SidebarContent against what was last actually sent, and
 * skip rebuilding (and re-sending packets) entirely when nothing
 * changed. SidebarContent is an immutable record, so it's stored
 * directly rather than copied field-by-field, and its generated
 * equals()/hashCode() (value-based, not identity-based) is what the
 * comparison relies on.
 */
final class SidebarState {

	private SidebarContent lastContent;

	SidebarContent lastContent() {
		return lastContent;
	}

	void setLastContent(SidebarContent content) {
		this.lastContent = content;
	}

	boolean isActive() {
		return lastContent != null;
	}
}
