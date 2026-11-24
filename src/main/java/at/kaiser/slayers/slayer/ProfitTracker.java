package at.kaiser.slayers.slayer;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.ToDoubleBiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Profit-Tracker pro Slayer: zählt Drops (Chat + Sacks), Kills und Quest-Kosten,
 * getrennt nach Session (seit Spielstart / Reset) und Gesamt (dauerhaft gespeichert).
 * Ohne Minecraft-Code → testbar. Preise kommen von außen (Funktion Item → Wert).
 */
public class ProfitTracker {

	/** Ein Topf mit Zahlen – einmal pro Slayer für Session und für Gesamt. */
	public static class Bucket {
		public Map<String, Long> items = new LinkedHashMap<>();
		public long kills;
		public long cost;
		public long activeMs;
		public long minibosses;

		public void add(String item, long count) {
			items.merge(item, count, Long::sum);
		}

		public double itemValue(ToDoubleBiFunction<String, Long> price) {
			double sum = 0;
			for (Map.Entry<String, Long> e : items.entrySet()) sum += price.applyAsDouble(e.getKey(), e.getValue());
			return sum;
		}

		public double profit(ToDoubleBiFunction<String, Long> price) {
			return itemValue(price) - cost;
		}

		public double perHour(double value) {
			return activeMs < 60_000 ? 0 : value / (activeMs / 3_600_000.0);
		}

		public void clear() {
			items.clear();
			kills = 0;
			cost = 0;
			activeMs = 0;
			minibosses = 0;
		}
	}

	/** Gespeicherter Teil (Gesamt) */
	public static class Saved {
		public Map<String, Bucket> slayers = new LinkedHashMap<>();
	}

	// Sack-Nachricht im Hover-Text: "+12 Revenant Flesh (Combat Sack)"
	private static final Pattern SACK_LINE = Pattern.compile("([+-])([\\d,]+) (.+?) \\((.+?) Sack\\)");

	private final Map<SlayerType, Bucket> session = new EnumMap<>(SlayerType.class);
	private Saved saved = new Saved();
	private boolean dirty;

	public void setSaved(Saved s) {
		saved = s == null ? new Saved() : s;
		if (saved.slayers == null) saved.slayers = new LinkedHashMap<>();
	}

	public Saved saved() {
		return saved;
	}

	public Bucket session(SlayerType t) {
		return session.computeIfAbsent(t, k -> new Bucket());
	}

	public Bucket total(SlayerType t) {
		Bucket b = saved.slayers.computeIfAbsent(t.key(), k -> new Bucket());
		if (b.items == null) b.items = new LinkedHashMap<>();
		return b;
	}

	public void addItem(SlayerType t, String item, long count) {
		if (t == null || count <= 0) return;
		session(t).add(item, count);
		total(t).add(item, count);
		dirty = true;
	}

	public void addKill(SlayerType t, int tier, double costMultiplier) {
		if (t == null) return;
		long cost = t.motes ? 0 : Math.round(t.cost(tier) * costMultiplier);
		session(t).kills++;
		session(t).cost += cost;
		total(t).kills++;
		total(t).cost += cost;
		dirty = true;
	}

	public void addMiniboss(SlayerType t) {
		if (t == null) return;
		session(t).minibosses++;
		total(t).minibosses++;
		dirty = true;
	}

	public void addActiveTime(SlayerType t, long ms) {
		if (t == null || ms <= 0) return;
		session(t).activeMs += ms;
		total(t).activeMs += ms;
	}

	public void resetSession() {
		session.clear();
	}

	/** Beispieldaten (nur Session) für die HUD-Vorschau */
	public void onDemo(SlayerType t, Map<String, Long> items) {
		for (Map.Entry<String, Long> e : items.entrySet()) session(t).add(e.getKey(), e.getValue());
	}

	public void resetTotal(SlayerType t) {
		saved.slayers.remove(t.key());
		dirty = true;
	}

	public boolean consumeDirty() {
		boolean d = dirty;
		dirty = false;
		return d;
	}

	/** Liest die Zeilen einer Sack-Nachricht (Hover-Text) → Item → Anzahl (nur Zugänge). */
	public static Map<String, Long> parseSackText(String hover) {
		Map<String, Long> out = new LinkedHashMap<>();
		if (hover == null) return out;
		Matcher m = SACK_LINE.matcher(hover);
		while (m.find()) {
			if (!"+".equals(m.group(1))) continue;
			long n = Long.parseLong(m.group(2).replace(",", ""));
			out.merge(m.group(3).trim(), n, Long::sum);
		}
		return out;
	}
}
