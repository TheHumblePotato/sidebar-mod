package com.lmssmp.sidebar;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reads the two event-name strings (random_event_name,
 * global_event_name) out of the datapack's command storage. Isolated
 * into its own file, separate from SidebarContentBuilder, specifically
 * because ResourceLocation/CommandStorage's exact API shape in this
 * project's 26.2 environment hasn't been confirmed yet -- if this one
 * file needs adjusting, nothing else in the mod is affected.
 *
 * IF THIS FILE DOESN'T COMPILE:
 * 1. First rule out a stale project sync -- run `./gradlew build` from
 *    a terminal. If that succeeds, this file is actually fine; your IDE
 *    just needs "Java: Clean the Java Language Server Workspace" +
 *    reload.
 * 2. If `./gradlew build` genuinely fails here, open CommandStorage in
 *    your IDE (Ctrl+Click into getCommandStorage() from
 *    MinecraftServer, or search the class directly) and look at the
 *    parameter type of its get(...) method. Swap that type in for
 *    ResourceLocation below, and update GAME_STORAGE_ID's construction
 *    to match however that type is built.
 */
final class GameStorageReader {

	/**
	 * VERIFY: storage ID guessed as "game:main" -- confirm against
	 * whatever `/data ... storage <id>` your datapack actually uses for
	 * random_event_name / global_event_name, and update if different.
	 */
	private static final Identifier GAME_STORAGE_ID =
        Identifier.fromNamespaceAndPath("events", "game");

	private GameStorageReader() {
	}

	/**
	 * Reads a string value out of the "game" command storage (e.g. set
	 * via `/data modify storage game:main random_event_name set value "..."`),
	 * or "" if the storage, tag, or key doesn't exist.
	 *
	 * VERIFY: CompoundTag#getString(key) is assumed to return
	 * Optional<String> (confirmed in this project's mappings), unwrapped
	 * with orElse("") below.
	 */
	static String readString(ServerPlayer player, String key) {
		CompoundTag tag = ((ServerLevel) player.level()).getServer().getCommandStorage().get(GAME_STORAGE_ID);
		if (tag == null) {
			return "";
		}
		return tag.getString(key).orElse("");
	}
}
