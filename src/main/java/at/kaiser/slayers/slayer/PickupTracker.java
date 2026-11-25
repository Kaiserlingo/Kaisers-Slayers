package at.kaiser.slayers.slayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Erkennt Drops, die ohne Chat-Nachricht direkt ins Inventar wandern (z. B. Derelict Ashe, Null Sphere, Blaze Rod):
 * vergleicht regelmäßig die Item-Anzahlen im Inventar. Zuwächse während des Quests (und kurz nach dem Kill) zählen als Drop.
 * Ohne Minecraft-Code → testbar.
 *
 * Nicht gezählt wird:
 *  - alles, während/kurz nach einem offenen Menü (Kisten, Bazaar, Handel, Crafting …)
 *  - Items, die schon per Chat-Drop-Nachricht gezählt wurden (RARE DROP! …) – die werden hier "verrechnet"
 *  - Verzauberungsbücher ("Enchanted Book" – die kommen mit Namen aus dem Chat)
 */
public final class PickupTracker {
	/** Chat-Drop, dessen Item gleich im Inventar auftauchen wird */
	private record Pending(String key, long count, long time) {}

	private static final long PENDING_MS = 10_000;
	private Map<String, Long> last;
	private long blockedUntil;
	private final List<Pending> pending = new ArrayList<>();

	/** Inventar-Vergleich aussetzen (Menü offen, Weltwechsel …) */
	public void block(long until) {
		blockedUntil = Math.max(blockedUntil, until);
	}

	/** Nächsten Vergleich als neue Basis nehmen (z. B. nach Lobby-Wechsel) */
	public void resetBaseline() {
		last = null;
	}

	/** Item kam schon über eine Chat-Drop-Nachricht → beim Auftauchen im Inventar nicht doppelt zählen */
	public void expectChatDrop(String item, long count, long now) {
		pending.add(new Pending(key(item), count, now));
	}

	/**
	 * @param snapshot Itemname → Gesamtanzahl im Inventar
	 * @param counting zählt gerade (Quest läuft oder kurz nach dem Kill)
	 * @return neu aufgesammelte Items (Name → Anzahl)
	 */
	public Map<String, Long> update(Map<String, Long> snapshot, long now, boolean counting) {
		pending.removeIf(p -> now - p.time() > PENDING_MS);
		Map<String, Long> gained = new LinkedHashMap<>();
		Map<String, Long> prev = last;
		last = new HashMap<>(snapshot);
		if (prev == null || now < blockedUntil || !counting) return gained;
		for (Map.Entry<String, Long> e : snapshot.entrySet()) {
			String name = e.getKey();
			if (ignored(name)) continue;
			long diff = e.getValue() - prev.getOrDefault(name, 0L);
			if (diff <= 0) continue;
			diff = consumePending(key(name), diff);
			if (diff > 0) gained.merge(name, diff, Long::sum);
		}
		return gained;
	}

	private long consumePending(String key, long diff) {
		Iterator<Pending> it = pending.iterator();
		List<Pending> rest = new ArrayList<>();
		while (it.hasNext() && diff > 0) {
			Pending p = it.next();
			if (!p.key().equals(key)) continue;
			it.remove();
			long used = Math.min(diff, p.count());
			diff -= used;
			if (p.count() > used) rest.add(new Pending(p.key(), p.count() - used, p.time()));
		}
		pending.addAll(rest);
		return diff;
	}

	/** Items, die nie als Slayer-Drop zählen */
	static boolean ignored(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		return n.isEmpty() || n.equals("enchanted book") || n.equals("skyblock menu") || n.contains("(click)")
				|| n.equals("quiver") || n.equals("air");
	}

	private static String key(String item) {
		return item.trim().toLowerCase(Locale.ROOT);
	}
}
