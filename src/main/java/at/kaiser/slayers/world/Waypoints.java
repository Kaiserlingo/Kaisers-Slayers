package at.kaiser.slayers.world;

import java.util.ArrayList;
import java.util.List;

/** Kurzzeitige Ziele (Miniboss, Beacon), zu denen ein Pfeil im HUD zeigt. */
public final class Waypoints {
	public static final class Waypoint {
		public final String key;
		public final String label;
		public double x, y, z;
		public final int color;
		public long expiresAt;

		Waypoint(String key, String label, double x, double y, double z, int color, long expiresAt) {
			this.key = key;
			this.label = label;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.expiresAt = expiresAt;
		}
	}

	private static final List<Waypoint> LIST = new ArrayList<>();

	private Waypoints() {}

	/** Fügt einen Waypoint hinzu oder aktualisiert die Position eines bestehenden (gleicher key). */
	public static synchronized void put(String key, String label, double x, double y, double z, int color, long expiresAt) {
		for (Waypoint w : LIST) {
			if (w.key.equals(key)) {
				w.x = x;
				w.y = y;
				w.z = z;
				return;
			}
		}
		LIST.add(new Waypoint(key, label, x, y, z, color, expiresAt));
	}

	public static synchronized void remove(String key) {
		LIST.removeIf(w -> w.key.equals(key));
	}

	public static synchronized List<Waypoint> active(long now) {
		LIST.removeIf(w -> w.expiresAt < now);
		return new ArrayList<>(LIST);
	}

	public static synchronized void clear() {
		LIST.clear();
	}
}
