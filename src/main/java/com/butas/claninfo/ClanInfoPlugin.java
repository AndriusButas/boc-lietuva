package com.butas.claninfo;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "BOC Lietuva",
	description = "How to join the BOC Lietuva clan, requirements and community links",
	tags = {"clan", "join", "info", "recruit", "discord"}
)
public class ClanInfoPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ClanInfoConfig config;

	private ClanInfoPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		panel = new ClanInfoPanel(config);

		BufferedImage icon = createIcon();
		navButton = NavigationButton.builder()
			.tooltip("BOC Lietuva")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (ClanInfoConfig.GROUP.equals(event.getGroup()) && panel != null)
		{
			SwingUtilities.invokeLater(panel::rebuild);
		}
	}

	/**
	 * Draws the small gold shield shown in the RuneLite sidebar.
	 */
	private static BufferedImage createIcon()
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
