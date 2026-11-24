package at.kaiser.slayers.config;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.slayer.LifetimeStats;
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

	// ================= Allgemein: Tracker =================
	public boolean showProfit = true;
	public boolean showDrops = true;
	public boolean showLevelEta = true;
	public boolean killMessage = true;
	/** true = Instant-Sell-Preis, false = Sell-Offer-Preis */
	public boolean instantSellPrices = true;

	// ================= Allgemein: Alerts =================
	public boolean spawnAlert = true;
	public boolean soonSpawnAlert = true;
	/** Ab wie viel % Quest-Fortschritt "Boss spawnt gleich" kommt. */
	public int soonSpawnPercent = 90;
	public boolean minibossAlert = true;
	public boolean minibossWaypoint = true;
	/** Wie lange der Waypoint-Pfeil zum Miniboss sichtbar bleibt (Sekunden). */
	public int waypointSeconds = 15;
	public boolean failAlert = true;
	public boolean dropAlert = true;
	public boolean bossTimeWarning = true;
	public int bossTimeLimitSec = 180;
	public int bossTimeWarnSec = 30;
	public boolean sounds = true;

	// ================= HUD =================
	public boolean hudEnabled = true;
	public int hudX = 6;
	public int hudY = 40;
	public float hudScale = 1.0f;
	public boolean hudBackground = true;
	public boolean hudOnlyWhenActive = false;
	public boolean waypointArrow = true;
	/** Abstand des Boss-Panels unter dem Fadenkreuz */
	public int bossPanelOffsetY = 24;

	// ================= Gambling =================
	public boolean gamblingEnabled = true;
	/** REEL = Kisten-Öffnung (horizontal), SLOTS = Slot-Machine mit 3 Walzen */
	public String gamblingStyle = "REEL";
	/** Dauer des Drehens in Millisekunden */
	public int gamblingDurationMs = 4000;
	/** ALWAYS = bei jedem Kill, RARE_ONLY = nur wenn ein RARE DROP oder besser kam */
	public String gamblingTrigger = "ALWAYS";
	public boolean gamblingSounds = true;
	public boolean gamblingShowValue = true;
	/** Ab diesem Wert (Coins) gibt es den Jackpot-Effekt */
	public long gamblingJackpotValue = 5_000_000;
	public float gamblingScale = 1.0f;
	/** Eigene Drops (aus der Gesamtstatistik) mit in die Walze nehmen */
	public boolean gamblingUseOwnDrops = true;
	/** Eigene Items je Slayer, Format "Itemname|Stufe" (Stufe 0-5), z. B. "Warden Heart|5" */
	public Map<String, List<String>> gamblingExtraItems = new LinkedHashMap<>();

	// ================= Pro Slayer =================
	/** slayer -> feature -> an/aus */
	public Map<String, Map<String, Boolean>> slayerFeatures = new LinkedHashMap<>();
	/** slayer -> Glow-Farbe (RGB) */
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

	private void fixNulls() {
		if (slayerFeatures == null) slayerFeatures = new LinkedHashMap<>();
		if (glowColors == null) glowColors = new LinkedHashMap<>();
		if (gamblingExtraItems == null) gamblingExtraItems = new LinkedHashMap<>();
		for (SlayerType t : SlayerType.values()) {
			gamblingExtraItems.computeIfAbsent(t.key(), k -> new ArrayList<>());
		}
		if (gamblingStyle == null) gamblingStyle = "REEL";
		if (gamblingTrigger == null) gamblingTrigger = "ALWAYS";
	}

	public void save() {
		write(CONFIG_FILE, this);
	}

	public static LifetimeStats loadStats() {
		try {
			if (Files.exists(STATS_FILE)) {
				try (Reader r = Files.newBufferedReader(STATS_FILE, StandardCharsets.UTF_8)) {
					LifetimeStats s = GSON.fromJson(r, LifetimeStats.class);
					if (s != null && s.slayers != null) return s;
				}
			}
		} catch (Exception e) {
			KaisersSlayers.LOGGER.warn("Konnte Statistik nicht laden", e);
		}
		return new LifetimeStats();
	}

	public static void saveStats(LifetimeStats stats) {
		write(STATS_FILE, stats);
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
