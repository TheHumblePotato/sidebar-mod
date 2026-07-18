package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Supplies the capture point entries the sidebar should display for a
 * given player. Implementations decide where that data actually comes
 * from -- static placeholders today, tagged armor stands
 * (control_point / cp_order / cp_owner / cp_state) in a later milestone,
 * possibly something else after that. SidebarContentBuilder only
 * depends on this interface, and SidebarManager doesn't know this
 * concept exists at all -- both only ever see the plain Component lines
 * that come out the other end.
 */
public interface CapturePointProvider {

	List<CapturePointEntry> getCapturePoints(ServerPlayer player);
}
