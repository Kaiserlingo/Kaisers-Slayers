package at.kaiser.slayers.world;

import java.util.List;

/**
 * Leuchtende Kästen in der Welt (gefüllt, durch Wände sichtbar) – z. B. für Voidgloom-Beacons und Nukekubi-Skulls.
 * Entweder an eine feste Position (Block) oder an ein Entity gebunden (folgt ihm jeden Frame).
 */
public final class Boxes {
	/** entityId &gt;= 0: Kasten um den Kopf des Entities; sonst fester Block bei x/y/z */
	public record Box(int entityId, int x, int y, int z, int rgb) {
		public static Box block(int x, int y, int z, int rgb) {
			return new Box(-1, x, y, z, rgb);
		}

		public static Box head(int entityId, int rgb) {
			return new Box(entityId, 0, 0, 0, rgb);
		}
	}

	private static volatile List<Box> current = List.of();

	private Boxes() {}

	public static void set(List<Box> next) {
		current = next == null ? List.of() : List.copyOf(next);
	}

	public static void clear() {
		current = List.of();
	}

	public static List<Box> get() {
		return current;
	}
}
