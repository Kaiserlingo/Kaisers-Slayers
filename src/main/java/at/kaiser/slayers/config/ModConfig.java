package at.kaiser.slayers.config;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.slayer.LifetimeStats;
import at.kaiser.slayers.slayer.ProfitTracker;
import at.kaiser.slayers.slayer.SlayerType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Alle Einstellungen. Gespeichert in .minecraft/config/kaisers_slayers.json */
public class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path DIR = FabricLoader.getInstance().getConfigDir();
	private static final Path CONFIG_FILE = DIR.resolve("kaisers_slayers.json");
	private static final Path STATS_FILE = DIR.resolve("kaisers_slayers_stats.json");
	private static final Path PROFIT_FILE = DIR.resolve("kaisers_slayers_profit.json");

	/** Position eines HUD-Elements: x/y als Anteil der Bildschirmgröße (0..1), damit es bei jeder Auflösung passt. */
	public static class HudPos {
		public float x, y, scale = 1.0f;
		public boolean enabled = true;

		public HudPos() {}

		public HudPos(float x, float y) {
			this.x = x;
			this.y = y;
		}
	}

	// ================= Tracker =================
	public boolean killMessage = true;
	public boolean questTimeMessage = true;
	public boolean showLevelEta = true;
	/** true = Instant-Sell-Preis, false = Sell-Offer-Preis */
	public boolean instantSellPrices = true;

	// ================= Profit-Tracker =================
	public boolean profitEnabled = true;
	/** SESSION oder TOTAL */
	public String profitMode = "SESSION";
	public int profitMaxItems = 8;
	/** Items unter diesem Gesamtwert werden zusammengefasst */
	public long profitMinValue = 0;
	public boolean profitCountSacks = true;
	public boolean profitShowIcons = true;
	/** Kosten-Faktor (z. B. 0.5 bei Aatrox "Slashed Pricing") */
	public double costMultiplier = 1.0;
	public List<String> hiddenItems = new ArrayList<>();

	// ================= Alerts =================
	public boolean spawnAlert = true;
	public boolean soonSpawnAlert = true;
	public int soonSpawnPercent = 90;
	public boolean minibossAlert = true;
	public boolean minibossWaypoint = true;
	public int waypointSeconds = 15;
	public boolean failAlert = true;
	public boolean dropAlert = true;
	public boolean bossTimeWarning = true;
	public int bossTimeLimitSec = 180;
	public int bossTimeWarnSec = 30;
	public boolean sounds = true;
	public float alertScale = 3.0f;

	// ================= Welt & Highlights =================
	public boolean beamsEnabled = true;
	public int minibossColor = 0x5555FF;
	public int minibossBeamColor = 0x55FFFF;
	public boolean otherBossGlow = false;
	public int otherBossColor = 0x888888;
	public int dropGlowSeconds = 20;

	// ================= HUD =================
	public HudPos slayerHud = new HudPos(0.005f, 0.06f);
	public HudPos profitHud = new HudPos(0.005f, 0.55f);
	public boolean hudBackground = true;
	public boolean hudOnlyWhenActive = false;
	public boolean waypointArrow = true;
	public int bossPanelOffsetY = 24;
	/** Akzentfarbe der Oberfläche (RGB) */
	public int accentColor = 0xE74C3C;

	// ================= Gambling =================
	public boolean gamblingEnabled = true;
	/** CASE, SLOTS, WHEEL, CARDS oder RANDOM */
	public String gamblingStyle = "CASE";
	public int gamblingDurationMs = 5000;
	/** ALWAYS oder RARE_ONLY */
	public String gamblingTrigger = "ALWAYS";
	public boolean gamblingSounds = true;
	public boolean gamblingShowValue = true;
	public long gamblingJackpotValue = 5_000_000;
	public boolean gamblingHideHud = true;
	public boolean gamblingParticles = true;
	public boolean gamblingShake = true;
	public boolean gamblingFlash = true;
	/** Abdunkelung des Hintergrunds in % */
	public int gamblingDim = 85;
	public int gamblingAccent = 0xFFD700;
	public boolean gamblingUseOwnDrops = true;
	/** Eigene Items je Slayer, Format "Itemname|Stufe" (Stufe 0-5) */
	public Map<String, List<String>> gamblingExtraItems = new LinkedHashMap<>();

	// ================= Pro Slayer =================
	public Map<String, Map<String, Boolean>> slayerFeatures = new LinkedHashMap<>();
	public Map<String, Integer> glowColors = new LinkedHashMap<>();

	public boolean feature(SlayerType type, SlayerFeature f) {
		if (type == null) return false;
		Map<String, Boolean> m = slayerFeatures.get(type.key());
		if (m == null) return f.defaultOn;
		Boolean b = m.get(f.key());
		return b == null ? f.defaultOn : b;
	}

	public void setFeature(SlayerType type, SlayerFeature f, boolean on) {
		slayerFeatures.computeIfAbsent(type.key(), k -> new LinkedHashMap<>()).put(f.key(), on);
	}

	public int glowColor(SlayerType type) {
		Integer c = glowColors.get(type.key());
		return c != null ? c : type.defaultGlow;
	}

	public List<String> extraItems(SlayerType type) {
		return gamblingExtraItems.getOrDefault(type.key(), new ArrayList<>());
	}

	// ================= Laden / Speichern =================

	public static ModConfig load() {
		try {
			if (Files.exists(CONFIG_FILE)) {
				try (Reader r = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
					ModConfig c = GSON.fromJson(r, ModConfig.class);
					if (c != null) {
						c.fixNulls();
						return c;
					}
				}
			}
		} catch (Exception e) {
			KaisersSlayers.LOGGER.warn("Konnte Config nicht laden, nutze Standardwerte", e);
		}
		ModConfig c = new ModConfig();
		c.fixNulls();
		c.save();
		return c;
	}

	void fixNulls() {
		if (slayerFeatures == null) slayerFeatures = new LinkedHashMap<>();
		if (glowColors == null) glowColors = new LinkedHashMap<>();
		if (gamblingExtraItems == null) gamblingExtraItems = new LinkedHashMap<>();
		for (SlayerType t : SlayerType.values()) gamblingExtraItems.computeIfAbsent(t.key(), k -> new ArrayList<>());
		if (hiddenItems == null) hiddenItems = new ArrayList<>();
		if (slayerHud == null) slayerHud = new HudPos(0.005f, 0.06f);
		if (profitHud == null) profitHud = new HudPos(0.005f, 0.55f);
		if (gamblingStyle == null) gamblingStyle = "CASE";
		if (gamblingTrigger == null) gamblingTrigger = "ALWAYS";
		if (profitMode == null) profitMode = "SESSION";
		if (costMultiplier <= 0) costMultiplier = 1.0;
	}

	public void save() {
		write(CONFIG_FILE, this);
	}

	public static LifetimeStats loadStats() {
		LifetimeStats s = read(STATS_FILE, LifetimeStats.class);
		return s != null && s.slayers != null ? s : new LifetimeStats();
	}

	public static void saveStats(LifetimeStats stats) {
		write(STATS_FILE, stats);
	}

	public static ProfitTracker.Saved loadProfit() {
		ProfitTracker.Saved s = read(PROFIT_FILE, ProfitTracker.Saved.class);
		return s != null ? s : new ProfitTracker.Saved();
	}

	public static void saveProfit(ProfitTracker.Saved s) {
		write(PROFIT_FILE, s);
	}

	public static Path debugFile() {
		return DIR.resolve("kaisers_slayers_debug.txt");
	}

	private static <T> T read(Path file, Class<T> cls) {
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					return GSON.fromJson(r, cls);
				}
			}
		} catch (Exception e) {
			KaisersSlayers.LOGGER.warn("Konnte {} nicht laden", file.getFileName(), e);
		}
		return null;
	}

	private static void write(Path file, Object data) {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(data, w);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			KaisersSlayers.LOGGER.warn("Konnte {} nicht speichern", file.getFileName(), e);
		}
	}
}
