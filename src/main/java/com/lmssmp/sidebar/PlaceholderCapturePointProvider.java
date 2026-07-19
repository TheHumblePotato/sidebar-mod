package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Static three-point stand-in for local testing without any armor
 * stands placed. No longer the default (RealCapturePointProvider is,
 * as of Milestone 15) -- register this instead via
 * SidebarContentBuilder#setCapturePointProvider if you want to preview
 * the Events layout without setting up real control points.
 *
 * Updated to match the Milestone 15 CapturePointEntry shape
 * (order/enabled/team/capturingState/capturingTeam/timeTicks) -- the
 * old CapturePointEntry.nameOnly(String) factory this class used to
 * call no longer exists.
 */
public final class PlaceholderCapturePointProvider implements CapturePointProvider {

	@Override
	public List<CapturePointEntry> getCapturePoints(ServerPlayer player) {
		return List.of(
				new CapturePointEntry(1, true, 0, 0, 0, 0),
				new CapturePointEntry(2, true, 0, 0, 0, 0),
				new CapturePointEntry(3, true, 0, 0, 0, 0)
		);
	}
}
