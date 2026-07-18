package com.lmssmp.sidebar;

import java.util.Optional;

/**
 * One entry in the sidebar's capture point section.
 *
 * owner and progress are unused placeholders for Milestone 12 -- always
 * Optional.empty() until a later milestone actually reads
 * ownership/progress data (from tagged armor stands: cp_owner, cp_state).
 * Modeling them now, even unused, means CapturePointProvider's return
 * type won't need to change shape again when that data shows up -- only
 * the provider implementation and SidebarContentBuilder's line-building
 * will need to change.
 */
public record CapturePointEntry(String name, Optional<String> owner, Optional<Integer> progress) {

	/** Convenience for entries that only have a name so far, e.g. today's placeholders. */
	public static CapturePointEntry nameOnly(String name) {
		return new CapturePointEntry(name, Optional.empty(), Optional.empty());
	}
}
