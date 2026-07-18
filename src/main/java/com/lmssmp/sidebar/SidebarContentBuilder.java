package com.lmssmp.sidebar;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
 *
 * Milestone 13: content generation is split by SidebarMode
 * (buildEvents/buildLeaderboard/buildMini), chosen per player via a
 * swappable SidebarModeProvider -- same injection style as
 * CapturePointProvider. buildSidebarContent returns Optional.empty()
 * for HIDDEN rather than an "empty" SidebarContent, so the caller can
 * tell "nothing to show" apart from "a sidebar with few/no lines" and
 * react accordingly.
 *
 * Milestone 14: every visible layout now shares one "Your Team: <name>"
 * line via teamLine(), instead of only EVENTS having it. buildLeaderboard
 * no longer returns hardcoded zeros -- it reads real per-team totals via
 * the new readNamedScore helper, which looks up a plain-string
 * scoreboard holder (e.g. "Red") the same way readScore looks up a
 * player. ASSUMPTION: team totals live on the same "score" objective,
 * keyed by team name as the holder (e.g.
 * `/scoreboard players add Red score 118`). If the datapack stores
 * these differently, only SCORE_OBJECTIVE_NAME / the four holder names
 * in buildLeaderboard need to change.
 */
public final class SidebarContentBuilder {

	/** Datapack-owned objective this milestone reads from. */
	private static final String SCORE_OBJECTIVE_NAME = "score";

	private static final Component TITLE = Component.literal("LMSSMP");
	private static final Component NO_TEAM_LABEL = Component.literal("None");

	/**
	 * Swappable so a later milestone can supply real capture point data
	 * (e.g. a provider that scans tagged armor stands) without this class
	 * otherwise changing -- see setCapturePointProvider.
	 */
	private static CapturePointProvider capturePointProvider = new PlaceholderCapturePointProvider();

	/**
	 * Swappable so a later milestone can supply a real per-player mode
	 * (e.g. read from datapack/config state) without this class's
	 * mode-dispatch logic changing -- see setSidebarModeProvider.
	 */
	private static SidebarModeProvider sidebarModeProvider = new PlaceholderSidebarModeProvider();

	private SidebarContentBuilder() {
	}

	/**
	 * Lets a later milestone register a real CapturePointProvider (e.g.
	 * one backed by tagged armor stands) in place of the placeholder
	 * default, without any other method here needing to change.
	 */
	public static void setCapturePointProvider(CapturePointProvider provider) {
		capturePointProvider = provider;
	}

	/**
	 * Lets a later milestone register a real SidebarModeProvider in place
	 * of the placeholder default, without buildSidebarContent's dispatch
	 * logic needing to change.
	 */
	public static void setSidebarModeProvider(SidebarModeProvider provider) {
		sidebarModeProvider = provider;
	}

	/**
	 * Builds the sidebar content for whichever mode the current
	 * SidebarModeProvider reports for this player. Returns
	 * Optional.empty() for HIDDEN -- there is no "empty sidebar" content
	 * to render in that case, only an instruction for the caller to make
	 * sure nothing is shown.
	 */
	public static Optional<SidebarContent> buildSidebarContent(ServerPlayer player) {
		SidebarMode mode = sidebarModeProvider.getMode(player);

		return switch (mode) {
			case HIDDEN -> Optional.empty();
			case EVENTS -> Optional.of(buildEvents(player));
			case LEADERBOARD -> Optional.of(buildLeaderboard(player));
			case MINI -> Optional.of(buildMini(player));
		};
	}

	/** The original (Milestone 8-12) layout: score, team, capture points. */
	private static SidebarContent buildEvents(ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("Score: " + readScore(player, SCORE_OBJECTIVE_NAME)));
		lines.add(Component.empty());
		lines.add(teamLine(player));
		lines.add(Component.empty());
		lines.add(Component.literal("Capture Points:"));
		lines.addAll(capturePointLines(capturePointProvider.getCapturePoints(player)));

		return new SidebarContent(TITLE, List.copyOf(lines));
	}

	/**
	 * Milestone 14: now reads real per-team totals via readNamedScore
	 * instead of the old Milestone 13 hardcoded zeros. Also gained the
	 * shared team line.
	 */
	private static SidebarContent buildLeaderboard(ServerPlayer player) {
		List<Component> lines = List.of(
				Component.literal("Score: " + readScore(player, SCORE_OBJECTIVE_NAME)),
				Component.empty(),
				teamLine(player),
				Component.empty(),
				Component.literal("Leaderboard:"),
				Component.literal("Red: " + readNamedScore(player, SCORE_OBJECTIVE_NAME, "team1")),
				Component.literal("Yellow: " + readNamedScore(player, SCORE_OBJECTIVE_NAME, "team2")),
				Component.literal("Green: " + readNamedScore(player, SCORE_OBJECTIVE_NAME, "team3")),
				Component.literal("Blue: " + readNamedScore(player, SCORE_OBJECTIVE_NAME, "team4"))
		);

		return new SidebarContent(TITLE, lines);
	}

	/** Milestone 14: mini mode now also shows the player's team. */
	private static SidebarContent buildMini(ServerPlayer player) {
		List<Component> lines = List.of(
				Component.literal("Score: " + readScore(player, SCORE_OBJECTIVE_NAME)),
				teamLine(player)
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
	 * Reads a plain-string scoreboard holder's value (e.g. a team's
	 * fake-player entry such as "Red") for an existing objective, or 0 if
	 * either is absent. Same read-only semantics as readScore, but for a
	 * holder name that isn't a real player -- this is what fixes the
	 * Milestone 13 leaderboard bug, where team totals were never read at
	 * all and instead hardcoded to 0.
	 */
	private static int readNamedScore(ServerPlayer player, String objectiveName, String holderName) {
		Scoreboard scoreboard = ((ServerLevel) player.level()).getServer().getScoreboard();

		Objective objective = scoreboard.getObjective(objectiveName);
		if (objective == null) {
			return 0;
		}

		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(holderName), objective);
		if (info == null) {
			return 0;
		}

		return info.value();
	}

	/**
	 * "Your Team: <name>" line, shared by every visible layout as of
	 * Milestone 14 so the read/format logic only lives in one place.
	 */
	private static Component teamLine(ServerPlayer player) {
		return Component.literal("Your Team: ").append(readTeamDisplayName(player));
	}

	/**
	 * Entity#getTeam() actually returns PlayerTeam, not the abstract Team
	 * superclass -- getDisplayName()/getFormattedDisplayName() are only
	 * declared on PlayerTeam, which is why typing this as Team hid them
	 * earlier. getFormattedDisplayName() returns the team's display name
	 * (set via /team add ... "<name>" or /team modify ... displayName)
	 * with the team's color/formatting already applied by Minecraft --
	 * nothing styled by this mod. getTeam() itself is read-only: it
	 * reports whichever team (if any) the player is already on via
	 * /team, it never creates or joins one.
	 */
	private static Component readTeamDisplayName(ServerPlayer player) {
		PlayerTeam team = player.getTeam();
		if (team == null) {
			return NO_TEAM_LABEL;
		}
		return team.getFormattedDisplayName();
	}

	/**
	 * Converts capture point entries into one sidebar line per entry, in
	 * order. owner/progress are ignored for now -- Milestone 12 only
	 * introduces the fields, a later milestone will render them.
	 */
	private static List<Component> capturePointLines(List<CapturePointEntry> capturePoints) {
		return capturePoints.stream()
				.map(entry -> (Component) Component.literal(entry.name()))
				.toList();
	}
}
