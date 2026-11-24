package at.kaiser.slayers.slayer;

import at.kaiser.slayers.util.TextUtil;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Herzstück der Mod: erkennt anhand von Scoreboard und Chat, in welcher Phase
 * der aktuelle Slayer-Quest ist, und führt Statistik.
 * Diese Klasse kennt kein Minecraft – dadurch ist sie einfach testbar.
 */
public class SlayerTracker {

	public enum Phase { NONE, GRINDING, BOSS_ALIVE, BOSS_SLAIN }

	/** Ein Drop aus dem Chat. tier: 1 = RARE ... 5 = PRAY TO RNGESUS */
	public record Drop(String item, int count, int tier, long time) {}

	/** Rückmeldungen an die Oberfläche (Alerts, Sounds, Chat). */
	public interface Listener {
		void onBossSpawned(SlayerType type, int tier, long spawnMs);
		void onBossKilled(SlayerType type, int tier, long killMs, boolean newBest);
		void onQuestFailed(SlayerType type, int tier);
		void onRareDrop(String rarity, String item, int count, boolean big);
		/** Hypixel meldet "SLAYER MINI-BOSS ... HAS SPAWNED!" */
		default void onMinibossMessage(String message) {}
		/** Spider T5: "YOU COCOONED YOUR SLAYER BOSS" */
		default void onCocoon() {}
		default void onLevelUp(SlayerType type, int level) {}
		default void onQuestStarted(SlayerType type, int tier) {}
	}

	// --- Chat-Muster (ohne Farbcodes, getrimmt) ---
	static final Pattern QUEST_STARTED = Pattern.compile("^SLAYER QUEST STARTED!$");
	static final Pattern BOSS_SLAIN = Pattern.compile("^NICE! SLAYER BOSS SLAIN!$");
	static final Pattern QUEST_FAILED = Pattern.compile("^(SLAYER QUEST FAILED!|Your Slayer Quest has been cancelled!)$");
	static final Pattern MINIBOSS = Pattern.compile("^SLAYER MINI-?BOSS.*");
	static final Pattern COCOON = Pattern.compile("^YOU COCOONED YOUR SLAYER BOSS.*");
	static final Pattern LEVEL_UP = Pattern.compile("^LVL UP! ➜ (Zombie|Spider|Wolf|Enderman|Blaze|Vampire) Slayer LVL (\\d+).*");
	static final Pattern LEVEL_INFO = Pattern.compile(
			"^(Zombie|Spider|Wolf|Enderman|Blaze|Vampire) Slayer LVL (\\d+) - (?:Next LVL in ([\\d,]+) XP!|LVL MAXED OUT!)$");
	static final Pattern RNG_METER = Pattern.compile("^RNG Meter - ([\\d,]+) Stored XP$");
	static final Pattern DROP = Pattern.compile(
			"^(RARE DROP|VERY RARE DROP|CRAZY RARE DROP|INSANE DROP|PRAY TO RNGESUS DROP|EXTRAORDINARILY RARE DROP)!\\s+(.+)$");
	static final Pattern MAGIC_FIND = Pattern.compile("\\s*\\(\\+[\\d,.]+%? ?✯? ?Magic Find\\)\\s*$");
	static final Pattern COUNT_PREFIX = Pattern.compile("^(\\d+)x\\s+(.+)$");

	// --- Scoreboard-Muster ---
	static final Pattern BOSS_LINE = Pattern.compile("^(.+?) (I|II|III|IV|V)$");
	static final Pattern PROGRESS = Pattern.compile("\\(?([\\d,.]+[kKmM]?)/([\\d,.]+[kKmM]?)\\)?\\s*(Combat XP|Kills|XP)");

	private static final long ACTIVE_GRACE_MS = 120_000;
	private static final long DROP_WINDOW_MS = 15_000;

	private final SessionStats session = new SessionStats();
	private final ProfitTracker profit = new ProfitTracker();
	private double costMultiplier = 1.0;
	private static final long SACK_WINDOW_MS = 60_000;
	private LifetimeStats lifetime = new LifetimeStats();
	private Listener listener;

	private Phase phase = Phase.NONE;
	private SlayerType type;
	private int tier;
	private double progress;
	private double progressMax;
	private String progressUnit = "";

	private long questStart;
	private boolean questStartKnown;
	private long bossSpawn;
	private boolean killCounted;
	private long lastKillAt;
	private long lastTick;
	private SlayerType lastType;
	private int lastTier;
	private boolean dirty;
	/** Purse für Mob-Kill-Coins: letzter Stand, -1 = unbekannt */
	private double lastPurse = -1;
	private long purseBlockedUntil;
	private boolean countMobCoins = true;
	/** Größere Sprünge sind Verkäufe/Bank, keine Mob-Drops */
	static final double MAX_MOB_COINS = 25_000;
	static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy): ([\\d,.]+)");
	private final List<Drop> lastKillDrops = new java.util.ArrayList<>();
	private int questCounter;
	/** Nach einem Quest-Start per Chat kurz veraltete Scoreboard-Zustände ignorieren. */
	private long ignoreBoardStateUntil;

	public void setListener(Listener listener) {
		this.listener = listener;
	}

	public void setLifetime(LifetimeStats lifetime) {
		this.lifetime = lifetime == null ? new LifetimeStats() : lifetime;
	}

	// ------------------------------------------------------------------ Tick

	/** Regelmäßig aufrufen (z. B. jede Sekunde), zählt aktive Zeit. */
	public void tick(long now) {
		if (lastTick != 0) {
			long delta = Math.min(now - lastTick, 5_000);
			boolean active = phase != Phase.NONE || (lastKillAt != 0 && now - lastKillAt < ACTIVE_GRACE_MS);
			if (active && delta > 0) {
				session.activeMs += delta;
				profit.addActiveTime(type(), delta);
			}
		}
		lastTick = now;
	}

	// ------------------------------------------------------------ Scoreboard

	/**
	 * @param lines bereinigte Sidebar-Zeilen von oben nach unten, oder null wenn nicht in SkyBlock
	 */
	public void onScoreboard(List<String> lines, long now) {
		trackPurse(lines, now);
		// Boss-Zeile irgendwo im Scoreboard suchen (z. B. "Revenant Horror IV", "Atoned Horror V")
		int idx = -1;
		SlayerType foundType = null;
		int foundTier = 0;
		if (lines != null) {
			for (int i = 0; i < lines.size(); i++) {
				Matcher m = BOSS_LINE.matcher(lines.get(i));
				if (!m.matches()) continue;
				SlayerType t = SlayerType.byBossName(m.group(1));
				if (t != null) {
					idx = i;
					foundType = t;
					foundTier = TextUtil.romanToInt(m.group(2));
					break;
				}
			}
		}
		if (idx < 0) {
			if (phase != Phase.NONE && phase != Phase.BOSS_SLAIN) {
				// Quest verschwunden (Lobby-Wechsel, abgebrochen) – nur Anzeige zurücksetzen
				phase = Phase.NONE;
			} else if (phase == Phase.BOSS_SLAIN) {
				phase = Phase.NONE;
			}
			return;
		}

		type = foundType;
		tier = foundTier;
		lastType = type;
		lastTier = tier;

		// Status-Zeile: die nächsten Zeilen nach dem Boss-Namen durchsuchen
		String state = "";
		for (int i = idx + 1; i < Math.min(lines.size(), idx + 4); i++) {
			String l = lines.get(i);
			if (l.startsWith("Slay the boss") || l.startsWith("Boss slain") || PROGRESS.matcher(l).find()) {
				state = l;
				break;
			}
		}
		boolean stale = now < ignoreBoardStateUntil;
		if (stale && (state.startsWith("Slay the boss") || state.startsWith("Boss slain"))) {
			return;
		}
		if (state.startsWith("Slay the boss")) {
			if (phase != Phase.BOSS_ALIVE && phase != Phase.BOSS_SLAIN) {
				onSpawn(now);
			}
		} else if (state.startsWith("Boss slain")) {
			if (!killCounted) registerKill(now);
			phase = Phase.BOSS_SLAIN;
		} else {
			Matcher p = PROGRESS.matcher(state);
			boolean hasProgress = p.find();
			if (hasProgress && (phase == Phase.NONE || phase == Phase.BOSS_SLAIN)) {
				// Quest läuft schon (z. B. nach Lobby-Wechsel) oder neuer Quest ohne Chat-Meldung
				boolean recentKill = lastKillAt != 0 && now - lastKillAt < 10_000;
				startQuest(now, phase == Phase.BOSS_SLAIN || recentKill);
			}
			if (hasProgress) {
				progress = TextUtil.parseNumber(p.group(1));
				progressMax = TextUtil.parseNumber(p.group(2));
				progressUnit = p.group(3);
			}
		}
	}

	/** Purse wurde in einem Menü verändert (Verkauf, Bank, …) – bis dahin keine Mob-Coins zählen */
	public void blockPurse(long until) {
		purseBlockedUntil = Math.max(purseBlockedUntil, until);
	}

	public void setCountMobCoins(boolean on) {
		countMobCoins = on;
	}

	/** Kleine Purse-Zuwächse während eines laufenden Quests = Coins von Mob-Kills */
	private void trackPurse(List<String> lines, long now) {
		double purse = -1;
		if (lines != null) {
			for (String l : lines) {
				Matcher m = PURSE.matcher(l);
				if (m.find()) {
					purse = TextUtil.parseNumber(m.group(1));
					break;
				}
			}
		}
		if (purse < 0) {
			lastPurse = -1;
			return;
		}
		double delta = lastPurse >= 0 ? purse - lastPurse : 0;
		lastPurse = purse;
		boolean questRunning = phase == Phase.GRINDING || phase == Phase.BOSS_ALIVE;
		if (countMobCoins && questRunning && type != null && delta >= 1 && delta <= MAX_MOB_COINS && now >= purseBlockedUntil) {
			profit.addItem(type, ProfitTracker.MOB_COINS, Math.round(delta));
			dirty = true;
		}
	}

	private static int indexOf(List<String> lines, String prefix) {
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).startsWith(prefix)) return i;
		}
		return -1;
	}

	// ------------------------------------------------------------------ Chat

	public void onChat(String rawMessage, long now) {
		String msg = TextUtil.stripFormatting(rawMessage).trim();
		if (msg.isEmpty()) return;

		if (MINIBOSS.matcher(msg).matches()) {
			if (listener != null) listener.onMinibossMessage(msg);
			return;
		}
		if (COCOON.matcher(msg).matches()) {
			if (listener != null) listener.onCocoon();
			return;
		}
		Matcher lu = LEVEL_UP.matcher(msg);
		if (lu.matches()) {
			SlayerType t = SlayerType.byLevelName(lu.group(1));
			if (t != null) {
				lifetime.get(t).level = Integer.parseInt(lu.group(2));
				dirty = true;
				if (listener != null) listener.onLevelUp(t, lifetime.get(t).level);
			}
			return;
		}
		if (QUEST_STARTED.matcher(msg).matches()) {
			startQuest(now, true);
			if (listener != null && type() != null) listener.onQuestStarted(type(), tier());
			ignoreBoardStateUntil = now + 2_000;
			return;
		}
		if (BOSS_SLAIN.matcher(msg).matches()) {
			if (!killCounted) registerKill(now);
			phase = Phase.BOSS_SLAIN;
			return;
		}
		if (QUEST_FAILED.matcher(msg).matches()) {
			session.fails++;
			SlayerType t = type != null ? type : lastType;
			if (t != null) lifetime.get(t).fails++;
			if (listener != null) listener.onQuestFailed(t, type != null ? tier : lastTier);
			phase = Phase.NONE;
			dirty = true;
			return;
		}
		Matcher m = LEVEL_INFO.matcher(msg);
		if (m.matches()) {
			SlayerType t = SlayerType.byLevelName(m.group(1));
			if (t != null) {
				LifetimeStats.Entry e = lifetime.get(t);
				e.level = Integer.parseInt(m.group(2));
				e.xpToNextLevel = m.group(3) == null ? 0 : (long) TextUtil.parseNumber(m.group(3));
				dirty = true;
			}
			return;
		}
		m = RNG_METER.matcher(msg);
		if (m.matches()) {
			SlayerType t = type != null ? type : lastType;
			if (t != null) {
				lifetime.get(t).rngMeterXp = (long) TextUtil.parseNumber(m.group(1));
				dirty = true;
			}
			return;
		}
		m = DROP.matcher(msg);
		if (m.matches() && isSlayerContext(now)) {
			String rarity = m.group(1);
			String item = MAGIC_FIND.matcher(m.group(2)).replaceAll("").trim();
			if (item.startsWith("(") && item.endsWith(")")) item = item.substring(1, item.length() - 1).trim();
			if (item.startsWith("Enchanted Book (") && item.endsWith(")")) {
				item = item.substring("Enchanted Book (".length(), item.length() - 1).trim();
			}
			int count = 1;
			Matcher c = COUNT_PREFIX.matcher(item);
			if (c.matches()) {
				count = Integer.parseInt(c.group(1));
				item = c.group(2).trim();
			}
			session.addDrop(item, count);
			profit.addItem(type != null ? type : lastType, item, count);
			SlayerType t = type != null ? type : lastType;
			if (t != null) lifetime.get(t).drops.merge(item, count, Integer::sum);
			dirty = true;
			boolean big = !rarity.equals("RARE DROP") && !rarity.equals("VERY RARE DROP");
			if (lastKillAt != 0 && now - lastKillAt < DROP_WINDOW_MS) {
				lastKillDrops.add(new Drop(item, count, rarityTier(rarity), now));
			}
			if (listener != null) listener.onRareDrop(rarity, item, count, big);
		}
	}

	private boolean isSlayerContext(long now) {
		return phase != Phase.NONE || (lastKillAt != 0 && now - lastKillAt < DROP_WINDOW_MS);
	}

	// --------------------------------------------------------------- Logik

	public static int rarityTier(String rarity) {
		return switch (rarity) {
			case "RARE DROP" -> 1;
			case "VERY RARE DROP" -> 2;
			case "CRAZY RARE DROP" -> 3;
			case "INSANE DROP" -> 4;
			default -> 5;
		};
	}

	private void startQuest(long now, boolean known) {
		questCounter++;
		phase = Phase.GRINDING;
		questStart = now;
		questStartKnown = known;
		bossSpawn = 0;
		killCounted = false;
		progress = 0;
		progressMax = 0;
	}

	private void onSpawn(long now) {
		if (phase == Phase.NONE) {
			// Mitten im Bosskampf eingestiegen -> Zeiten unbekannt
			questStart = now;
			questStartKnown = false;
			killCounted = false;
			bossSpawn = 0;
			phase = Phase.BOSS_ALIVE;
			return;
		}
		phase = Phase.BOSS_ALIVE;
		bossSpawn = now;
		long spawnMs = now - questStart;
		if (questStartKnown) session.spawnTimes.add(spawnMs);
		if (listener != null && type != null) listener.onBossSpawned(type, tier, questStartKnown ? spawnMs : -1);
	}

	private void registerKill(long now) {
		killCounted = true;
		lastKillAt = now;
		lastKillDrops.clear();
		SlayerType t = type != null ? type : lastType;
		int tr = type != null ? tier : lastTier;
		if (t == null) return;

		session.kills++;
		if (!t.motes) session.coinCost += Math.round(t.cost(tr) * costMultiplier);
		profit.addKill(t, tr, costMultiplier);
		session.xpGained += t.xpPerBoss(tr);

		LifetimeStats.Entry e = lifetime.get(t);
		e.kills++;
		if (e.xpToNextLevel > 0) e.xpToNextLevel = Math.max(0, e.xpToNextLevel - t.xpPerBoss(tr));

		long killMs = -1;
		boolean newBest = false;
		if (bossSpawn > 0) {
			killMs = now - bossSpawn;
			session.killTimes.add(killMs);
			e.totalKillMs += killMs;
			e.timedKills++;
			if (e.bestKillMs <= 0 || killMs < e.bestKillMs) {
				newBest = e.bestKillMs > 0;
				e.bestKillMs = killMs;
			}
		}
		if (questStartKnown) session.questTimes.add(now - questStart);
		dirty = true;
		if (listener != null) listener.onBossKilled(t, tr, killMs, newBest);
	}

	/** Items aus einer Sack-Nachricht – zählen nur während eines Quests / kurz nach einem Kill. */
	public void onSackItems(java.util.Map<String, Long> items, long now) {
		boolean context = phase != Phase.NONE || (lastKillAt != 0 && now - lastKillAt < SACK_WINDOW_MS);
		if (!context) return;
		SlayerType t = type();
		for (var e : items.entrySet()) profit.addItem(t, e.getKey(), e.getValue());
	}

	public void setCostMultiplier(double m) {
		costMultiplier = m <= 0 ? 1.0 : m;
	}

	public ProfitTracker profit() {
		return profit;
	}

	public void resetSession() {
		profit.resetSession();
		session.reset();
	}

	/** true, wenn sich Lifetime-Daten geändert haben (dann speichern). Setzt das Flag zurück. */
	public boolean consumeDirty() {
		boolean d = dirty | profit.consumeDirty();
		dirty = false;
		return d;
	}

	// ------------------------------------------------------------- Getter

	public Phase phase() { return phase; }
	public SlayerType type() { return type != null ? type : lastType; }
	public int tier() { return type != null ? tier : lastTier; }
	public double progress() { return progress; }
	public double progressMax() { return progressMax; }
	public String progressUnit() { return progressUnit; }
	public long bossSpawnTime() { return bossSpawn; }
	public long questStart() { return questStart; }
	public boolean questStartKnown() { return questStartKnown; }
	public SessionStats session() { return session; }
	public long lastKillAt() { return lastKillAt; }
	public List<Drop> lastKillDrops() { return lastKillDrops; }
	/** Wird bei jedem neuen Quest erhöht (für "einmal pro Quest"-Alerts). */
	public int questCounter() { return questCounter; }
	public LifetimeStats lifetime() { return lifetime; }
}
