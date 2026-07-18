package com.lmssmp.sidebar;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Immutable snapshot of one player's sidebar: a title and an ordered
 * list of lines. Both are full Components rather than Strings, so any
 * Minecraft-native formatting (team colors, bold/italic, etc.) picked up
 * by SidebarContentBuilder survives unchanged all the way through to the
 * outgoing packets in SidebarManager.
 */
public record SidebarContent(Component title, List<Component> lines) {
}
