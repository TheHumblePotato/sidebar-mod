package com.lmssmp.sidebar;

import net.minecraft.server.level.ServerPlayer;

/**
 * Answers exactly one question: what sidebar mode should this player
 * see? Implementations decide where that answer comes from -- a fixed
 * placeholder today, real datapack/config state in a later milestone.
 * No packet logic, no rendering -- same abstraction shape as
 * CapturePointProvider.
 */
public interface SidebarModeProvider {

	SidebarMode getMode(ServerPlayer player);
}
