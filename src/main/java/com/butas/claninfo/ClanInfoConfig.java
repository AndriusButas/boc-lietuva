package com.butas.claninfo;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ClanInfoConfig.GROUP)
public interface ClanInfoConfig extends Config
{
	String GROUP = "claninfo";

	@ConfigItem(
		keyName = "clanName",
		name = "Clan name",
		description = "The in-game clan name players should look for",
		position = 1
	)
	default String clanName()
	{
		return "BOC Lietuva";
	}

	@ConfigItem(
		keyName = "about",
		name = "About",
		description = "Short description of the clan",
		position = 2
	)
	default String about()
	{
		return "A friendly clan for all account types. We do bossing, skilling events and help new players.";
	}

	@ConfigItem(
		keyName = "joinSteps",
		name = "How to join",
		description = "Steps to join the clan, one step per line",
		position = 3
	)
	default String joinSteps()
	{
		return "Open the Clan tab (Grouping → Your Clan).\n"
			+ "Join our guest chat: click 'Guest' and type BOC Lietuva.\n"
			+ "Ask Mindaugas for an invite.\n"
			+ "Meet Mindaugas in-game to receive the 'Recruit' invite.";
	}

	@ConfigItem(
		keyName = "requirements",
		name = "Requirements",
		description = "Requirements to join, one per line (leave empty to hide)",
		position = 4
	)
	default String requirements()
	{
		return "Total level 1500+";
	}

	@ConfigItem(
		keyName = "contacts",
		name = "Contact (RSN)",
		description = "Who to message in-game for an invite, comma separated",
		position = 5
	)
	default String contacts()
	{
		return "Mindaugas";
	}

	@ConfigItem(
		keyName = "discordUrl",
		name = "Discord invite",
		description = "Discord invite link (leave empty to hide the button)",
		position = 6
	)
	default String discordUrl()
	{
		return "https://discord.gg/your-invite";
	}

	@ConfigItem(
		keyName = "websiteUrl",
		name = "Website / stream",
		description = "Optional extra link, e.g. clan website or YouTube channel",
		position = 7
	)
	default String websiteUrl()
	{
		return "https://www.youtube.com/";
	}

	@ConfigItem(
		keyName = "websiteLabel",
		name = "Extra link label",
		description = "Button text for the extra link",
		position = 8
	)
	default String websiteLabel()
	{
		return "Watch our stream";
	}
}
