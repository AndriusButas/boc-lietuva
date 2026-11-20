package com.butas.claninfo;

import com.google.gson.Gson;
import java.awt.Color;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Clan texts, links, streamers and the rank ladder.
 * Loaded from clan.json in the plugin's GitHub repository, so the clan can change them without a plugin update.
 * A copy is bundled with the plugin and used until (or if) the online version can't be loaded.
 */
class ClanContent
{
	static final String URL = "https://raw.githubusercontent.com/AndriusButas/boc-lietuva/main/clan.json";

	String clanName;
	String about;
	String discordUrl;
	String streamUrl;
	String streamLabel;
	List<String> joinSteps;
	String contacts;
	List<RequiredPlugin> requiredPlugins;
	String requiredPluginsNote;
	List<Streamer> streamers;
	String ranksIntro;
	List<Tier> tiers;
	List<String> howChecked;
	List<String> bot;

	static class RequiredPlugin
	{
		String name;       // shown in the panel
		String plugin;     // RuneLite plugin name to look for
		String config;     // optional "group.key" setting that must be on
		String configHint; // what to do if that setting is off
	}

	static class Streamer
	{
		String name;
		String role;
		String url;
	}

	static class Tier
	{
		String title;
		String note;
		List<Rank> ranks;
	}

	static class Rank
	{
		String name;
		String color;
		Integer total;
		Map<String, Integer> skills;
		Integer months;
		List<String> other;

		Color color()
		{
			try
			{
				return Color.decode(color);
			}
			catch (RuntimeException e)
			{
				return Color.LIGHT_GRAY;
			}
		}

		/**
		 * e.g. "1850 total · visi Medium diaries · 90 Attack/Strength/Defence/Ranged/Magic · 77 Prayer"
		 */
		String describe()
		{
			List<String> parts = new ArrayList<>();
			if (total != null)
			{
				parts.add(total + " total");
			}
			parts.addAll(other());
			parts.addAll(describeSkills());
			if (months != null)
			{
				parts.add(months + " mėn. BOC");
			}
			return String.join(" · ", parts);
		}

		List<String> describeSkills()
		{
			// Group skills by level: {90: [Attack, Strength...], 77: [Prayer]}
			Map<Integer, List<String>> byLevel = new LinkedHashMap<>();
			if (skills != null)
			{
				for (Map.Entry<String, Integer> e : skills.entrySet())
				{
					byLevel.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(skillName(e.getKey()));
				}
			}
			List<String> result = new ArrayList<>();
			for (Map.Entry<Integer, List<String>> e : byLevel.entrySet())
			{
				result.add(e.getKey() + " " + String.join("/", e.getValue()));
			}
			return result;
		}

		List<String> other()
		{
			return other == null ? Collections.emptyList() : other;
		}
	}

	static String skillName(String key)
	{
		return key.isEmpty() ? key : Character.toUpperCase(key.charAt(0)) + key.substring(1).toLowerCase();
	}

	/**
	 * Finds a rank by its in-game clan title (case-insensitive), or null.
	 */
	Rank findRank(String title)
	{
		if (title == null)
		{
			return null;
		}
		for (Rank r : allRanks())
		{
			if (r.name.equalsIgnoreCase(title.trim()))
			{
				return r;
			}
		}
		return null;
	}

	/**
	 * The rank after the given one in the ladder, or null if it's the last one.
	 */
	Rank nextRank(Rank rank)
	{
		List<Rank> all = allRanks();
		int i = all.indexOf(rank);
		return i >= 0 && i + 1 < all.size() ? all.get(i + 1) : null;
	}

	List<Rank> allRanks()
	{
		List<Rank> all = new ArrayList<>();
		for (Tier t : tiers)
		{
			all.addAll(t.ranks);
		}
		return all;
	}

	/**
	 * A content file is usable only if the parts the panel relies on are present.
	 */
	boolean isValid()
	{
		return clanName != null && joinSteps != null && requiredPlugins != null
			&& streamers != null && tiers != null && howChecked != null && bot != null;
	}

	static ClanContent parse(String json, Gson gson)
	{
		ClanContent c = gson.fromJson(json, ClanContent.class);
		return c != null && c.isValid() ? c : null;
	}

	/**
	 * The copy bundled with the plugin.
	 */
	static ClanContent bundled(Gson gson)
	{
		try (InputStream in = ClanContent.class.getResourceAsStream("clan.json"))
		{
			ClanContent c = gson.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), ClanContent.class);
			if (c != null && c.isValid())
			{
				return c;
			}
		}
		catch (Exception e)
		{
			// fall through
		}
		throw new IllegalStateException("Bundled clan.json is missing or invalid");
	}
}
