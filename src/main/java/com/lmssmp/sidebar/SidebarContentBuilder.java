package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;

import java.util.List;

/**
 * Reads existing Minecraft scoreboard state -- owned and populated by the
 * datapack -- and turns it into sidebar text. This class only reads data:
 * it never creates an objective, never writes a score, and never touches
 * packet-sending code (that stays in SidebarManager).
 *
 * This is the only class allowed to call
 * MinecraftServer#getScoreboard(), and it only ever calls read-only
 * methods on the result (getObjective, getPlayerScoreInfo) -- never
 * addObjective, setDisplayObjective, or anything else that would create
 * or mutate scoreboard state. That's a different Scoreboard instance
 * entirely from SidebarManager's private SCRATCH_SCOREBOARD, which is
 * never shared with the server and never read from.
 */
public final class SidebarContentBuilder {

	/** Datapack-owned objective this milestone reads from. */
	private static final String TEST_OBJECTIVE_NAME = "score";

	private SidebarContentBuilder() {
	}

	/** Milestone 5: a single line showing the player's "score" objective value. */
	public static List<String> buildSidebarLines(ServerPlayer player) {
		int score = readScore(player, TEST_OBJECTIVE_NAME);
		return List.of("Score: " + score);
	}

	/** Reads a player's value for an existing objective, or 0 if either is absent. */
	private static int readScore(ServerPlayer player, String objectiveName) {
		Scoreboard scoreboard = player.getServer().getScoreboard();

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
}
