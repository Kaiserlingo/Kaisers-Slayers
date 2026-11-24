package at.kaiser.slayers.slayer;

import java.util.List;
import java.util.Locale;

/**
 * Alle Slayer in Hypixel SkyBlock mit Boss-Namen, Kosten, Slayer-XP pro Tier und Minibossen.
 * Kosten = Coins (bzw. Motes beim Vampire Slayer), Index 0 = Tier I.
 */
public enum SlayerType {
	ZOMBIE("Revenant Horror", "Zombie", "§2", 0x55FF55,
			new long[]{2_000, 7_500, 20_000, 50_000, 100_000},
			new int[]{5, 25, 100, 500, 1_500}, false,
			List.of("Revenant Sycophant", "Revenant Champion", "Deformed Revenant", "Atoned Champion", "Atoned Revenant")),
	SPIDER("Tarantula Broodfather", "Spider", "§4", 0xFF5555,
			new long[]{2_000, 7_500, 20_000, 50_000, 100_000},
			new int[]{5, 25, 100, 500, 1_000}, false,
			List.of("Tarantula Vermin", "Tarantula Beast", "Mutant Tarantula")),
	WOLF("Sven Packmaster", "Wolf", "§f", 0xFFFFFF,
			new long[]{2_000, 7_500, 20_000, 50_000},
			new int[]{5, 25, 100, 500}, false,
			List.of("Pack Enforcer", "Sven Follower", "Sven Alpha")),
	ENDERMAN("Voidgloom Seraph", "Enderman", "§5", 0xAA00AA,
			new long[]{2_000, 7_500, 20_000, 50_000},
			new int[]{5, 25, 100, 500}, false,
			List.of("Voidling Devotee", "Voidling Radical", "Voidcrazed Maniac")),
	BLAZE("Inferno Demonlord", "Blaze", "§6", 0xFFAA00,
			new long[]{10_000, 25_000, 60_000, 150_000},
			new int[]{5, 25, 100, 500}, false,
			List.of("Flare Demon", "Kindleheart Demon", "Burningsoul Demon")),
	VAMPIRE("Riftstalker Bloodfiend", "Vampire", "§c", 0xFF5555,
			new long[]{2_000, 4_000, 6_000, 10_000, 20_000},
			new int[]{10, 25, 60, 120, 150}, true,
			List.of());

	public final String bossName;
	public final String levelName;
	public final String color;
	/** Standard-Glow-Farbe (RGB) */
	public final int defaultGlow;
	private final long[] costs;
	private final int[] xp;
	/** true = Kosten in Motes (Rift), fließt nicht in die Coin-Profit-Rechnung ein. */
	public final boolean motes;
	public final List<String> minibosses;

	SlayerType(String bossName, String levelName, String color, int defaultGlow, long[] costs, int[] xp,
			   boolean motes, List<String> minibosses) {
		this.bossName = bossName;
		this.levelName = levelName;
		this.color = color;
		this.defaultGlow = defaultGlow;
		this.costs = costs;
		this.xp = xp;
		this.motes = motes;
		this.minibosses = minibosses;
	}

	public long cost(int tier) {
		return tier >= 1 && tier <= costs.length ? costs[tier - 1] : 0;
	}

	public int xpPerBoss(int tier) {
		return tier >= 1 && tier <= xp.length ? xp[tier - 1] : 0;
	}

	public String key() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Name für Menüs, z. B. "Revenant Horror (Zombie)" */
	public String menuName() {
		return bossName + " (" + levelName + ")";
	}

	public static SlayerType byBossName(String text) {
		for (SlayerType t : values()) {
			if (text.startsWith(t.bossName)) return t;
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
