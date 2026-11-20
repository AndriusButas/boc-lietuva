package com.butas.claninfo;

import java.time.LocalDate;
import java.util.Map;
import lombok.Value;

/**
 * Everything the panel shows, provided by the plugin.
 */
interface PanelData
{
	ClanContent content();

	/**
	 * The streamer's current live stream, or null when not live.
	 */
	LiveStatus live(String streamerName);

	ClanEvents.State events();

	/**
	 * The logged-in player, or null when not logged in.
	 */
	PlayerInfo player();

	PluginStatus pluginStatus(ClanContent.RequiredPlugin plugin);

	enum PluginStatus
	{
		OK,
		MISSING,
		DISABLED,
		SETTING_OFF
	}

	@Value
	class PlayerInfo
	{
		String name;
		boolean member;          // in the clan from content().clanName
		String rankTitle;        // in-game clan title, e.g. "Iron"; null if not a member
		LocalDate joinDate;      // date joined the clan; null if unknown
		int totalLevel;
		Map<String, Integer> skillLevels; // "slayer" -> 70
	}
}
