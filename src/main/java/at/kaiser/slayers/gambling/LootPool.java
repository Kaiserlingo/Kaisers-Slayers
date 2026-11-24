package at.kaiser.slayers.gambling;

import at.kaiser.slayers.slayer.SlayerType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Items, die in der Gambling-Walze vorbeilaufen. Stufe (tier): 0 = Common, 1 = RARE, 2 = VERY RARE,
 * 3 = CRAZY RARE, 4 = INSANE, 5 = PRAY TO RNGESUS. Eigene Items kommen über die Config dazu.
 */
public final class LootPool {
	public record Entry(String name, int tier) {}

	private static final Map<SlayerType, List<Entry>> BUILT_IN = new LinkedHashMap<>();

	static {
		BUILT_IN.put(SlayerType.ZOMBIE, List.of(
				e("Revenant Flesh", 0), e("Foul Flesh", 1), e("Pestilence Rune", 1), e("Undead Catalyst", 1),
				e("Smite VI", 2), e("Beheaded Horror", 2), e("Revenant Catalyst", 2), e("Snake Rune", 3),
				e("Scythe Blade", 4), e("Shard of the Shredded", 4), e("Warden Heart", 5)));
		BUILT_IN.put(SlayerType.SPIDER, List.of(
				e("Tarantula Web", 0), e("Toxic Arrow Poison", 1), e("Bite Rune", 1), e("Spider Catalyst", 2),
				e("Bane of Arthropods VI", 2), e("Fly Swatter", 3), e("Tarantula Talisman", 3),
				e("Digested Mosquito", 4), e("Shriveled Wasp", 4), e("Ensnared Snail", 4), e("Primordial Eye", 5)));
		BUILT_IN.put(SlayerType.WOLF, List.of(
				e("Wolf Tooth", 0), e("Hamster Wheel", 1), e("Spirit Rune", 1), e("Critical VI", 2),
				e("Grizzly Bait", 2), e("Red Claw Egg", 3), e("Couture Rune", 3), e("Overflux Capacitor", 4)));
		BUILT_IN.put(SlayerType.ENDERMAN, List.of(
				e("Null Sphere", 0), e("Twilight Arrow Poison", 1), e("Endersnake Rune", 1), e("Summoning Eye", 2),
				e("Mana Steal I", 2), e("Transmission Tuner", 2), e("Null Atom", 2), e("Hazmat Enderman", 3),
				e("Pocket Espresso Machine", 3), e("Smarty Pants I", 3), e("End Rune", 3), e("Handy Blood Chalice", 3),
				e("Sinful Dice", 3), e("Etherwarp Merger", 3), e("Judgement Core", 4), e("Ender Slayer VII", 4),
				e("Enchant Rune", 4), e("Exceedingly Rare Ender Artifact Upgrader", 5)));
		BUILT_IN.put(SlayerType.BLAZE, List.of(
				e("Derelict Ashe", 0), e("Enchanted Blaze Powder", 1), e("Lavatears Rune", 1), e("Bundle of Magma Arrows", 1),
				e("Mana Disintegrator", 2), e("Scorched Books", 2), e("Kelvin Inverter", 2), e("Blaze Rod Distillate", 2),
				e("Glowstone Distillate", 2), e("Magma Cream Distillate", 2), e("Nether Wart Distillate", 2),
				e("Gabagool Distillate", 2), e("Archfiend Dice", 3), e("Fire Aspect III", 3), e("Fiery Burst Rune", 3),
				e("Duplex I", 4), e("High Class Archfiend Dice", 5)));
		BUILT_IN.put(SlayerType.VAMPIRE, List.of(
				e("Motes", 0), e("Coven Seal", 1), e("McGrubber's Burger", 2), e("Unfanged Vampire Part", 3), e("The One IV", 4)));
	}

	private LootPool() {}

	private static Entry e(String name, int tier) {
		return new Entry(name, tier);
	}

	/**
	 * @param extra eigene Einträge "Name|Stufe"
	 * @param ownDrops eigene Drops (Name -> Anzahl) aus der Statistik, oder null
	 */
	public static List<Entry> build(SlayerType type, List<String> extra, Map<String, Integer> ownDrops) {
		List<Entry> out = new ArrayList<>(BUILT_IN.getOrDefault(type, List.of()));
		if (extra != null) {
			for (String s : extra) {
				Entry parsed = parse(s);
				if (parsed != null && !contains(out, parsed.name())) out.add(parsed);
			}
		}
		if (ownDrops != null) {
			for (String name : ownDrops.keySet()) {
				if (!contains(out, name)) out.add(new Entry(name, 1));
			}
		}
		if (out.isEmpty()) out.add(new Entry("???", 0));
		return out;
	}

	public static Entry parse(String s) {
		if (s == null || s.isBlank()) return null;
		String[] parts = s.split("\\|");
		String name = parts[0].trim();
		int tier = 1;
		if (parts.length > 1) {
			try {
				tier = Math.max(0, Math.min(5, Integer.parseInt(parts[1].trim())));
			} catch (NumberFormatException ignored) {
			}
		}
		return name.isEmpty() ? null : new Entry(name, tier);
	}

	public static int tierOf(List<Entry> pool, String name, int fallback) {
		for (Entry e : pool) if (e.name().equalsIgnoreCase(name)) return e.tier();
		return fallback;
	}

	public static Entry common(SlayerType type) {
		List<Entry> l = BUILT_IN.get(type);
		return l != null && !l.isEmpty() ? l.get(0) : new Entry("Common Drop", 0);
	}

	private static boolean contains(List<Entry> list, String name) {
		for (Entry e : list) if (e.name().equalsIgnoreCase(name)) return true;
		return false;
	}

	/** Rahmenfarbe (ARGB) je Stufe */
	public static int tierColor(int tier) {
		return switch (tier) {
			case 0 -> 0xFFAAAAAA;
			case 1 -> 0xFF5555FF;
			case 2 -> 0xFFAA00AA;
			case 3 -> 0xFFFF55FF;
			case 4 -> 0xFFFF5555;
			default -> 0xFFFFAA00;
		};
	}

	public static String tierName(int tier) {
		return switch (tier) {
			case 0 -> "COMMON";
			case 1 -> "RARE DROP";
			case 2 -> "VERY RARE DROP";
			case 3 -> "CRAZY RARE DROP";
			case 4 -> "INSANE DROP";
			default -> "PRAY TO RNGESUS";
		};
	}
}
