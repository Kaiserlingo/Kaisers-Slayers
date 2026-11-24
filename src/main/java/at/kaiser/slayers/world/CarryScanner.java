package at.kaiser.slayers.world;

import at.kaiser.slayers.carry.CarryManager;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.slayer.SlayerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Findet die Slayer-Bosse deiner Carry-Kunden ("Name's SLAYER BOSS" über dem Boss) und erkennt, wann sie sterben.
 * Ein Boss zählt als Carry, wenn er stirbt (Todes-Animation) oder sein Namensschild bei niedriger HP verschwindet,
 * während du in der Nähe bist. Läuft der Timer ab (Quest failed), zählt es nicht.
 */
public final class CarryScanner {
	private static final Pattern OWNER = Pattern.compile("^(?:Spawned by:\\s*)?([A-Za-z0-9_]{1,16})(?:['’]s SLAYER BOSS)?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern TIER = Pattern.compile("\\b(I|II|III|IV|V)\\b");

	/** Was der HUD über einen lebenden Kunden-Boss wissen muss */
	public record LiveBoss(String bossName, double hpPercent, int timeLeftSec) {}

	/** Ein beobachteter Kunden-Boss (Schlüssel: Entity-ID des Besitzer-Schilds) */
	static final class Tracked {
		String client;
		String bossName = "";
		double hp = -1, maxHp = -1;
		int timeLeft = -1;
		long firstSeen, lastSeen;
		double x, y, z;
		boolean dying;
	}

	/** Ereignis für KaisersSlayers: Boss gespawnt oder als Carry gezählt */
	public interface Listener {
		void onClientBossSpawned(String client, String bossName);

		void onCarryCounted(CarryManager.Client client, long fightMs);
	}

	private final Map<Integer, Tracked> tracked = new HashMap<>();
	private final Set<Integer> counted = new HashSet<>();
	private volatile Map<String, LiveBoss> live = Map.of();

	/** Lebende Bosse der Kunden (Name klein → Info) */
	public Map<String, LiveBoss> live() {
		return live;
	}

	public void reset() {
		tracked.clear();
		counted.clear();
		live = Map.of();
		Highlights.setCarry(Map.of());
	}

	/** Spielername aus "Name's SLAYER BOSS" bzw. "Spawned by: Name", sonst null */
	public static String ownerName(String label) {
		if (label == null || !NameParser.isOwnerMarker(label)) return null;
		Matcher m = OWNER.matcher(label.trim());
		return m.matches() ? m.group(1) : null;
	}

	/**
	 * Entscheidung, ob ein verschwundener Boss getötet wurde (pure Logik, testbar).
	 * @param hpFraction letzte bekannte HP (0..1) oder &lt; 0 wenn unbekannt
	 */
	public static boolean countsAsKill(boolean dying, double hpFraction, int timeLeftSec, double distanceToPlayer) {
		if (dying) return true;
		if (distanceToPlayer > 24) return false; // aus Sichtweite verschwunden – nicht sicher
		if (timeLeftSec >= 0 && timeLeftSec <= 1) return false; // Zeit abgelaufen = Quest failed
		return hpFraction >= 0 && hpFraction <= 0.2;
	}

	public void scan(Minecraft mc, CarryManager mgr, ModConfig cfg, Listener listener, long now) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || mgr.isEmpty()) {
			if (!tracked.isEmpty() || !live.isEmpty()) reset();
			return;
		}
		List<WorldScanner.Label> labels = new ArrayList<>();
		List<Entity> mobs = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == player) continue;
			String label = WorldScanner.labelText(e);
			if (label != null) labels.add(new WorldScanner.Label(e, label));
			if (e instanceof LivingEntity && !WorldScanner.typeId(e).equals("armor_stand")) mobs.add(e);
		}

		Map<Integer, Integer> glow = new HashMap<>();
		Map<String, LiveBoss> nowLive = new HashMap<>();
		Set<Integer> seen = new HashSet<>();
		for (WorldScanner.Label owner : labels) {
			String name = ownerName(owner.text());
			if (name == null) continue;
			CarryManager.Client client = mgr.find(name);
			if (client == null) continue;
			int id = owner.entity().getId();
			seen.add(id);

			WorldScanner.Label bossLabel = WorldScanner.closestLabel(labels, owner.entity(), 1.5, 2.5, l -> bossType(l.text()) != null);
			SlayerType type = bossLabel != null ? bossType(bossLabel.text()) : null;
			Entity anchor = bossLabel != null ? bossLabel.entity() : owner.entity();
			Entity mob = WorldScanner.mobBelow(anchor, mobs, type != null ? type.mobId : null, player);

			Tracked t = tracked.get(id);
			boolean isNew = t == null;
			if (isNew) {
				t = new Tracked();
				t.client = client.name;
				t.firstSeen = now;
				tracked.put(id, t);
			}
			t.lastSeen = now;
			t.x = anchor.getX();
			t.y = anchor.getY();
			t.z = anchor.getZ();
			if (bossLabel != null) {
				String text = bossLabel.text();
				double hp = NameParser.hp(text);
				if (hp >= 0) {
					t.hp = hp;
					t.maxHp = Math.max(t.maxHp, hp);
				}
				Matcher tm = TIER.matcher(text);
				String tier = tm.find() ? " " + tm.group(1) : "";
				if (type != null) {
					t.bossName = type.bossName + tier;
					long table = type.maxHp(at.kaiser.slayers.util.TextUtil.romanToInt(tier.trim()));
					if (table > 0) t.maxHp = Math.max(t.maxHp, table);
				}
			}
			// Timer-Schild direkt beim Boss
			for (WorldScanner.Label l : labels) {
				if (WorldScanner.horizontalDist(l.entity(), anchor) > 2.5 || Math.abs(l.entity().getY() - anchor.getY()) > 3.5) continue;
				int sec = NameParser.timerSeconds(l.text());
				if (sec >= 0) t.timeLeft = sec;
			}
			if (isNew && listener != null) listener.onClientBossSpawned(client.name, t.bossName);

			if (mob instanceof LivingEntity le) {
				if (le.isDeadOrDying()) t.dying = true;
				if (cfg.carryGlow) glow.put(le.getId(), cfg.carryGlowColor);
			}
			if (t.dying && counted.add(id)) count(mgr, t, listener, now);
			double pct = t.hp >= 0 && t.maxHp > 0 ? t.hp / t.maxHp : -1;
			if (!counted.contains(id)) nowLive.put(CarryManager.key(client.name), new LiveBoss(t.bossName, pct, t.timeLeft));
		}

		// verschwundene Bosse auswerten
		var it = tracked.entrySet().iterator();
		while (it.hasNext()) {
			var e = it.next();
			if (seen.contains(e.getKey())) continue;
			Tracked t = e.getValue();
			if (!counted.contains(e.getKey())) {
				double dx = player.getX() - t.x, dy = player.getY() - t.y, dz = player.getZ() - t.z;
				double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
				double pct = t.hp >= 0 && t.maxHp > 0 ? t.hp / t.maxHp : -1;
				if (countsAsKill(t.dying, pct, t.timeLeft, dist)) count(mgr, t, listener, now);
			}
			counted.remove(e.getKey());
			it.remove();
		}
		live = nowLive;
		Highlights.setCarry(glow);
	}

	private static void count(CarryManager mgr, Tracked t, Listener listener, long now) {
		long fight = now - t.firstSeen;
		CarryManager.Client c = mgr.onBossKilled(t.client, t.bossName, fight, now);
		if (c != null && listener != null) listener.onCarryCounted(c, fight);
	}

	private static SlayerType bossType(String text) {
		for (SlayerType t : SlayerType.values()) if (t.matchesBossName(text) && !text.toUpperCase(Locale.ROOT).contains("SLAYER BOSS")) return t;
		return null;
	}
}
