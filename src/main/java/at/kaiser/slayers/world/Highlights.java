package at.kaiser.slayers.world;

import java.util.HashMap;
import java.util.Map;

/**
 * Welche Entities gerade leuchten sollen (Entity-ID -> RGB-Farbe).
 * Wird vom WorldScanner neu befüllt und von den Mixins gelesen.
 */
public final class Highlights {
	private static volatile Map<Integer, Integer> current = Map.of();
	/** Eigene Ebene für Carry-Bosse (wird vom CarryScanner befüllt und hat Vorrang) */
	private static volatile Map<Integer, Integer> carry = Map.of();

	private Highlights() {}

	public static void set(Map<Integer, Integer> next) {
		current = next == null ? Map.of() : new HashMap<>(next);
	}

	public static void clear() {
		current = Map.of();
	}

	public static void setCarry(Map<Integer, Integer> next) {
		carry = next == null ? Map.of() : new HashMap<>(next);
	}

	/** RGB-Farbe oder -1, wenn das Entity nicht markiert ist. */
	public static int color(int entityId) {
		Integer c = carry.get(entityId);
		if (c == null) c = current.get(entityId);
		return c == null ? -1 : c;
	}
}
