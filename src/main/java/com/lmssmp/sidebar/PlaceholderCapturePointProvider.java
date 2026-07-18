package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Milestone 12's default CapturePointProvider: three static entries,
 * identical to Milestone 8's hard-coded placeholders, just moved behind
 * the CapturePointProvider interface. A later milestone can register a
 * different implementation (e.g. one that scans tagged armor stands)
 * via SidebarContentBuilder#setCapturePointProvider without this class,
 * SidebarContentBuilder's other responsibilities, or SidebarManager
 * needing to change.
 */
public final class PlaceholderCapturePointProvider implements CapturePointProvider {

	@Override
	public List<CapturePointEntry> getCapturePoints(ServerPlayer player) {
		return List.of(
				CapturePointEntry.nameOnly("Alpha"),
				CapturePointEntry.nameOnly("Bravo"),
				CapturePointEntry.nameOnly("Charlie")
		);
	}
}
