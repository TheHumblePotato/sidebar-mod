package com.lmssmp.sidebar;

import net.minecraft.ChatFormatting;
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
 * Milestone 15: Events mode rewritten to match the exact spec layout --
 * a shared "Team: <name>" line (not "Your Team:"), and a
 * "Current Events:" section with three independently-gated
 * sub-sections (Capture Points / Random Event / Global Event), each
 * driven off "#Game"-holder scores. Capture point data now comes from
 * RealCapturePointProvider (real tagged armor stands) instead of the
 * Milestone 8-12 placeholder. Random/Global event names come from the
 * datapack's command storage; their durations are printed as raw score
 * values (no tick->time conversion -- only capture_point_time is
 * specified as needing min:s formatting).
 */
public final class SidebarContentBuilder {
	private static final ChatFormatting SECTION_COLOR = ChatFormatting.AQUA;
	/** Datapack-owned objective this milestone reads from. */
	private static final String SCORE_OBJECTIVE_NAME = "score";

	/** Fake scoreboard holder the datapack uses for global/game-wide state. */
	private static final String GAME_HOLDER = "#Game";

	private static final String CAPTURE_POINT_EVENT_OBJ = "capture_point_event";
	private static final String RANDOM_EVENT_ACTIVE_OBJ = "random_event_active";
	private static final String RANDOM_EVENT_DURATION_OBJ = "random_event_duration";
	private static final String GLOBAL_EVENT_ACTIVE_OBJ = "global_event_active";
	private static final String GLOBAL_EVENT_TIME_LIMIT_OBJ = "global_event_time_limit";
	private static final String GLOBAL_EVENT_DURATION_OBJ = "global_event_duration";

	private static final String RANDOM_EVENT_NAME_KEY = "random_event_name";
	private static final String GLOBAL_EVENT_NAME_KEY = "global_event_name";

	private static final Component TITLE =
        Component.literal("LMSSMP")
        .withStyle(ChatFormatting.BOLD, ChatFormatting.GOLD);
	private static final Component NO_TEAM_LABEL = Component.literal("None");

	/**
	 * Swappable so a later milestone can supply a different capture point
	 * source without this class otherwise changing -- see
	 * setCapturePointProvider. Default is now the real armor-stand-backed
	 * provider, not a placeholder.
	 */
	private static CapturePointProvider capturePointProvider = new RealCapturePointProvider();

	/**
	 * Swappable so a later milestone can supply a real per-player mode
	 * (e.g. read from datapack/config state) without this class's
	 * mode-dispatch logic changing -- see setSidebarModeProvider.
	 */
	private static SidebarModeProvider sidebarModeProvider = new PlaceholderSidebarModeProvider();

	private SidebarContentBuilder() {
	}

	/**
	 * Lets a later milestone register a different CapturePointProvider in
	 * place of the current default, without any other method here
	 * needing to change.
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

	/**
	 * Events layout, matching the spec exactly:
	 *
	 *   Score: ####
	 *   Team: ####
	 *   (blank)
	 *   Current Events:
	 *   Capture Points          <- only if #Game capture_point_event == 1
	 *   #1 ***
	 *   ...
	 *   Random Event            <- only if #Game random_event_active == 1
	 *   Current Event: ___
	 *   Duration: ___
	 *   Global Event            <- only if #Game global_event_active == 1
	 *   Event: ___
	 *   Duration: ___           <- only if #Game global_event_time_limit == 1
	 *
	 * "None" is shown if none of the three sub-sections are active --
	 * that fallback isn't in the spec explicitly, it's a reasonable
	 * default so the section never renders as a bare, empty header.
	 */
	private static SidebarContent buildEvents(ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.literal("Score: " + readScore(player, SCORE_OBJECTIVE_NAME)));
		lines.add(teamLine(player));
		lines.add(Component.empty());

		boolean anySection = false;

		if (readNamedScore(player, CAPTURE_POINT_EVENT_OBJ, GAME_HOLDER) == 1) {
			anySection = true;
			lines.add(
				Component.literal("Capture Points")
					.withStyle(ChatFormatting.BOLD, SECTION_COLOR)
			);
			lines.addAll(capturePointStatusLines(player));
		}

		if (readNamedScore(player, RANDOM_EVENT_ACTIVE_OBJ, GAME_HOLDER) == 1) {
			anySection = true;
			lines.add(
				Component.literal("Random Event")
					.withStyle(ChatFormatting.BOLD, SECTION_COLOR)
			);
			lines.add(Component.literal("Current Event: " + GameStorageReader.readString(player, RANDOM_EVENT_NAME_KEY)));
			lines.add(Component.literal(
				"Duration: " + formatTime(
					readNamedScore(player, RANDOM_EVENT_DURATION_OBJ, GAME_HOLDER)
				)
			));
		}

		if (readNamedScore(player, GLOBAL_EVENT_ACTIVE_OBJ, GAME_HOLDER) == 1) {
			anySection = true;
			lines.add(
				Component.literal("Global Event")
					.withStyle(ChatFormatting.BOLD, SECTION_COLOR)
			);
			lines.add(Component.literal("Event: " + GameStorageReader.readString(player, GLOBAL_EVENT_NAME_KEY)));
			if (readNamedScore(player, GLOBAL_EVENT_TIME_LIMIT_OBJ, GAME_HOLDER) == 1) {
				lines.add(Component.literal(
					"Duration: " + formatTime(
						readNamedScore(player, GLOBAL_EVENT_DURATION_OBJ, GAME_HOLDER)
					)
				));
			}
		}

		if (!anySection) {
			lines.add(Component.literal("Current Events: None"));
		}

		return new SidebarContent(TITLE, List.copyOf(lines));
	}

	/**
	 * Leaderboard layout: Score, Team, blank, "Leaderboard:", then one
	 * fixed Red/Yellow/Green/Blue line each, reading team totals off the
	 * "#team1".."#team4" fake-player holders (per your correction --
	 * these are fake players named "#team1" etc, not real Team objects
	 * named "team1"). No team 5 row, since team 5 is for individuals.
	 */
	private static SidebarContent buildLeaderboard(ServerPlayer player) {
		List<Component> lines = List.of(
				Component.literal("Score: " + readScore(player, SCORE_OBJECTIVE_NAME)),
				teamLine(player),
				Component.empty(),
				Component.literal("Leaderboard:") .withStyle(ChatFormatting.BOLD, SECTION_COLOR),
				Component.literal("Red: " + readNamedScore(player, SCORE_OBJECTIVE_NAME, "#team1")),
				Component.literal("Yellow: " + readNamedScore(player, SCORE_OBJECTIVE_NAME, "#team2")),
				Component.literal("Green: " + readNamedScore(player, SCORE_OBJECTIVE_NAME, "#team3")),
				Component.literal("Blue: " + readNamedScore(player, SCORE_OBJECTIVE_NAME, "#team4"))
		);

		return new SidebarContent(TITLE, lines);
	}

	/** Mini layout: just Score and Team. */
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
	 * Reads a plain-string scoreboard holder's value (e.g. a fake-player
	 * entry such as "#team1" or "#Game") for an existing objective, or 0
	 * if either is absent. Same read-only semantics as readScore, but for
	 * a holder name that isn't a real online player.
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
	 * "Team: <name>" line, shared by every visible layout so the
	 * read/format logic only lives in one place. Matches the spec's
	 * literal "Team: ####" wording (not "Your Team:").
	 */
	private static Component teamLine(ServerPlayer player) {
		return Component.literal("Team: ").append(readTeamDisplayName(player));
	}

	/**
	 * Entity#getTeam() actually returns PlayerTeam, not the abstract Team
	 * superclass -- getFormattedDisplayName() is only declared on
	 * PlayerTeam. It returns the team's display name (set via
	 * /team add ... "<name>" or /team modify ... displayName) with the
	 * team's color/formatting already applied by Minecraft -- nothing
	 * styled by this mod. getTeam() itself is read-only: it reports
	 * whichever team (if any) the player is already on via /team, it
	 * never creates or joins one. This is how "team1".."team4" (the real
	 * team names) end up displaying as "Red"/"Yellow"/etc -- that mapping
	 * lives entirely in the datapack's /team modify ... displayName
	 * commands, not in this mod.
	 */
	private static Component readTeamDisplayName(ServerPlayer player) {
		PlayerTeam team = player.getTeam();
		if (team == null) {
			return NO_TEAM_LABEL;
		}
		return team.getFormattedDisplayName();
	}

	private static ChatFormatting teamColor(int teamId) {
		return switch (teamId) {
			case 1 -> ChatFormatting.RED;
			case 2 -> ChatFormatting.YELLOW;
			case 3 -> ChatFormatting.GREEN;
			case 4 -> ChatFormatting.BLUE;
			case 5 -> ChatFormatting.GRAY;
			default -> ChatFormatting.WHITE;
		};
	}

	/**
	 * One line per enabled capture point, in ascending order (order
	 * comes directly from each entity's own "capture_point" score).
	 * Disabled points (capture_point_enabled == 0) are omitted entirely,
	 * per spec -- they don't get a line at all, not even a placeholder.
	 */
	private static List<Component> capturePointStatusLines(ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		for (CapturePointEntry point : capturePointProvider.getCapturePoints(player)) {
			if (!point.enabled()) {
				continue;
			}
			lines.add(Component.literal("#" + point.order() + " ").append(capturePointStatus(point)));
		}
		return lines;
	}

	/**
	 * Renders one capture point's status symbol + label:
	 *   - capturingState != 0 -> "⚔ <capturingTeam> capturing (m:ss)"
	 *     (covers both "capturing for the first time" and "recapturing" --
	 *     the spec doesn't distinguish their display, both just show ⚔
	 *     plus the capturing team and remaining time)
	 *   - team == 0            -> "⬜ Uncaptured"
	 *   - otherwise             -> "❎ <team>" (colored per team)
	 */
	private static Component capturePointStatus(CapturePointEntry point) {
		if (point.capturingState() != 0) {
			Component previousOwner;

			if (point.team() == 0) {
				previousOwner = Component.literal("⬜");
			} else {
				previousOwner = Component.literal("❎")
						.withStyle(teamColor(point.team()));
			}

			Component capturing = Component.literal(" ⚔")
					.withStyle(teamColor(point.capturingTeam()));

			return Component.empty()
					.append(previousOwner)
					.append(capturing)
					.append(Component.literal(" (" + formatTime(point.timeTicks()) + ")"));
		}
		if (point.team() == 0) {
			return Component.literal("⬜");
		}
		return Component.literal("❎")
        .withStyle(teamColor(point.team()));
	}

	/**
	 * Ticks -> "m:ss". Per spec, this formatting is only specified for
	 * capture_point_time -- random/global event durations are printed as
	 * raw score values instead (see buildEvents), since the spec never
	 * said those were ticks needing conversion.
	 */
	private static String formatTime(int ticks) {
		int totalSeconds = Math.max(0, ticks) / 20;
		int minutes = totalSeconds / 60;
		int seconds = totalSeconds % 60;
		return minutes + ":" + (seconds < 10 ? "0" + seconds : seconds);
	}
}
