package com.lmssmp.sidebar;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Periodically pushes GLOBAL (non-player-specific) sidebar data --
 * capture points, event state, and team leaderboard totals -- to a
 * Firebase Realtime Database via its REST API, so an external web app
 * or Discord bot can read the same live state the in-game sidebar
 * shows.
 *
 * This class is read-only with respect to Minecraft state, same as
 * every other reader in this mod: it never creates an objective, never
 * assigns a team, never mutates anything. It duplicates a handful of
 * objective-name constants from SidebarContentBuilder on purpose --
 * that keeps this class fully independent (no shared mutable state,
 * nothing to break if SidebarContentBuilder's internals change shape)
 * at the cost of needing to keep the two lists of names in sync if the
 * datapack's objective names ever change.
 *
 * Uses java.net.http.HttpClient (built into the JDK, no extra
 * dependency) instead of the Firebase Admin SDK -- RTDB's REST API is
 * just HTTP PUT of JSON to <databaseURL>/<path>.json, which is all this
 * needs. The PUT is sent with sendAsync, so a slow or unreachable
 * network never blocks the server thread; the tick loop that calls
 * sync() fires-and-forgets.
 *
 * SECURITY NOTE: the databaseURL itself isn't a secret, but your
 * Firebase Realtime Database *rules* control who can read/write it. If
 * your project is still on the default open "test mode" rules, this
 * works as-is. Before pointing a real web app/Discord bot at this data,
 * lock the rules down and add an auth token to the request -- that
 * needs a service-account credential you'd supply separately, not
 * something to hardcode here.
 */
public final class FirebaseSync {

	private static final Logger LOGGER = LoggerFactory.getLogger("LMSSMP Sidebar");

	/** From the Firebase project config. */
	private static final String DATABASE_URL = "https://testing-7d972-default-rtdb.firebaseio.com";
	private static final String SYNC_PATH = "/lmssmp/global.json";

	// Must match the equivalent constants in SidebarContentBuilder --
	// see the class comment above for why these are duplicated rather
	// than shared.
	private static final String SCORE_OBJECTIVE_NAME = "score";
	private static final String GAME_HOLDER = "#Game";
	private static final String CAPTURE_POINT_EVENT_OBJ = "capture_point_event";
	private static final String RANDOM_EVENT_ACTIVE_OBJ = "random_event_active";
	private static final String RANDOM_EVENT_DURATION_OBJ = "random_event_duration";
	private static final String GLOBAL_EVENT_ACTIVE_OBJ = "global_event_active";
	private static final String GLOBAL_EVENT_DURATION_OBJ = "global_event_duration";
	private static final String RANDOM_EVENT_NAME_KEY = "random_event_name";
	private static final String GLOBAL_EVENT_NAME_KEY = "global_event_name";

	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();

	private FirebaseSync() {
	}

	/**
	 * Reads current global state and PUTs it to Firebase. Safe to call
	 * even with zero players online (capture points/events just come
	 * back empty/false in that case) -- overworld() and the server
	 * scoreboard exist regardless of who's connected.
	 */
	public static void sync(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level == null) {
			return;
		}

		String json;
		try {
			json = buildJson(server, level);
		} catch (Exception e) {
			// Never let a bad read crash the tick loop -- log and skip
			// this cycle, try again next interval.
			LOGGER.warn("[LMSSMP Sidebar] Firebase sync: failed to build payload", e);
			return;
		}

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(DATABASE_URL + SYNC_PATH))
				.header("Content-Type", "application/json")
				.PUT(HttpRequest.BodyPublishers.ofString(json))
				.build();

		HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
				.whenComplete((response, error) -> {
					if (error != null) {
						LOGGER.warn("[LMSSMP Sidebar] Firebase sync failed: {}", error.toString());
					} else if (response.statusCode() >= 300) {
						LOGGER.warn("[LMSSMP Sidebar] Firebase sync returned HTTP {}", response.statusCode());
					}
				});
	}

	private static String buildJson(MinecraftServer server, ServerLevel level) {
		Scoreboard scoreboard = server.getScoreboard();

		int red = readNamedScore(scoreboard, SCORE_OBJECTIVE_NAME, "#team1");
		int yellow = readNamedScore(scoreboard, SCORE_OBJECTIVE_NAME, "#team2");
		int green = readNamedScore(scoreboard, SCORE_OBJECTIVE_NAME, "#team3");
		int blue = readNamedScore(scoreboard, SCORE_OBJECTIVE_NAME, "#team4");

		boolean capturePointsActive = readNamedScore(scoreboard, CAPTURE_POINT_EVENT_OBJ, GAME_HOLDER) == 1;

		boolean randomEventActive = readNamedScore(scoreboard, RANDOM_EVENT_ACTIVE_OBJ, GAME_HOLDER) == 1;
		int randomEventDuration = randomEventActive
				? readNamedScore(scoreboard, RANDOM_EVENT_DURATION_OBJ, GAME_HOLDER)
				: 0;

		boolean globalEventActive = readNamedScore(scoreboard, GLOBAL_EVENT_ACTIVE_OBJ, GAME_HOLDER) == 1;
		int globalEventDuration = globalEventActive
				? readNamedScore(scoreboard, GLOBAL_EVENT_DURATION_OBJ, GAME_HOLDER)
				: 0;

		ServerPlayer anyPlayer = firstOnlinePlayerOrNull(server);
		String randomEventName = (randomEventActive && anyPlayer != null)
				? GameStorageReader.readString(anyPlayer, RANDOM_EVENT_NAME_KEY)
				: "";
		String globalEventName = (globalEventActive && anyPlayer != null)
				? GameStorageReader.readString(anyPlayer, GLOBAL_EVENT_NAME_KEY)
				: "";

		List<CapturePointEntry> capturePoints = anyPlayer != null
				? new RealCapturePointProvider().getCapturePoints(anyPlayer)
				: List.of();

		StringBuilder cpJson = new StringBuilder("[");
		for (int i = 0; i < capturePoints.size(); i++) {
			CapturePointEntry point = capturePoints.get(i);
			if (i > 0) {
				cpJson.append(",");
			}
			cpJson.append("{")
					.append("\"order\":").append(point.order()).append(",")
					.append("\"enabled\":").append(point.enabled()).append(",")
					.append("\"team\":").append(point.team()).append(",")
					.append("\"capturingState\":").append(point.capturingState()).append(",")
					.append("\"capturingTeam\":").append(point.capturingTeam()).append(",")
					.append("\"timeTicks\":").append(point.timeTicks())
					.append("}");
		}
		cpJson.append("]");

		StringBuilder json = new StringBuilder("{");
		json.append("\"updatedAtTick\":").append(server.getTickCount()).append(",");
		json.append("\"leaderboard\":{")
				.append("\"red\":").append(red).append(",")
				.append("\"yellow\":").append(yellow).append(",")
				.append("\"green\":").append(green).append(",")
				.append("\"blue\":").append(blue)
				.append("},");
		json.append("\"events\":{")
				.append("\"capturePointsActive\":").append(capturePointsActive).append(",")
				.append("\"randomEvent\":{")
				.append("\"active\":").append(randomEventActive).append(",")
				.append("\"name\":").append(jsonString(randomEventName)).append(",")
				.append("\"duration\":").append(randomEventDuration)
				.append("},")
				.append("\"globalEvent\":{")
				.append("\"active\":").append(globalEventActive).append(",")
				.append("\"name\":").append(jsonString(globalEventName)).append(",")
				.append("\"duration\":").append(globalEventDuration)
				.append("}")
				.append("},");
		json.append("\"capturePoints\":").append(cpJson);
		json.append("}");

		return json.toString();
	}

	private static int readNamedScore(Scoreboard scoreboard, String objectiveName, String holderName) {
		Objective objective = scoreboard.getObjective(objectiveName);
		if (objective == null) {
			return 0;
		}
		ReadOnlyScoreInfo info = scoreboard.getPlayerScoreInfo(ScoreHolder.forNameOnly(holderName), objective);
		return info != null ? info.value() : 0;
	}

	private static ServerPlayer firstOnlinePlayerOrNull(MinecraftServer server) {
		List<ServerPlayer> players = server.getPlayerList().getPlayers();
		return players.isEmpty() ? null : players.get(0);
	}

	/** Minimal hand-rolled JSON string escaping -- no extra dependency for this small a need. */
	private static String jsonString(String value) {
		StringBuilder sb = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
				case '"' -> sb.append("\\\"");
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				default -> {
					if (c < 0x20) {
						sb.append(String.format("\\u%04x", (int) c));
					} else {
						sb.append(c);
					}
				}
			}
		}
		sb.append("\"");
		return sb.toString();
	}
}