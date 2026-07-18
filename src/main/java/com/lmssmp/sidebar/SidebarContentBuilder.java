package com.lmssmp.sidebar;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.List;

/**
 * Reads existing Minecraft scoreboard state -- owned and populated by the
 * datapack -- and turns it into a SidebarContent. This class only reads
 * data: it never creates an objective, never assigns a team, and never
 * touches packet-sending code (that stays in SidebarManager).
 *
 * This is the only class allowed to call MinecraftServer#getScoreboard(),
 * and it only ever calls read-only methods on the result (getObjective,
 * getPlayerScoreInfo) -- never addObjective, setDisplayObjective, or
 * anything else that would create or mutate scoreboard state. That's a
 * different Scoreboard instance entirely from SidebarManager's private
 * SCRATCH_SCOREBOARD, which is never shared with the server and never
 * read from.
 */
public final class SidebarContentBuilder {

	/** Datapack-owned objective this milestone reads from. */
	private static final String SCORE_OBJECTIVE_NAME = "score";

	private static final Component TITLE = Component.literal("LMSSMP");
	private static final Component NO_TEAM_LABEL = Component.literal("None");

	private SidebarContentBuilder() {
	}

	/**
	 * Milestone 7: the full sidebar layout as real Components. Score and
	 * team are real data (team formatting preserved); capture points are
	 * still a placeholder pending Milestone 8.
	 */
	public static SidebarContent buildSidebarContent(ServerPlayer player) {
		List<Component> lines = List.of(
				Component.literal("Score: " + readScore(player, SCORE_OBJECTIVE_NAME)),
				Component.empty(),
				Component.literal("Your team: ").append(readTeamDisplayName(player)),
				Component.empty(),
				Component.literal("Capture Points:"),
				Component.literal("(Not implemented)")
		);

		return new SidebarContent(TITLE, lines);
	}

	/** Reads a player's value for an existing objective, or 0 if either is absent. */
	private static int readScore(ServerPlayer player, String objectiveName) {
		Scoreboard scoreboard = ((ServerLevel) player.level()).getServer().getScoreboard();

		Objective objective = scoreboard.getObjective(objectiveName);
		if (objective == null) {
			return 0;
		}

		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(player, objective);
		if (info == null) {
			return 0;
		}

		return info.value();
	}

	/**
	 * This version's Team has no separate "display name" -- getFormattedName()
	 * is the closest equivalent: it wraps the given Component with the
	 * team's own prefix/suffix/color, all read straight from Minecraft's
	 * Team object rather than assigned by this mod. Entity#getTeam() is
	 * read-only: it reports whichever team (if any) the player is already
	 * on via /team, it never creates or joins one.
	 */
	private static Component readTeamDisplayName(ServerPlayer player) {
		Team team = player.getTeam();
		if (team == null) {
			return NO_TEAM_LABEL;
		}
		return team.getFormattedName(Component.literal(team.getName()));
	}
}