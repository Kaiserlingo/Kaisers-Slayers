package at.kaiser.slayers.world;

import java.util.List;

/**
 * Linien vom Spieler zu Entities (z. B. Minibossen). Wird vom WorldScanner befüllt
 * und jeden Frame von {@link at.kaiser.slayers.render.WorldRender} gezeichnet.
 */
public final class Tracers {
	/** Linie zu einem Entity in einer RGB-Farbe */
	public record Tracer(int entityId, int rgb) {}

	private static volatile List<Tracer> current = List.of();

	private Tracers() {}

	public static void set(List<Tracer> next) {
		current = next == null ? List.of() : List.copyOf(next);
	}

	public static void clear() {
		current = List.of();
	}

	public static List<Tracer> get() {
		return current;
	}
}
