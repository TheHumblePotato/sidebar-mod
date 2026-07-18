package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

/**
 * Milestone 14's first real SidebarModeProvider. Reads a per-player
 * value off a configurable scoreboard objective (MODE_OBJECTIVE_NAME)
 * so the datapack can control each player's mode independently, e.g.
 * `/scoreboard players set <player> sidebar_mode 2` for LEADERBOARD.
 *
 * Missing objective, missing player entry, or an out-of-range value all
 * safely fall back to EVENTS -- SidebarMode.fromId already defaults
 * unknown ints to EVENTS, and this class does the same when there's no
 * entry to read at all. This class only reads scoreboard state,
 * identically in spirit to SidebarContentBuilder's read-only pattern --
 * it never creates the objective or mutates any score.
 */
public final class ScoreboardSidebarModeProvider implements SidebarModeProvider {

	/** Datapack-owned objective; confirm this name matches the datapack. */
	public static final String MODE_OBJECTIVE_NAME = "sidebar_mode";

	@Override
	public SidebarMode getMode(ServerPlayer player) {
		Scoreboard scoreboard = ((ServerLevel) player.level()).getServer().getScoreboard();

		Objective objective = scoreboard.getObjective(MODE_OBJECTIVE_NAME);
		if (objective == null) {
			return SidebarMode.EVENTS;
		}

		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
		if (info == null) {
			return SidebarMode.EVENTS;
		}

		return SidebarMode.fromId(info.value());
	}
}
