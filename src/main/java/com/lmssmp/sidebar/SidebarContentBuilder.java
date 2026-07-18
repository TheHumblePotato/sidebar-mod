package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.List;

/**
 * Reads existing Minecraft scoreboard state -- owned and populated by the
 * datapack -- and turns it into sidebar text. This class only reads data:
 * it never creates an objective, never assigns a team, and never touches
 * packet-sending code (that stays in SidebarManager).
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

	/** Shown when a player has no vanilla scoreboard team. */
	private static final String NO_TEAM_LABEL = "None";

	private SidebarContentBuilder() {
	}

	/**
	 * Milestone 6: the full sidebar layout. Score is real data; team is
	 * real data if the player has one; capture points are still a
	 * placeholder pending Milestone 8.
	 */
	public static List<String> buildSidebarLines(ServerPlayer player) {
		return List.of(
				"Score: " + readScore(player, SCORE_OBJECTIVE_NAME),
				"",
				"Your team: " + readTeamName(player),
				"",
				"Capture Points:",
				"(Not implemented)"
		);
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
	 * Entity#getTeam() is a read-only accessor onto the same vanilla team
	 * assignment the datapack manages with /team commands -- it never
	 * creates or assigns a team, it only reports whichever one (if any)
	 * the player is already on.
	 */
	private static String readTeamName(ServerPlayer player) {
		Team team = player.getTeam();
		return team != null ? team.getName() : NO_TEAM_LABEL;
	}
}
