package com.butas.claninfo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Player settings. Only personal preferences live here; clan texts come from clan.json (ClanContent).
 */
@ConfigGroup(ClanInfoConfig.GROUP)
public interface ClanInfoConfig extends Config
{
	String GROUP = "claninfo";

	@ConfigItem(
		keyName = "streamCheck",
		name = "Tikrinti, ar streamina",
		description = "Kas 5 min. patikrina klano streamerių YouTube kanalus (jungiasi prie youtube.com)",
		position = 21
	)
	default boolean streamCheck()
	{
		return true;
	}

	@ConfigItem(
		keyName = "streamChatMessage",
		name = "Žinutė chatbox'e",
		description = "Parodyti žinutę tavo chatbox'e, kai streameris pradeda transliaciją",
		position = 22
	)
	default boolean streamChatMessage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "streamNotification",
		name = "RuneLite pranešimas",
		description = "Taip pat parodyti RuneLite (darbalaukio) pranešimą",
		position = 23
	)
	default boolean streamNotification()
	{
		return false;
	}

	@ConfigItem(
		keyName = "clanMessages",
		name = "Klano priminimai chatbox'e",
		description = "Prisijungus – kas vyksta ir ar netrūksta privalomų pluginų; renginio pradžia ir paskutinė para",
		position = 20
	)
	default boolean clanMessages()
	{
		return true;
	}
}
