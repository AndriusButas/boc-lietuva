package com.butas.claninfo;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

class ClanInfoPanel extends PluginPanel
{
	// Width for wrapped HTML text inside the side panel
	private static final int TEXT_WIDTH = PluginPanel.PANEL_WIDTH - 65;

	private final ClanInfoConfig config;
	private final JPanel content = new JPanel();

	ClanInfoPanel(ClanInfoConfig config)
	{
		super(false);
		this.config = config;

		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(content, BorderLayout.NORTH);

		rebuild();
	}

	/**
	 * Rebuilds the panel from current config. Must be called on the Swing thread.
	 */
	void rebuild()
	{
		content.removeAll();

		JLabel title = new JLabel(config.clanName());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		addRow(title);

		if (!isBlank(config.about()))
		{
			addRow(textLabel(escape(config.about())));
		}

		addSection("How to join");
		addRow(textLabel(numberedList(config.joinSteps())));

		if (!isBlank(config.requirements()))
		{
			addSection("Requirements");
			addRow(textLabel(bulletList(config.requirements())));
		}

		if (!isBlank(config.contacts()))
		{
			addSection("Ask for an invite");
			addRow(textLabel(bulletList(config.contacts().replace(',', '\n'))));
		}

		addSection("Links");
		addRow(button("Copy clan name", () ->
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(config.clanName()), null)));

		if (!isBlank(config.discordUrl()))
		{
			addRow(button("Join our Discord", () -> LinkBrowser.browse(config.discordUrl().trim())));
		}

		if (!isBlank(config.websiteUrl()))
		{
			String label = isBlank(config.websiteLabel()) ? "Website" : config.websiteLabel();
			addRow(button(label, () -> LinkBrowser.browse(config.websiteUrl().trim())));
		}

		content.revalidate();
		content.repaint();
	}

	private void addSection(String name)
	{
		content.add(Box.createVerticalStrut(12));
		JLabel header = new JLabel(name);
		header.setFont(FontManager.getRunescapeBoldFont());
		header.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			new EmptyBorder(0, 0, 3, 0)));
		addRow(header);
		content.add(Box.createVerticalStrut(4));
	}

	private void addRow(Component c)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(new EmptyBorder(2, 0, 2, 0));
		row.add(c, BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		content.add(row);
	}

	private static JLabel textLabel(String html)
	{
		JLabel label = new JLabel("<html><body style='width:" + TEXT_WIDTH + "px'>" + html + "</body></html>");
		label.setForeground(ColorScheme.TEXT_COLOR);
		label.setFont(FontManager.getRunescapeSmallFont());
		return label;
	}

	private static JButton button(String text, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.addActionListener(e -> action.run());
		return b;
	}

	private static String numberedList(String text)
	{
		StringBuilder sb = new StringBuilder();
		int n = 1;
		for (String line : lines(text))
		{
			sb.append(n++).append(". ").append(escape(line)).append("<br>");
		}
		return sb.toString();
	}

	private static String bulletList(String text)
	{
		StringBuilder sb = new StringBuilder();
		for (String line : lines(text))
		{
			sb.append("• ").append(escape(line)).append("<br>");
		}
		return sb.toString();
	}

	private static String[] lines(String text)
	{
		return text == null ? new String[0] : java.util.Arrays.stream(text.split("\\r?\\n"))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.toArray(String[]::new);
	}

	private static boolean isBlank(String s)
	{
		return s == null || s.trim().isEmpty();
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
