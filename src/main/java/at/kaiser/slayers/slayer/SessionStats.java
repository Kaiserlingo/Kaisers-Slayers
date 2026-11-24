package at.kaiser.slayers.slayer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Statistik der aktuellen Spielsitzung (wird mit /kslayers reset zurückgesetzt). */
public class SessionStats {
	/** Aktive Slayer-Zeit in ms (Pausen > 2 min ohne Quest zählen nicht). */
	public long activeMs;
	public int kills;
	public int fails;
	public long coinCost;
	public long xpGained;
	public final List<Long> killTimes = new ArrayList<>();
	public final List<Long> spawnTimes = new ArrayList<>();
	public final List<Long> questTimes = new ArrayList<>();
	/** Itemname -> Anzahl */
	public final Map<String, Integer> drops = new LinkedHashMap<>();

	public void reset() {
		activeMs = 0;
		kills = 0;
		fails = 0;
		coinCost = 0;
		xpGained = 0;
		killTimes.clear();
		spawnTimes.clear();
		questTimes.clear();
		drops.clear();
	}

	public static long average(List<Long> values) {
		if (values.isEmpty()) return 0;
		long sum = 0;
		for (long v : values) sum += v;
		return sum / values.size();
	}

	public static long best(List<Long> values) {
		long best = Long.MAX_VALUE;
		for (long v : values) best = Math.min(best, v);
		return values.isEmpty() ? 0 : best;
	}

	public double killsPerHour() {
		if (activeMs < 30_000 || kills == 0) return 0;
		return kills / (activeMs / 3_600_000.0);
	}

	public void addDrop(String item, int count) {
		drops.merge(item, count, Integer::sum);
	}
}
