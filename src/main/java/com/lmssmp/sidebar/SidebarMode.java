package com.lmssmp.sidebar;

/**
 * Which sidebar layout a player should see. As of Milestone 14 this is
 * driven by a real datapack value via ScoreboardSidebarModeProvider,
 * but the id mapping was defined back in Milestone 13 specifically so
 * that later step (reading a raw mode value off a scoreboard objective)
 * would have a single, safe place to convert it.
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
