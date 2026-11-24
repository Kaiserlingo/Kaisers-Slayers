package at.kaiser.slayers.slayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alle Slayer in Hypixel SkyBlock: Boss-Namen (auch T5-Varianten), Mob-Typ, Kosten, XP, Boss-HP und Minibosse je Tier.
 * Kosten = Coins (bzw. Motes beim Vampire Slayer), Index 0 = Tier I.
 */
public enum SlayerType {
	ZOMBIE("Revenant Horror", List.of("Revenant Horror", "Atoned Horror"), "Zombie", "zombie", "§2", 0x55FF55,
			new long[]{2_000, 7_500, 20_000, 50_000, 100_000},
			new int[]{5, 25, 100, 500, 1_500},
			new long[]{500, 20_000, 400_000, 1_500_000, 10_000_000}, false,
			List.of("Revenant Sycophant"), List.of("Revenant Champion", "Deformed Revenant"), List.of("Atoned Champion", "Atoned Revenant")),
	SPIDER("Tarantula Broodfather", List.of("Tarantula Broodfather", "Conjoined Brood"), "Spider", "spider", "§4", 0xFF5555,
			new long[]{2_000, 7_500, 20_000, 50_000, 100_000},
			new int[]{5, 25, 100, 500, 1_500},
			new long[]{750, 30_000, 900_000, 2_400_000, 10_000_000}, false,
			List.of("Tarantula Vermin"), List.of("Tarantula Beast", "Mutant Tarantula"), List.of("Primordial Jockey", "Primordial Viscount")),
	WOLF("Sven Packmaster", List.of("Sven Packmaster"), "Wolf", "wolf", "§f", 0xFFFFFF,
			new long[]{2_000, 7_500, 20_000, 50_000},
			new int[]{5, 25, 100, 500},
			new long[]{2_000, 40_000, 750_000, 2_000_000}, false,
			List.of("Pack Enforcer"), List.of("Sven Follower", "Sven Alpha"), List.of()),
	ENDERMAN("Voidgloom Seraph", List.of("Voidgloom Seraph"), "Enderman", "enderman", "§5", 0xAA00AA,
			new long[]{2_000, 7_500, 20_000, 50_000},
			new int[]{5, 25, 100, 500},
			new long[]{300_000, 12_000_000, 50_000_000, 210_000_000}, false,
			List.of("Voidling Devotee"), List.of("Voidling Radical", "Voidcrazed Maniac"), List.of()),
	BLAZE("Inferno Demonlord", List.of("Inferno Demonlord"), "Blaze", "blaze", "§6", 0xFFAA00,
			new long[]{10_000, 25_000, 60_000, 150_000},
			new int[]{5, 25, 100, 500},
			new long[]{2_500_000, 10_000_000, 45_000_000, 150_000_000}, false,
			List.of("Flare Demon"), List.of("Kindleheart Demon", "Burningsoul Demon"), List.of()),
	VAMPIRE("Riftstalker Bloodfiend", List.of("Riftstalker Bloodfiend", "Bloodfiend"), "Vampire", "player", "§c", 0xFF5555,
			new long[]{2_000, 4_000, 6_000, 10_000, 20_000},
			new int[]{10, 25, 60, 120, 150},
			new long[]{625, 1_100, 1_800, 2_400, 3_000}, true,
			List.of(), List.of(), List.of());

	public final String bossName;
	/** Alle Namen, unter denen der Boss im Spiel auftaucht (z. B. T5 "Atoned Horror"). */
	public final List<String> bossNames;
	public final String levelName;
	/** Minecraft-Entity-ID des Boss-Mobs, z. B. "zombie" */
	public final String mobId;
	public final String color;
	public final int defaultGlow;
	private final long[] costs;
	private final int[] xp;
	private final long[] maxHp;
	public final boolean motes;
	private final List<String> t3Minis, t4Minis, t5Minis;

	SlayerType(String bossName, List<String> bossNames, String levelName, String mobId, String color, int defaultGlow,
			   long[] costs, int[] xp, long[] maxHp, boolean motes, List<String> t3, List<String> t4, List<String> t5) {
		this.bossName = bossName;
		this.bossNames = bossNames;
		this.levelName = levelName;
		this.mobId = mobId;
		this.color = color;
		this.defaultGlow = defaultGlow;
		this.costs = costs;
		this.xp = xp;
		this.maxHp = maxHp;
		this.motes = motes;
		this.t3Minis = t3;
		this.t4Minis = t4;
		this.t5Minis = t5;
	}

	public long cost(int tier) {
		return tier >= 1 && tier <= costs.length ? costs[tier - 1] : 0;
	}

	public int xpPerBoss(int tier) {
		return tier >= 1 && tier <= xp.length ? xp[tier - 1] : 0;
	}

	/** Maximale Boss-HP für ein Tier (für die HP-Anzeige), oder -1 */
	public long maxHp(int tier) {
		return tier >= 1 && tier <= maxHp.length ? maxHp[tier - 1] : -1;
	}

	public int maxTier() {
		return costs.length;
	}

	/** Alle Minibosse dieses Slayers (unabhängig vom Tier). */
	public List<String> minibosses() {
		List<String> all = new ArrayList<>(t3Minis);
		all.addAll(t4Minis);
		all.addAll(t5Minis);
		return all;
	}

	/** Minibosse, die bei diesem Tier spawnen können. */
	public List<String> minibosses(int tier) {
		List<String> out = new ArrayList<>();
		if (tier >= 3) out.addAll(t3Minis);
		if (tier >= 4) out.addAll(t4Minis);
		if (tier >= 5) out.addAll(t5Minis);
		return out;
	}

	/** Enthält der Text einen Namen dieses Bosses? */
	public boolean matchesBossName(String text) {
		for (String n : bossNames) if (text.contains(n)) return true;
		return false;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String menuName() {
		return bossName + " (" + levelName + ")";
	}

	/** Findet den Slayer zu einem Text, der einen Boss-Namen enthält. */
	public static SlayerType byBossName(String text) {
		for (SlayerType t : values()) {
			if (t.matchesBossName(text)) return t;
		}
		return null;
	}

	public static SlayerType byLevelName(String name) {
		for (SlayerType t : values()) {
			if (t.levelName.equalsIgnoreCase(name)) return t;
		}
		return null;
	}

	public static SlayerType byKey(String key) {
		for (SlayerType t : values()) {
			if (t.key().equalsIgnoreCase(key)) return t;
		}
		return null;
	}
}
