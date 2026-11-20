package com.butas.claninfo;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@PluginDescriptor(
	name = "BOC Lietuva",
	description = "BOC Lietuva clan: how to join, ranks, clan events with Wise Old Man leaderboards and stream alerts",
	tags = {"clan", "join", "info", "recruit", "discord", "stream", "youtube", "events", "wise old man"}
)
public class ClanInfoPlugin extends Plugin implements PanelData
{
	// How often (in game ticks of 0.6s) the player's clan info and reminders are re-checked
	private static final int PLAYER_CHECK_TICKS = 10;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClanInfoConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Notifier notifier;

	private ClanInfoPanel panel;
	private NavigationButton navButton;

	// Clan texts, ranks and streamers (bundled copy, replaced by the online clan.json when loaded)
	private volatile ClanContent content;

	// Events
	private volatile ClanEvents.State eventsState = ClanEvents.State.LOADING;
	private volatile List<ClanEvents.Event> manualEvents = Collections.emptyList(); // events.txt
	private volatile List<ClanEvents.Event> womEvents = Collections.emptyList();    // Wise Old Man
	private final Map<Integer, List<ClanEvents.Entry>> leaderboards = new ConcurrentHashMap<>();
	private volatile boolean manualLoaded;
	private volatile boolean womLoaded;
	private volatile boolean loadFailed;

	// Logged-in player (null when logged out)
	private volatile PlayerInfo player;
	private int tickCounter;

	// Reminders already shown in this login session
	private boolean loginRemindersSent;
	private Set<String> knownLiveEvents;
	private final Set<String> endingWarned = ConcurrentHashMap.newKeySet();

	// Streams: streamer name -> current live stream (absent when not live)
	private final Map<String, LiveStatus> live = new ConcurrentHashMap<>();
	private final Set<String> announcedStreams = ConcurrentHashMap.newKeySet();

	// Chat messages waiting for the player to log in
	private final Queue<String> pendingChat = new ConcurrentLinkedQueue<>();

	@Override
	protected void startUp()
	{
		content = ClanContent.bundled(gson);
		panel = new ClanInfoPanel(this);

		navButton = NavigationButton.builder()
			.tooltip("BOC Lietuva")
			.icon(createIcon())
			.priority(10)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		loadOnlineData();
		checkStreams();
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		live.clear();
		pendingChat.clear();
		player = null;
	}

	// ---- PanelData ----

	@Override
	public ClanContent content()
	{
		return content;
	}

	@Override
	public LiveStatus live(String streamerName)
	{
		return live.get(streamerName);
	}

	@Override
	public ClanEvents.State events()
	{
		return eventsState;
	}

	@Override
	public PlayerInfo player()
	{
		return player;
	}

	@Override
	public PluginStatus pluginStatus(ClanContent.RequiredPlugin required)
	{
		Plugin found = null;
		for (Plugin p : pluginManager.getPlugins())
		{
			PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class);
			if (d != null && d.name().equalsIgnoreCase(required.plugin))
			{
				found = p;
				break;
			}
		}
		if (found == null)
		{
			return PluginStatus.MISSING;
		}
		if (!pluginManager.isPluginEnabled(found))
		{
			return PluginStatus.DISABLED;
		}
		if (required.config != null && required.config.contains("."))
		{
			int dot = required.config.indexOf('.');
			String value = configManager.getConfiguration(required.config.substring(0, dot), required.config.substring(dot + 1));
			if (!"true".equalsIgnoreCase(value))
			{
				return PluginStatus.SETTING_OFF;
			}
		}
		return PluginStatus.OK;
	}

	// ---- Game events ----

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			String message;
			while ((message = pendingChat.poll()) != null)
			{
				queueChat(message);
			}
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			player = null;
			loginRemindersSent = false;
			knownLiveEvents = null;
			refreshPanel();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (++tickCounter % PLAYER_CHECK_TICKS != 0)
		{
			return;
		}
		updatePlayer();
		sendReminders();
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		refreshPanel();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ClanInfoConfig.GROUP.equals(event.getGroup()))
		{
			if (!config.streamCheck())
			{
				live.clear();
			}
			else if ("streamCheck".equals(event.getKey()))
			{
				checkStreams();
			}
		}
		// Also covers other plugins' settings, e.g. XP Updater's "Wise Old Man" option
		refreshPanel();
	}

	/**
	 * Reads the player's clan, rank title, join date and levels. Runs on the client thread.
	 */
	private void updatePlayer()
	{
		Player local = client.getLocalPlayer();
		if (client.getGameState() != GameState.LOGGED_IN || local == null || local.getName() == null)
		{
			return;
		}

		ClanSettings clan = client.getClanSettings();
		boolean member = clan != null && clan.getName() != null && clan.getName().equalsIgnoreCase(content.clanName);
		String title = null;
		LocalDate joined = null;
		if (member)
		{
			ClanMember me = clan.findMember(local.getName());
			if (me != null)
			{
				ClanTitle t = clan.titleForRank(me.getRank());
				title = t == null ? null : t.getName();
				joined = me.getJoinDate();
			}
		}

		Map<String, Integer> skills = new HashMap<>();
		for (Skill s : Skill.values())
		{
			if (s != Skill.OVERALL)
			{
				skills.put(s.name().toLowerCase(), client.getRealSkillLevel(s));
			}
		}

		PlayerInfo updated = new PlayerInfo(local.getName(), member, title, joined, client.getTotalLevel(), skills);
		if (!updated.equals(player))
		{
			player = updated;
			refreshPanel();
		}
	}

	// ---- Chat reminders ----

	/**
	 * On login: missing required plugins and a summary of live events.
	 * While playing: when an event starts, and when one has less than 24 hours left.
	 */
	private void sendReminders()
	{
		PlayerInfo p = player;
		if (p == null || !config.clanMessages() || !eventsState.isLoaded())
		{
			return;
		}

		ZonedDateTime now = ZonedDateTime.now(ClanEvents.CLAN_ZONE);
		List<ClanEvents.Event> liveEvents = ClanEvents.live(eventsState.getEvents(), now);

		if (!loginRemindersSent)
		{
			loginRemindersSent = true;

			if (p.isMember())
			{
				List<String> missing = new ArrayList<>();
				for (ClanContent.RequiredPlugin rp : content.requiredPlugins)
				{
					PluginStatus s = pluginStatus(rp);
					if (s == PluginStatus.SETTING_OFF)
					{
						missing.add(rp.name + " (" + rp.configHint + ")");
					}
					else if (s != PluginStatus.OK)
					{
						missing.add(rp.name);
					}
				}
				if (!missing.isEmpty())
				{
					clanChat("Trukst privalomu pluginu: " + String.join(", ", missing) + ". Zr. BOC Lietuva panele.");
				}
			}

			if (!liveEvents.isEmpty())
			{
				List<String> names = new ArrayList<>();
				for (ClanEvents.Event e : liveEvents)
				{
					names.add(e.getName() + " (liko " + ClanEvents.formatRemaining(now, e.getEnd()) + ")");
				}
				clanChat("Siuo metu vyksta: " + String.join(", ", names));
			}
		}

		Set<String> liveKeys = new java.util.HashSet<>();
		for (ClanEvents.Event e : liveEvents)
		{
			String key = eventKey(e);
			liveKeys.add(key);

			// Started while the player is online
			if (knownLiveEvents != null && !knownLiveEvents.contains(key))
			{
				clanChat(e.getName() + " prasidejo! Sekmes!");
			}

			// Less than 24 hours left (events longer than a day only)
			Duration left = Duration.between(now, e.getEnd());
			Duration length = Duration.between(e.getStart(), e.getEnd());
			if (left.toHours() < 24 && length.toHours() > 24 && endingWarned.add(key))
			{
				clanChat(e.getName() + " baigiasi po " + ClanEvents.formatRemaining(now, e.getEnd()) + "!");
			}
		}
		knownLiveEvents = liveKeys;
	}

	private static String eventKey(ClanEvents.Event e)
	{
		return e.getName() + "|" + e.getStart();
	}

	private void clanChat(String text)
	{
		queueChat(new ChatMessageBuilder()
			.append(ChatColorType.HIGHLIGHT)
			.append("[BOC Lietuva] ")
			.append(ChatColorType.NORMAL)
			.append(toChatText(text, 200))
			.build());
	}

	private void queueChat(String message)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	// ---- Online data: clan.json, events.txt, Wise Old Man ----

	@Schedule(period = 15, unit = ChronoUnit.MINUTES, asynchronous = true)
	public void loadOnlineData()
	{
		fetch(ClanContent.URL, text ->
		{
			ClanContent loaded = ClanContent.parse(text, gson);
			if (loaded != null)
			{
				content = loaded;
			}
		});

		fetch(ClanEvents.URL, text ->
		{
			manualEvents = ClanEvents.parse(text);
			manualLoaded = true;
		});

		fetch(ClanEvents.WOM_COMPETITIONS_API, text ->
		{
			womEvents = ClanEvents.parseWomCompetitions(text, gson);
			womLoaded = true;

			// Leaderboards only for competitions running right now
			ZonedDateTime now = ZonedDateTime.now(ClanEvents.CLAN_ZONE);
			List<Integer> liveIds = new ArrayList<>();
			for (ClanEvents.Event e : womEvents)
			{
				if (e.isLive(now))
				{
					liveIds.add(e.getWomId());
				}
			}
			leaderboards.keySet().retainAll(liveIds);
			for (int id : liveIds)
			{
				fetch(ClanEvents.WOM_COMPETITION_API + id, details ->
					leaderboards.put(id, ClanEvents.parseWomLeaderboard(details, gson)));
			}
		});
	}

	private void fetch(String url, Consumer<String> onSuccess)
	{
		okHttpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Could not load {}", url, e);
				loadFailed = true;
				publishEvents();
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						loadFailed = true;
					}
					else
					{
						onSuccess.accept(body.string());
					}
				}
				catch (RuntimeException e)
				{
					log.debug("Could not read {}", url, e);
					loadFailed = true;
				}
				publishEvents();
			}
		});
	}

	private void publishEvents()
	{
		List<ClanEvents.Event> all = new ArrayList<>(womEvents);
		all.addAll(manualEvents);
		boolean loaded = manualLoaded || womLoaded;
		eventsState = new ClanEvents.State(all, new HashMap<>(leaderboards), loaded, !loaded && loadFailed);
		refreshPanel();
	}

	// ---- Streams ----

	@Schedule(period = 5, unit = ChronoUnit.MINUTES, asynchronous = true)
	public void checkStreams()
	{
		if (!config.streamCheck())
		{
			return;
		}

		for (ClanContent.Streamer streamer : content.streamers)
		{
			Request request = new Request.Builder()
				.url(streamer.url.replaceAll("/+$", "") + "/live")
				// Skips YouTube's cookie-consent page (non-essential cookies rejected)
				.header("Cookie", "SOCS=CAI")
				.header("Accept-Language", "en-US,en;q=0.9")
				.build();

			okHttpClient.newCall(request).enqueue(new Callback()
			{
				@Override
				public void onFailure(Call call, IOException e)
				{
					log.debug("Stream check failed for {}", streamer.name, e);
				}

				@Override
				public void onResponse(Call call, Response response) throws IOException
				{
					try (ResponseBody body = response.body())
					{
						if (!response.isSuccessful() || body == null)
						{
							return;
						}
						updateStream(streamer, LiveStatus.parse(body.string(), gson));
					}
				}
			});
		}
	}

	private void updateStream(ClanContent.Streamer streamer, LiveStatus status)
	{
		LiveStatus previous = status == null ? live.remove(streamer.name) : live.put(streamer.name, status);

		if (status != null && announcedStreams.add(status.getVideoId()))
		{
			announceStream(streamer, status);
		}

		if (previous == null ? status != null : !previous.equals(status))
		{
			refreshPanel();
		}
	}

	private void announceStream(ClanContent.Streamer streamer, LiveStatus status)
	{
		if (config.streamChatMessage())
		{
			String title = toChatText(status.getTitle(), 60);
			String message = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("[BOC Lietuva] ")
				.append(ChatColorType.NORMAL)
				.append(toChatText(streamer.name, 30) + " dabar LIVE YouTube!" + (title.isEmpty() ? "" : " " + title))
				.build();

			if (client.getGameState() == GameState.LOGGED_IN)
			{
				queueChat(message);
			}
			else
			{
				pendingChat.add(message);
			}
		}

		if (config.streamNotification())
		{
			notifier.notify(streamer.name + " dabar streamina YouTube!");
		}
	}

	// ---- Helpers ----

	private void refreshPanel()
	{
		ClanInfoPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(p::rebuild);
		}
	}

	/**
	 * The in-game chat font has no Lithuanian letters, so "ĮKRITO" becomes "IKRITO"
	 * and other unsupported characters (emoji etc.) are dropped.
	 */
	static String toChatText(String text, int maxLength)
	{
		if (text == null)
		{
			return "";
		}
		String plain = Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.replace('–', '-')
			.replaceAll("[^\\x20-\\x7E]", "")
			.replaceAll("[<>]", "")
			.replaceAll("\\s+", " ")
			.trim();
		return plain.length() > maxLength ? plain.substring(0, maxLength - 3) + "..." : plain;
	}

	/**
	 * Draws the small gold shield shown in the RuneLite sidebar.
	 */
	static BufferedImage createIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Polygon shield = new Polygon(new int[]{2, 14, 14, 8, 2}, new int[]{1, 1, 9, 15, 9}, 5);
		g.setColor(new Color(214, 160, 40));
		g.fillPolygon(shield);
		g.setColor(new Color(90, 60, 10));
		g.setStroke(new BasicStroke(1f));
		g.drawPolygon(shield);
		g.setColor(Color.WHITE);
		g.fillRect(7, 3, 3, 2);
		g.fillRect(7, 6, 3, 6);
		g.dispose();
		return img;
	}

	@Provides
	ClanInfoConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ClanInfoConfig.class);
	}
}
