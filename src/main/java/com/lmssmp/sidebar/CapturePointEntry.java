package com.lmssmp.sidebar;

/**
 * One control point's full state, read directly off its backing
 * entity's per-entity scoreboard scores (see RealCapturePointProvider).
 *
 * order corresponds directly to the entity's own "capture_point" score
 * (1, 2, 3, ...) -- the same value the datapack's
 * `@n[scores={capture_point=N}]` selectors key off of.
 *
 * team / capturingTeam encoding matches the datapack's convention:
 * 0 = none/uncaptured, 1 = Red, 2 = Yellow, 3 = Green, 4 = Blue, 5 = Grey.
 *
 * capturingState: 0 = nobody capturing, 1 = one team capturing/
 * recapturing, 2 = two-or-more teams contesting at once. Per the spec,
 * both non-zero states render identically (⚔ + capturingTeam + time) --
 * there is no separate "contested" display.
 */
public record CapturePointEntry(
		int order,
		boolean enabled,
		int team,
		int capturingState,
		int capturingTeam,
		int timeTicks
) {
}
