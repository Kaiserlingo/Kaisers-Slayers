package at.kaiser.slayers.util;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Liest die Sidebar (Scoreboard rechts) von Hypixel aus. */
public final class ScoreboardReader {
	private ScoreboardReader() {}

	/** true, wenn die Sidebar "SKYBLOCK" im Titel trägt. */
	public static boolean isOnSkyBlock(Minecraft mc) {
		Objective obj = sidebar(mc);
		if (obj == null) return false;
		String title = TextUtil.cleanScoreboard(obj.getDisplayName().getString()).toUpperCase(java.util.Locale.ROOT);
		return title.contains("SKYBLOCK");
	}

	/** Bereinigte Zeilen von oben nach unten, oder null wenn keine Sidebar. */
	public static List<String> readLines(Minecraft mc) {
		Objective obj = sidebar(mc);
		if (obj == null) return null;
		Scoreboard board = mc.level.getScoreboard();

		List<PlayerScoreEntry> entries = new ArrayList<>();
		for (PlayerScoreEntry e : board.listPlayerScores(obj)) {
			if (!e.isHidden()) entries.add(e);
		}
		// Höchster Score steht oben
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());

		List<String> lines = new ArrayList<>(entries.size());
		for (PlayerScoreEntry e : entries) {
			PlayerTeam team = board.getPlayersTeam(e.owner());
			Component name = PlayerTeam.formatNameForTeam(team, Component.literal(e.owner()));
			String line = TextUtil.cleanScoreboard(name.getString());
			if (!line.isEmpty()) lines.add(line);
		}
		return lines;
	}

	private static Objective sidebar(Minecraft mc) {
		if (mc.level == null) return null;
		return mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
	}
}
