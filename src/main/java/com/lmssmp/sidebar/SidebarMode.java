package com.lmssmp.sidebar;

/**
 * Which sidebar layout a player should see. Not yet driven by any real
 * datapack/config value -- see PlaceholderSidebarModeProvider -- but the
 * id mapping is defined now so a later milestone that reads a raw mode
 * value (e.g. from a scoreboard objective) has a single, safe place to
 * convert it.
 */
public enum SidebarMode {
	HIDDEN,
	EVENTS,
	LEADERBOARD,
	MINI;

	/**
	 * Maps a raw mode id to a SidebarMode. Unknown values safely default
	 * to EVENTS rather than throwing, since a bad/unset id (e.g. before
	 * the datapack has written one yet) should fall back to the normal
	 * sidebar, not hide it or crash.
	 */
	public static SidebarMode fromId(int id) {
		return switch (id) {
			case 0 -> HIDDEN;
			case 1 -> EVENTS;
			case 2 -> LEADERBOARD;
			case 3 -> MINI;
			default -> EVENTS;
		};
	}
}
