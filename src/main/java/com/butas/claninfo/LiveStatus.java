package com.butas.claninfo;

import com.google.gson.Gson;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;

/**
 * A live stream that is currently running, read from a YouTube channel's /live page.
 */
@Value
class LiveStatus
{
	private static final Pattern VIDEO_ID = Pattern.compile(
		"\"currentVideoEndpoint\":\\{.{0,400}?\"videoId\":\"([\\w-]{11})\"");
	private static final Pattern TITLE = Pattern.compile(
		"\"videoPrimaryInfoRenderer\":\\{\"title\":\\{\"runs\":\\[\\{\"text\":\"((?:[^\"\\\\]|\\\\.)*)\"");

	String videoId;
	String title;

	String watchUrl()
	{
		return "https://www.youtube.com/watch?v=" + videoId;
	}

	/**
	 * Reads the HTML of https://www.youtube.com/@channel/live.
	 * When the channel is live, YouTube serves the live video's watch page there;
	 * otherwise it serves the channel page (or an "upcoming" page for scheduled streams).
	 *
	 * @return the live stream, or null if the channel is not live right now
	 */
	static LiveStatus parse(String html, Gson gson)
	{
		if (html == null
			|| !html.contains("\"isLive\":true")
			|| html.contains("\"upcomingEventData\""))
		{
			return null;
		}

		Matcher id = VIDEO_ID.matcher(html);
		if (!id.find())
		{
			return null;
		}

		String title = "";
		Matcher t = TITLE.matcher(html);
		if (t.find())
		{
			try
			{
				// The title is a JSON string literal: let Gson decode escapes like &
				title = gson.fromJson("\"" + t.group(1) + "\"", String.class);
			}
			catch (RuntimeException e)
			{
				title = t.group(1);
			}
		}

		return new LiveStatus(id.group(1), title);
	}
}
