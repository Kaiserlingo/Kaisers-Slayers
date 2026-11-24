package at.kaiser.slayers.slayer;

import java.util.LinkedHashMap;
import java.util.Map;

/** Dauerhafte Statistik pro Slayer-Typ (wird als JSON gespeichert). */
public class LifetimeStats {
	public Map<String, Entry> slayers = new LinkedHashMap<>();

	public Entry get(SlayerType type) {
		return slayers.computeIfAbsent(type.key(), k -> new Entry());
	}

	public static class Entry {
		public int kills;
		public int fails;
		public long totalKillMs;
		public int timedKills;
		public long bestKillMs;
		public int level = -1;
		public long xpToNextLevel = -1;
		public long rngMeterXp = -1;
		public Map<String, Integer> drops = new LinkedHashMap<>();

		public long averageKillMs() {
			return timedKills == 0 ? 0 : totalKillMs / timedKills;
		}
	}
}
