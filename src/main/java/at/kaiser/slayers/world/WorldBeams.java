package at.kaiser.slayers.world;

import java.util.ArrayList;
import java.util.List;

/** Lichtstrahlen, die gerade in der Welt gezeichnet werden sollen (wird vom Scanner befüllt). */
public final class WorldBeams {
	public record Beam(int x, int y, int z, int rgb) {}

	private static volatile List<Beam> current = List.of();

	private WorldBeams() {}

	public static void set(List<Beam> beams) {
		current = beams == null ? List.of() : new ArrayList<>(beams);
	}

	public static List<Beam> get() {
		return current;
	}

	public static void clear() {
		current = List.of();
	}
}
