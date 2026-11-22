package com.butas.claninfo;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

class ClanInfoPanel extends PluginPanel
{
	// Width for wrapped HTML text inside the side panel
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 72;

	private static final String RED = "#ff4b4b";
	private static final String GREEN = "#3cc86e";
	private static final String ORANGE = "#ff981f";
	private static final String AUTHOR_DISCORD = "https://discord.com/users/1001209523685564466";

	private static final String S_LIVE = "Vyksta dabar";
	private static final String S_UPCOMING = "Artėjantys renginiai";
	private static final String S_STREAMERS = "Streameriai";
	private static final String S_MY_RANK = "Mano rank'as";
	private static final String S_JOIN = "Kaip įstoti";
	private static final String S_PLUGINS = "Privalomi pluginai";
	private static final String S_RANKS = "Rank'ai";
	private static final String S_CHECKED = "Kaip tikrinama";
	private static final String S_BOT = "Discord botas";

	private final PanelData data;
	private final JPanel content = new JPanel();
	// Remembers which sections the player opened/closed while the panel is rebuilt
	private final Map<String, Boolean> expanded = new HashMap<>();

	ClanInfoPanel(PanelData data)
	{
		super(false);
		this.data = data;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		// The whole panel scrolls, since it can get long
		JScrollPane scroll = new JScrollPane(wrapTop(content));
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(scroll, BorderLayout.CENTER);

		rebuild();
	}

	/**
	 * Rebuilds the panel from the current data. Must be called on the Swing thread.
	 */
	void rebuild()
	{
		ClanContent c = data.content();
		PanelData.PlayerInfo player = data.player();
		boolean member = player != null && player.isMember();

		content.removeAll();

		JLabel title = new JLabel(c.clanName);
		title.setFont(titleFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		addTo(content, title);

		if (member)
		{
			addTo(content, textLabel("Sveikas, <b>" + escape(player.getName()) + "</b>!"));
		}
		else if (!isBlank(c.about))
		{
			addTo(content, textLabel(escape(c.about)));
		}

		content.add(Box.createVerticalStrut(6));
		addButtons(c);

		if (member)
		{
			// Members: what's happening in the clan first, joining info at the bottom
			addEventSections(true);
			section(S_STREAMERS, true, body -> fillStreamers(body, c));
			section(S_MY_RANK, true, body -> fillMyRank(body, c, player));
			section(S_PLUGINS, !allPluginsOk(c), body -> fillPlugins(body, c));
			section(S_RANKS, false, body -> fillRanks(body, c));
			section(S_CHECKED, false, body -> addTo(body, textLabel(bulletList(c.howChecked))));
			section(S_BOT, false, body -> addTo(body, textLabel(bulletList(c.bot))));
			section(S_JOIN, false, body -> fillJoin(body, c));
		}
		else
		{
			// Guests: how to join first
			section(S_JOIN, true, body -> fillJoin(body, c));
			section(S_PLUGINS, true, body -> fillPlugins(body, c));
			addEventSections(true);
			section(S_STREAMERS, true, body -> fillStreamers(body, c));
			section(S_RANKS, false, body -> fillRanks(body, c));
			section(S_CHECKED, false, body -> addTo(body, textLabel(bulletList(c.howChecked))));
			section(S_BOT, false, body -> addTo(body, textLabel(bulletList(c.bot))));
		}

		// Credits: the author's name opens his Discord profile
		content.add(Box.createVerticalStrut(16));
		JLabel credits = new JLabel("<html>BOC Lietuva pluginą sukūrė <b><u>Etiopijus</u></b></html>");
		credits.setFont(bodyFont().deriveFont(11f));
		credits.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
		credits.setToolTipText("Atidaryti Etiopijus Discord profilį");
		credits.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		credits.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(8, 0, 0, 0)));
		credits.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(AUTHOR_DISCORD);
			}
		});
		addTo(content, credits);

		content.revalidate();
		content.repaint();
	}

	private void addButtons(ClanContent c)
	{
		addTo(content, button("Kopijuoti klano pavadinimą", () ->
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(c.clanName), null)));

		if (!isBlank(c.discordUrl))
		{
			addTo(content, button("Prisijungti prie Discord", () -> LinkBrowser.browse(c.discordUrl.trim())));
		}

		if (!isBlank(c.streamUrl))
		{
			String url = c.streamUrl.trim();
			String label = isBlank(c.streamLabel) ? "Žiūrėti streamą" : c.streamLabel;
			LiveStatus status = liveStatusFor(c, url);
			if (status != null)
			{
				addTo(content, button("<html><b><font color='" + RED + "'>● LIVE</font></b>&nbsp; "
					+ escape(label) + "</html>", () -> LinkBrowser.browse(status.watchUrl())));
			}
			else
			{
				addTo(content, button(label, () -> LinkBrowser.browse(url)));
			}
		}
	}

	// ---- Sections ----

	private void fillJoin(JPanel body, ClanContent c)
	{
		addTo(body, textLabel(numberedList(c.joinSteps)));
		if (!isBlank(c.contacts))
		{
			addTo(body, textLabel("<b>Dėl pakvietimo rašyk:</b> " + escape(c.contacts)));
		}
	}

	private void fillPlugins(JPanel body, ClanContent c)
	{
		StringBuilder sb = new StringBuilder();
		for (ClanContent.RequiredPlugin p : c.requiredPlugins)
		{
			PanelData.PluginStatus status = data.pluginStatus(p);
			switch (status)
			{
				case OK:
					sb.append("<font color='").append(GREEN).append("'>✔</font> ").append(escape(p.name));
					break;
				case DISABLED:
					sb.append("<font color='").append(ORANGE).append("'>!</font> ").append(escape(p.name))
						.append(" – <font color='").append(ORANGE).append("'>išjungtas, įjunk jį</font>");
					break;
				case SETTING_OFF:
					sb.append("<font color='").append(ORANGE).append("'>!</font> ").append(escape(p.name))
						.append(" – <font color='").append(ORANGE).append("'>")
						.append(escape(p.configHint == null ? "patikrink nustatymus" : p.configHint)).append("</font>");
					break;
				default:
					sb.append("<font color='").append(RED).append("'>✘</font> ").append(escape(p.name))
						.append(" – <font color='").append(RED).append("'>neįdiegtas (Plugin Hub)</font>");
			}
			sb.append("<br>");
		}
		addTo(body, textLabel(sb.toString()));
		if (!isBlank(c.requiredPluginsNote))
		{
			addTo(body, mutedLabel(escape(c.requiredPluginsNote)));
		}
	}

	private boolean allPluginsOk(ClanContent c)
	{
		for (ClanContent.RequiredPlugin p : c.requiredPlugins)
		{
			if (data.pluginStatus(p) != PanelData.PluginStatus.OK)
			{
				return false;
			}
		}
		return true;
	}

	private void fillStreamers(JPanel body, ClanContent c)
	{
		for (ClanContent.Streamer streamer : c.streamers)
		{
			LiveStatus status = data.live(streamer.name);
			String name = "<b>" + escape(streamer.name) + "</b>" + (isBlank(streamer.role) ? "" : " – " + escape(streamer.role));
			if (status != null)
			{
				addTo(body, textLabel("<b><font color='" + RED + "'>● LIVE</font></b>&nbsp; " + name));
				if (!status.getTitle().isEmpty())
				{
					addTo(body, mutedLabel(escape(status.getTitle())));
				}
				addTo(body, button("<html><font color='" + RED + "'>●</font>&nbsp; Žiūrėti gyvai</html>",
					() -> LinkBrowser.browse(status.watchUrl())));
			}
			else
			{
				addTo(body, textLabel(name));
				addTo(body, button("▶  Žiūrėti YouTube", () -> LinkBrowser.browse(streamer.url)));
			}
		}
	}

	private void fillRanks(JPanel body, ClanContent c)
	{
		if (!isBlank(c.ranksIntro))
		{
			addTo(body, mutedLabel(escape(c.ranksIntro)));
		}
		for (ClanContent.Tier tier : c.tiers)
		{
			body.add(Box.createVerticalStrut(8));
			JLabel tierTitle = new JLabel(tier.title);
			tierTitle.setFont(boldFont());
			tierTitle.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			addTo(body, tierTitle);
			for (ClanContent.Rank rank : tier.ranks)
			{
				addTo(body, textLabel(rankName(rank) + "&nbsp;– " + escape(rank.describe())));
			}
			if (!isBlank(tier.note))
			{
				addTo(body, mutedLabel(escape(tier.note)));
			}
		}
	}

	/**
	 * The member's current rank and a checklist for the next one.
	 * Total level, skills and time in the clan come from the game; the rest is checked by the Discord bot.
	 */
	private void fillMyRank(JPanel body, ClanContent c, PanelData.PlayerInfo player)
	{
		ClanContent.Rank current = c.findRank(player.getRankTitle());
		StringBuilder sb = new StringBuilder("Tavo rank'as: ");
		sb.append(current != null ? rankName(current) : "<b>" + escape(String.valueOf(player.getRankTitle())) + "</b>");

		int monthsInClan = -1;
		if (player.getJoinDate() != null)
		{
			Period p = Period.between(player.getJoinDate(), LocalDate.now(ClanEvents.CLAN_ZONE));
			monthsInClan = p.getYears() * 12 + p.getMonths();
			sb.append("<br>BOC narys nuo ").append(player.getJoinDate()).append(" (").append(monthsInClan).append(" mėn.)");
		}
		addTo(body, textLabel(sb.toString()));

		if (current == null)
		{
			addTo(body, mutedLabel("Šio rank'o BOC kopėčiose nėra – žr. „Rank'ai“."));
			return;
		}

		ClanContent.Rank next = c.nextRank(current);
		if (next == null)
		{
			addTo(body, textLabel("<b><font color='" + GREEN + "'>Pasiekei aukščiausią BOC rank'ą!</font></b>"));
			return;
		}

		StringBuilder checks = new StringBuilder("Kitas rank'as: ").append(rankName(next)).append("<br>");
		if (next.total != null)
		{
			checks.append(check(player.getTotalLevel() >= next.total,
				next.total + " total", "tu: " + player.getTotalLevel()));
		}
		if (next.skills != null)
		{
			for (Map.Entry<String, Integer> s : next.skills.entrySet())
			{
				Integer have = player.getSkillLevels().get(s.getKey().toLowerCase());
				int level = have == null ? 0 : have;
				checks.append(check(level >= s.getValue(),
					s.getValue() + " " + ClanContent.skillName(s.getKey()), "tu: " + level));
			}
		}
		if (next.months != null && monthsInClan >= 0)
		{
			checks.append(check(monthsInClan >= next.months,
				next.months + " mėn. BOC", "tu: " + monthsInClan));
		}
		for (String other : next.other())
		{
			checks.append("<font color='#9a9a9a'>•</font> ").append(escape(other)).append("<br>");
		}
		addTo(body, textLabel(checks.toString()));
		if (!next.other().isEmpty())
		{
			addTo(body, mutedLabel("Pažymėtų • pluginas nemato – juos patikrina botas: rašyk !rankup Discord'e."));
		}
	}

	private static String check(boolean ok, String requirement, String have)
	{
		return ok
			? "<font color='" + GREEN + "'>✔</font> " + escape(requirement) + "<br>"
			: "<font color='" + RED + "'>✘</font> " + escape(requirement)
				+ " <font color='" + RED + "'>(" + escape(have) + ")</font><br>";
	}

	private void addEventSections(boolean open)
	{
		ClanEvents.State state = data.events();
		ZonedDateTime now = ZonedDateTime.now(ClanEvents.CLAN_ZONE);
		List<ClanEvents.Event> liveEvents = ClanEvents.live(state.getEvents(), now);
		List<ClanEvents.Event> upcoming = ClanEvents.upcoming(state.getEvents(), now);

		String status = !state.isLoaded()
			? (state.isFailed() ? "Nepavyko gauti renginių. Bandysime vėl po kelių minučių." : "Kraunami renginiai...")
			: null;

		section(S_LIVE, open, body ->
		{
			if (status != null)
			{
				addTo(body, mutedLabel(status));
				return;
			}
			if (liveEvents.isEmpty())
			{
				addTo(body, mutedLabel("Šiuo metu renginių nevyksta."));
			}
			boolean first = true;
			for (ClanEvents.Event e : liveEvents)
			{
				if (!first)
				{
					body.add(Box.createVerticalStrut(6));
				}
				first = false;
				addTo(body, textLabel("<b><font color='" + RED + "'>●</font>&nbsp;" + escape(e.getName()) + "</b><br>"
					+ escape(ClanEvents.formatDates(e))
					+ "&nbsp;·&nbsp;<font color='" + RED + "'>liko " + ClanEvents.formatRemaining(now, e.getEnd()) + "</font>"
					+ (e.getDescription().isEmpty() ? "" : "<br><i>" + escape(e.getDescription()) + "</i>")));
				if (e.getWomId() != null)
				{
					addLeaderboard(body, e, state.getLeaderboards().get(e.getWomId()));
				}
			}
		});

		section(S_UPCOMING, open, body ->
		{
			if (status != null)
			{
				addTo(body, mutedLabel(status));
				return;
			}
			if (upcoming.isEmpty())
			{
				addTo(body, mutedLabel("Artėjančių renginių dar nepaskelbta."));
			}
			for (ClanEvents.Event e : upcoming)
			{
				String when = escape(ClanEvents.formatDates(e))
					+ (e.getStart() == null ? "" : " · po " + ClanEvents.formatRemaining(now, e.getStart()));
				addTo(body, textLabel("<b>" + escape(e.getName()) + "</b><br>" + when
					+ (e.getDescription().isEmpty() ? "" : "<br><i>" + escape(e.getDescription()) + "</i>")));
			}
		});
	}

	/**
	 * Top 3 of a Wise Old Man competition, the player's own place, and a link to the full results.
	 */
	private void addLeaderboard(JPanel body, ClanEvents.Event e, List<ClanEvents.Entry> entries)
	{
		if (entries == null)
		{
			addTo(body, mutedLabel("Kraunami rezultatai..."));
		}
		else
		{
			StringBuilder sb = new StringBuilder();
			String[] medals = {"#d4af37", "#c0c0c0", "#cd7f32"};
			for (int i = 0; i < Math.min(3, entries.size()); i++)
			{
				ClanEvents.Entry entry = entries.get(i);
				sb.append("<font color='").append(medals[i]).append("'><b>").append(i + 1).append(".</b></font>&nbsp;")
					.append(escape(entry.getName())).append(" – ")
					.append(ClanEvents.formatGained(e.getMetric(), entry.getGained())).append("<br>");
			}

			PanelData.PlayerInfo player = data.player();
			String me = ClanEvents.normalizeName(player == null ? null : player.getName());
			if (!me.isEmpty())
			{
				int place = -1;
				for (int i = 0; i < entries.size(); i++)
				{
					if (ClanEvents.normalizeName(entries.get(i).getName()).equals(me))
					{
						place = i;
						break;
					}
				}
				if (place >= 0 && entries.get(place).getGained() <= 0)
				{
					sb.append("<i>Tu dar nepradėjai – pirmyn!</i>");
				}
				else if (place >= 0)
				{
					sb.append("<b><font color='").append(ORANGE).append("'>Tu: ").append(place + 1)
						.append(" vieta</font></b> iš ").append(entries.size()).append(" – ")
						.append(ClanEvents.formatGained(e.getMetric(), entries.get(place).getGained()));
				}
				else
				{
					sb.append("<i>Tu šiose varžybose nedalyvauji.</i>");
				}
			}
			addTo(body, textLabel(sb.toString()));
		}
		addTo(body, button("Visi rezultatai (Wise Old Man)",
			() -> LinkBrowser.browse(ClanEvents.WOM_COMPETITION_PAGE + e.getWomId())));
	}

	/**
	 * If the link points to a clan streamer's channel and they are live, returns their stream.
	 */
	private LiveStatus liveStatusFor(ClanContent c, String url)
	{
		String normalized = url.replaceAll("/+$", "").toLowerCase();
		for (ClanContent.Streamer streamer : c.streamers)
		{
			if (streamer.url != null && streamer.url.replaceAll("/+$", "").toLowerCase().equals(normalized))
			{
				return data.live(streamer.name);
			}
		}
		return null;
	}

	// ---- Building blocks ----

	/**
	 * Adds a section with a clickable header that shows/hides its body.
	 */
	private void section(String name, boolean openByDefault, Consumer<JPanel> fill)
	{
		boolean open = expanded.getOrDefault(name, openByDefault);

		content.add(Box.createVerticalStrut(10));

		JLabel header = new JLabel((open ? "▼  " : "▶  ") + name);
		header.setFont(boldFont());
		header.setForeground(ColorScheme.BRAND_ORANGE);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 4, 0)));
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				expanded.put(name, !open);
				rebuild();
			}
		});
		addTo(content, header);

		if (open)
		{
			JPanel body = new JPanel();
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			body.setBackground(ColorScheme.DARK_GRAY_COLOR);
			body.setBorder(new EmptyBorder(4, 0, 0, 0));
			fill.accept(body);
			addTo(content, body);
		}
	}

	private static String rankName(ClanContent.Rank rank)
	{
		java.awt.Color col = rank.color();
		return "<b><font color='" + String.format("#%02x%02x%02x", col.getRed(), col.getGreen(), col.getBlue()) + "'>"
			+ escape(rank.name) + "</font></b>";
	}

	private static void addTo(JPanel parent, Component c)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.add(c, BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		parent.add(row);
	}

	private static JPanel wrapTop(JPanel inner)
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapper.add(inner, BorderLayout.NORTH);
		return wrapper;
	}

	private static JLabel textLabel(String html)
	{
		JLabel label = new JLabel("<html><body style='width:" + TEXT_WIDTH + "px'>" + html + "</body></html>");
		label.setForeground(ColorScheme.TEXT_COLOR);
		label.setFont(bodyFont());
		return label;
	}

	private static JLabel mutedLabel(String html)
	{
		JLabel label = textLabel("<i>" + html + "</i>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());
		return label;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFont(bodyFont());
		b.setFocusPainted(false);
		b.addActionListener(e -> action.run());
		return b;
	}

	// RuneLite's RuneScape-style fonts have no Lithuanian letters (ą, č, ę...),
	// so the panel uses RuneLite's regular UI font, which does.
	private static Font bodyFont()
	{
		return FontManager.getDefaultFont().deriveFont(12f);
	}

	private static Font boldFont()
	{
		return FontManager.getDefaultBoldFont().deriveFont(12f);
	}

	private static Font titleFont()
	{
		return FontManager.getDefaultBoldFont().deriveFont(15f);
	}

	private static String numberedList(List<String> items)
	{
		StringBuilder sb = new StringBuilder();
		int n = 1;
		for (String line : items)
		{
			sb.append(n++).append(". ").append(escape(line)).append("<br>");
		}
		return sb.toString();
	}

	private static String bulletList(List<String> items)
	{
		StringBuilder sb = new StringBuilder();
		for (String line : items)
		{
			sb.append("• ").append(escape(line)).append("<br>");
		}
		return sb.toString();
	}

	private static boolean isBlank(String s)
	{
		return s == null || s.trim().isEmpty();
	}

	private static String escape(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
