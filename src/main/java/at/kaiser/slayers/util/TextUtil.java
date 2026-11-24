package at.kaiser.slayers.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class TextUtil {
	private static final Pattern FORMATTING = Pattern.compile("§.");
	/** Hypixel streut unsichtbare Zeichen und Emojis in Scoreboard-Zeilen ein. */
	private static final Pattern SCOREBOARD_JUNK = Pattern.compile("[^\\x20-\\x7E✯]");

	private TextUtil() {}

	public static String stripFormatting(String s) {
		return s == null ? "" : FORMATTING.matcher(s).replaceAll("");
	}

	public static String cleanScoreboard(String s) {
		return SCOREBOARD_JUNK.matcher(stripFormatting(s)).replaceAll("").trim();
	}

	/** "1,234" / "1.5k" / "2M" -> Zahl */
	public static double parseNumber(String s) {
		if (s == null) return 0;
		String t = s.trim().replace(",", "").toLowerCase(Locale.ROOT);
		double mult = 1;
		if (t.endsWith("k")) { mult = 1_000; t = t.substring(0, t.length() - 1); }
		else if (t.endsWith("m")) { mult = 1_000_000; t = t.substring(0, t.length() - 1); }
		else if (t.endsWith("b")) { mult = 1_000_000_000; t = t.substring(0, t.length() - 1); }
		try {
			return Double.parseDouble(t) * mult;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	public static int romanToInt(String roman) {
		return switch (roman.trim().toUpperCase(Locale.ROOT)) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			case "V" -> 5;
			default -> 0;
		};
	}

	public static String toRoman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> "?";
		};
	}

	/** Millisekunden -> "1:05" bzw. "1:02:03" */
	public static String formatDuration(long ms) {
		if (ms < 0) ms = 0;
		long totalSec = ms / 1000;
		long h = totalSec / 3600, m = (totalSec % 3600) / 60, s = totalSec % 60;
		return h > 0 ? String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
				: String.format(Locale.ROOT, "%d:%02d", m, s);
	}

	/** Millisekunden mit einer Nachkommastelle -> "23.4s" */
	public static String formatSeconds(long ms) {
		return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
	}

	/** 1234567 -> "1.23M" */
	public static String formatCoins(double v) {
		double a = Math.abs(v);
		String sign = v < 0 ? "-" : "";
		if (a >= 1_000_000_000) return sign + String.format(Locale.ROOT, "%.2fB", a / 1_000_000_000);
		if (a >= 1_000_000) return sign + String.format(Locale.ROOT, "%.2fM", a / 1_000_000);
		if (a >= 1_000) return sign + String.format(Locale.ROOT, "%.1fk", a / 1_000);
		return sign + String.format(Locale.ROOT, "%.0f", a);
	}

	/** 1234567 -> "1,234,567" */
	public static String formatInt(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}
}
