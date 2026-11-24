package at.kaiser.slayers.world;

import at.kaiser.slayers.util.TextUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Liest Infos aus den Namensschildern (Armor Stands) von Hypixel. Ohne Minecraft-Abhängigkeit → testbar. */
public final class NameParser {
	private static final Pattern HP = Pattern.compile("([\\d.,]+[kKmMbB]?)(?:/[\\d.,]+[kKmMbB]?)?\\s*❤");
	private static final Pattern HITS = Pattern.compile("(\\d+)\\s*Hits?\\b");
	private static final Pattern ATTUNEMENT = Pattern.compile("\\b(ASHEN|SPIRIT|CRYSTAL|AURIC|IMMUNE)\\b");
	private static final Pattern TIMER = Pattern.compile("^(\\d{1,2}):(\\d{2})$");
	private static final Pattern PILLAR = Pattern.compile("(\\d+)s\\s+(\\d+)\\s*hits?", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPAWNED_BY = Pattern.compile("^Spawned by:\\s*(\\S+)");

	private NameParser() {}

	public static String clean(String raw) {
		return TextUtil.stripFormatting(raw).trim();
	}

	/** HP aus "☠ Revenant Horror 12.3M❤" -> 12300000, sonst -1 */
	public static double hp(String name) {
		Matcher m = HP.matcher(name);
		return m.find() ? TextUtil.parseNumber(m.group(1)) : -1;
	}

	/** Hits aus "Voidgloom Seraph 45 Hits" -> 45, sonst -1 */
	public static int hits(String name) {
		Matcher m = HITS.matcher(name);
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	/** ASHEN / SPIRIT / CRYSTAL / AURIC oder null */
	public static String attunement(String name) {
		Matcher m = ATTUNEMENT.matcher(name.toUpperCase(java.util.Locale.ROOT));
		return m.find() ? m.group(1) : null;
	}

	/** "02:41" -> 161 Sekunden, sonst -1 */
	public static int timerSeconds(String name) {
		Matcher m = TIMER.matcher(name.trim());
		return m.matches() ? Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)) : -1;
	}

	/** Fire Pillar "7s 8 hits" -> {7, 8}, sonst null */
	public static int[] pillar(String name) {
		Matcher m = PILLAR.matcher(name);
		return m.find() ? new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
	}

	/** Gehört dieses Namensschild zu einem Boss, den ich gespawnt habe? */
	/** "name's slayer boss" vorhanden und davor kein weiterer Namens-Buchstabe (sonst wäre es ein anderer Spieler) */
	private static boolean ownsBoss(String low, String needle) {
		int i = low.indexOf(needle);
		while (i >= 0) {
			if (i == 0 || !(Character.isLetterOrDigit(low.charAt(i - 1)) || low.charAt(i - 1) == '_')) return true;
			i = low.indexOf(needle, i + 1);
		}
		return false;
	}

	/** Ist das ein Besitzer-Schild eines Slayer-Bosses ("Spawned by: X" oder "X's SLAYER BOSS")? */
	public static boolean isOwnerMarker(String name) {
		String low = name.toLowerCase(java.util.Locale.ROOT);
		return low.contains("spawned by") || low.contains("'s slayer boss") || low.contains("’s slayer boss");
	}

	public static boolean isOwnerLabel(String name, String... myNames) {
		String low = name.toLowerCase(java.util.Locale.ROOT);
		boolean spawnedBy = low.contains("spawned by");
		for (String n : myNames) {
			if (n == null || n.isEmpty()) continue;
			String me = n.toLowerCase(java.util.Locale.ROOT);
			// Hypixel: "Spawned by: Name" (alt) oder "Name's SLAYER BOSS" (aktuell)
			if (spawnedBy && low.contains(me)) return true;
			if (ownsBoss(low, me + "'s slayer boss") || ownsBoss(low, me + "’s slayer boss")) return true;
		}
		return false;
	}

	/** Vampire: Boss kann gestaked werden */
	public static boolean isSteakReady(String name) {
		return name.contains("҉");
	}

	/** "Spawned by: Kaiser" -> "Kaiser", sonst null */
	public static String spawnedBy(String name) {
		Matcher m = SPAWNED_BY.matcher(name);
		return m.find() ? m.group(1) : null;
	}

	/** Welche Dagger-Familie passt zur Attunement? */
	public static String daggerFor(String attunement) {
		if (attunement == null) return null;
		return switch (attunement) {
			case "ASHEN", "AURIC" -> "Firedust / Kindlebane / Pyrochaos";
			case "SPIRIT", "CRYSTAL" -> "Twilight / Mawdredge / Deathripper";
			default -> null;
		};
	}

	/** true, wenn der gehaltene Itemname zur Attunement passt. */
	public static boolean holdsRightDagger(String heldName, String attunement) {
		if (heldName == null || attunement == null) return false;
		String h = heldName.toLowerCase(java.util.Locale.ROOT);
		boolean fireFamily = h.contains("firedust") || h.contains("kindlebane") || h.contains("pyrochaos");
		boolean twilightFamily = h.contains("twilight") || h.contains("mawdredge") || h.contains("deathripper");
		return switch (attunement) {
			case "ASHEN", "AURIC" -> fireFamily;
			case "SPIRIT", "CRYSTAL" -> twilightFamily;
			default -> false;
		};
	}

	/** Farbe (RGB) je Attunement */
	public static int attunementColor(String attunement) {
		if (attunement == null) return -1;
		return switch (attunement) {
			case "ASHEN" -> 0x555555;
			case "AURIC" -> 0xFFFF55;
			case "SPIRIT" -> 0xFFFFFF;
			case "CRYSTAL" -> 0x55FFFF;
			case "IMMUNE" -> 0xFF3333;
			default -> -1;
		};
	}

	public static String attunementColorCode(String attunement) {
		if (attunement == null) return "§7";
		return switch (attunement) {
			case "ASHEN" -> "§8";
			case "AURIC" -> "§e";
			case "SPIRIT" -> "§f";
			case "CRYSTAL" -> "§b";
			case "IMMUNE" -> "§c";
			default -> "§7";
		};
	}

	/** Blaze-Demons haben Namen in umkreisten Buchstaben. */
	public static boolean isBlazeDemon(String name) {
		return name.contains("ⓉⓎⓅⒽⓄⒺⓊⓈ") || name.contains("ⓆⓊⒶⓏⒾⒾ")
				|| name.contains("Typhoeus") || name.contains("Quazii");
	}
}
