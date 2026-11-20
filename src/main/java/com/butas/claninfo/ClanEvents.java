package com.butas.claninfo;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.Value;

/**
 * Clan events, read from a simple text file kept in the plugin's GitHub repository.
 *
 * <pre>
 * # comment
 * Name | Start | End | Description
 * Savaitės įgūdžio varžybos (SOTW) | 2026-11-16 | 2026-11-22 | Slayer
 * BOTW | 2026-11-27 20:00 | 2026-11-27 22:00 |
 * </pre>
 * A date without a time means the whole day. All times are Lithuanian time.
 * Start may be empty ("date will be announced"); end may be empty (lasts that day).
 */
final class ClanEvents
{
	static final String URL = "https://raw.githubusercontent.com/AndriusButas/boc-lietuva/main/events.txt";
	static final int WOM_GROUP_ID = 9521;
	static final String WOM_COMPETITIONS_API = "https://api.wiseoldman.net/v2/groups/" + WOM_GROUP_ID + "/competitions";
	static final String WOM_COMPETITION_API = "https://api.wiseoldman.net/v2/competitions/";
	static final String WOM_COMPETITION_PAGE = "https://wiseoldman.net/competitions/";
	static final ZoneId CLAN_ZONE = ZoneId.of("Europe/Vilnius");

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd H:mm");
	private static final String[] MONTHS = {
		"sausio", "vasario", "kovo", "balandžio", "gegužės", "birželio",
		"liepos", "rugpjūčio", "rugsėjo", "spalio", "lapkričio", "gruodžio"
	};

	private ClanEvents()
	{
	}

	/**
	 * What the plugin currently knows about events.
	 */
	@Value
	static class State
	{
		static final State LOADING = new State(Collections.emptyList(), Collections.emptyMap(), false, false);

		List<Event> events;
		// WOM competition id -> participants, best first
		Map<Integer, List<Entry>> leaderboards;
		boolean loaded;
		boolean failed;
	}

	/**
	 * One player's progress in a WOM competition.
	 */
	@Value
	static class Entry
	{
		String name;
		long gained;
	}

	@Value
	static class Event
	{
		String name;
		ZonedDateTime start; // null = date not announced yet
		ZonedDateTime end;   // exclusive
		boolean wholeDays;
		String description;
		Integer womId;   // set for Wise Old Man competitions
		String metric;   // WOM metric, e.g. "thieving", "phantom_muspah"

		boolean isLive(ZonedDateTime now)
		{
			return start != null && !now.isBefore(start) && now.isBefore(end);
		}

		boolean isUpcoming(ZonedDateTime now)
		{
			return start == null || now.isBefore(start);
		}
	}

	static List<Event> parse(String text)
	{
		List<Event> events = new ArrayList<>();
		if (text == null)
		{
			return events;
		}

		for (String rawLine : text.split("\\r?\\n"))
		{
			String line = rawLine.trim();
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}

			String[] parts = line.split("\\|", -1);
			String name = parts[0].trim();
			if (name.isEmpty())
			{
				continue;
			}

			String startText = parts.length > 1 ? parts[1].trim() : "";
			String endText = parts.length > 2 ? parts[2].trim() : "";
			String description = parts.length > 3 ? parts[3].trim() : "";

			try
			{
				ZonedDateTime start = null;
				ZonedDateTime end = null;
				boolean wholeDays = false;

				if (!startText.isEmpty())
				{
					wholeDays = !startText.contains(":");
					start = parseTime(startText, false);
					end = endText.isEmpty()
						? (wholeDays ? start.plusDays(1) : start.plusHours(2))
						: parseTime(endText, true);
				}

				events.add(new Event(name, start, end, wholeDays, description, null, null));
			}
			catch (DateTimeParseException e)
			{
				// Skip lines with a broken date instead of breaking the whole list
			}
		}
		return events;
	}

	/**
	 * "2026-11-16" (whole day) or "2026-11-16 18:00". A date-only end means the end of that day.
	 */
	private static ZonedDateTime parseTime(String text, boolean isEnd)
	{
		if (text.contains(":"))
		{
			return LocalDateTime.parse(text, DATE_TIME).atZone(CLAN_ZONE);
		}
		LocalDate date = LocalDate.parse(text);
		return (isEnd ? date.plusDays(1) : date).atStartOfDay(CLAN_ZONE);
	}

	static List<Event> parseWomCompetitions(String json, Gson gson)
	{
		List<Event> events = new ArrayList<>();
		ZonedDateTime now = ZonedDateTime.now(CLAN_ZONE);
		for (JsonElement el : gson.fromJson(json, JsonArray.class))
		{
			JsonObject c = el.getAsJsonObject();
			ZonedDateTime start = Instant.parse(c.get("startsAt").getAsString()).atZone(CLAN_ZONE);
			ZonedDateTime end = Instant.parse(c.get("endsAt").getAsString()).atZone(CLAN_ZONE);
			if (!end.isAfter(now))
			{
				continue; // finished
			}
			events.add(new Event(
				c.get("title").getAsString(), start, end, false, "",
				c.get("id").getAsInt(), c.get("metric").getAsString()));
		}
		return events;
	}

	static List<Entry> parseWomLeaderboard(String json, Gson gson)
	{
		List<Entry> entries = new ArrayList<>();
		JsonObject competition = gson.fromJson(json, JsonObject.class);
		for (JsonElement el : competition.getAsJsonArray("participations"))
		{
			JsonObject p = el.getAsJsonObject();
			JsonObject player = p.getAsJsonObject("player");
			String name = player.has("displayName") ? player.get("displayName").getAsString() : player.get("username").getAsString();
			long gained = p.getAsJsonObject("progress").get("gained").getAsLong();
			entries.add(new Entry(name, gained));
		}
		entries.sort((a, b) -> Long.compare(b.getGained(), a.getGained()));
		return entries;
	}

	private static final java.util.Set<String> XP_METRICS = new java.util.HashSet<>(java.util.Arrays.asList(
		"overall", "attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic", "cooking",
		"woodcutting", "fletching", "fishing", "firemaking", "crafting", "smithing", "mining", "herblore",
		"agility", "thieving", "slayer", "farming", "runecrafting", "hunter", "construction", "sailing"));

	static boolean isXpMetric(String metric)
	{
		return metric != null && XP_METRICS.contains(metric);
	}

	/**
	 * e.g. "1.2M XP", "125k XP", "37 KC", "12 clue"
	 */
	static String formatGained(String metric, long gained)
	{
		if (isXpMetric(metric))
		{
			String n;
			if (gained >= 1_000_000)
			{
				n = String.format("%.1fM", gained / 1_000_000.0);
			}
			else if (gained >= 10_000)
			{
				n = (gained / 1000) + "k";
			}
			else
			{
				n = Long.toString(gained);
			}
			return "+" + n + " XP";
		}
		if (metric != null && metric.startsWith("clue_scrolls"))
		{
			return "+" + gained + " clue";
		}
		return "+" + gained + " KC";
	}

	/**
	 * Lowercase, with spaces/underscores/hyphens unified, as WOM stores usernames.
	 */
	static String normalizeName(String name)
	{
		return name == null ? "" : name.replace('\u00a0', ' ').replace('_', ' ').replace('-', ' ').trim().toLowerCase();
	}

	static List<Event> live(List<Event> events, ZonedDateTime now)
	{
		List<Event> result = new ArrayList<>();
		for (Event e : events)
		{
			if (e.isLive(now))
			{
				result.add(e);
			}
		}
		result.sort(Comparator.comparing(Event::getEnd));
		return result;
	}

	static List<Event> upcoming(List<Event> events, ZonedDateTime now)
	{
		List<Event> result = new ArrayList<>();
		for (Event e : events)
		{
			if (e.isUpcoming(now))
			{
				result.add(e);
			}
		}
		result.sort(Comparator.comparing(Event::getStart, Comparator.nullsLast(Comparator.naturalOrder())));
		return Collections.unmodifiableList(result);
	}

	/**
	 * e.g. "lapkričio 16–22 d.", "lapkričio 30 d. – gruodžio 1 d.", "lapkričio 27 d. 20:00–22:00"
	 */
	static String formatDates(Event e)
	{
		if (e.getStart() == null)
		{
			return "Data bus paskelbta";
		}

		ZonedDateTime s = e.getStart().withZoneSameInstant(CLAN_ZONE);
		ZonedDateTime en = e.getEnd().withZoneSameInstant(CLAN_ZONE);

		if (e.isWholeDays())
		{
			ZonedDateTime last = en.minusDays(1);
			if (last.toLocalDate().equals(s.toLocalDate()))
			{
				return month(s) + " " + s.getDayOfMonth() + " d.";
			}
			if (last.getMonthValue() == s.getMonthValue())
			{
				return month(s) + " " + s.getDayOfMonth() + "–" + last.getDayOfMonth() + " d.";
			}
			return month(s) + " " + s.getDayOfMonth() + " d. – " + month(last) + " " + last.getDayOfMonth() + " d.";
		}

		String startText = month(s) + " " + s.getDayOfMonth() + " d. " + time(s);
		if (en.toLocalDate().equals(s.toLocalDate()))
		{
			return startText + "–" + time(en);
		}
		return startText + " – " + month(en) + " " + en.getDayOfMonth() + " d. " + time(en);
	}

	/**
	 * e.g. "dar 2 d.", "dar 5 val.", "dar 40 min."
	 */
	static String formatRemaining(ZonedDateTime now, ZonedDateTime until)
	{
		Duration d = Duration.between(now, until);
		long minutes = Math.max(1, d.toMinutes());
		if (minutes >= 48 * 60)
		{
			return (minutes / (24 * 60)) + " d.";
		}
		if (minutes >= 60)
		{
			return (minutes / 60) + " val.";
		}
		return minutes + " min.";
	}

	private static String month(ZonedDateTime t)
	{
		return MONTHS[t.getMonthValue() - 1];
	}

	private static String time(ZonedDateTime t)
	{
		return String.format("%02d:%02d", t.getHour(), t.getMinute());
	}
}
