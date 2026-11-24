package at.kaiser.slayers.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/** Eine Einstellung im Menü. Die Unterklassen bestimmen, welches Bedienelement rechts angezeigt wird. */
public abstract sealed class Option permits Option.Toggle, Option.Choice, Option.Slider, Option.Color, Option.Action, Option.Header {
	public final String label;
	public final String description;
	/** Für die Suche: in welcher Kategorie steht die Option */
	public String category = "";

	protected Option(String label, String description) {
		this.label = label;
		this.description = description == null ? "" : description;
	}

	public boolean matches(String query) {
		String q = query.toLowerCase(java.util.Locale.ROOT);
		return strip(label).toLowerCase(java.util.Locale.ROOT).contains(q) || description.toLowerCase(java.util.Locale.ROOT).contains(q);
	}

	static String strip(String s) {
		return s.replaceAll("§.", "");
	}

	// ------------------------------------------------------------------ Typen

	public static final class Toggle extends Option {
		public final Supplier<Boolean> get;
		public final Consumer<Boolean> set;
		public float anim = -1;

		Toggle(String label, String desc, Supplier<Boolean> get, Consumer<Boolean> set) {
			super(label, desc);
			this.get = get;
			this.set = set;
		}
	}

	public static final class Choice<T> extends Option {
		public final List<T> values;
		public final Function<T, String> format;
		public final Supplier<T> get;
		public final Consumer<T> set;

		Choice(String label, String desc, List<T> values, Function<T, String> format, Supplier<T> get, Consumer<T> set) {
			super(label, desc);
			this.values = values;
			this.format = format;
			this.get = get;
			this.set = set;
		}

		public void step(int dir) {
			int idx = values.indexOf(get.get());
			if (idx < 0) idx = 0;
			else idx = Math.floorMod(idx + dir, values.size());
			set.accept(values.get(idx));
		}

		public String text() {
			return format.apply(get.get());
		}
	}

	public static final class Slider extends Option {
		public final double min, max, step;
		public final Supplier<Double> get;
		public final Consumer<Double> set;
		public final DoubleFunction<String> format;

		Slider(String label, String desc, double min, double max, double step, Supplier<Double> get, Consumer<Double> set,
			   DoubleFunction<String> format) {
			super(label, desc);
			this.min = min;
			this.max = max;
			this.step = step;
			this.get = get;
			this.set = set;
			this.format = format;
		}

		public void setFraction(double f) {
			double v = min + Math.max(0, Math.min(1, f)) * (max - min);
			v = Math.round(v / step) * step;
			set.accept(Math.max(min, Math.min(max, v)));
		}

		public double fraction() {
			return (get.get() - min) / (max - min);
		}
	}

	public static final class Color extends Option {
		/** Schnellauswahl im Farbwähler (Minecraft-Farben + ein paar kräftige Extras) */
		public static final List<Integer> PALETTE = List.of(
				0xFF5555, 0xAA0000, 0xFFAA00, 0xFFD700, 0xFFFF55, 0x55FF55, 0x00AA00, 0x55FFFF,
				0x00AAAA, 0x5555FF, 0x0000AA, 0xAA00FF, 0xFF55FF, 0xFFFFFF, 0xAAAAAA, 0x555555);
		public final Supplier<Integer> get;
		public final Consumer<Integer> set;
		/** Standardfarbe (für "Reset"), kann null sein */
		public final Integer defaultValue;

		Color(String label, String desc, Supplier<Integer> get, Consumer<Integer> set, Integer defaultValue) {
			super(label, desc);
			this.get = get;
			this.set = set;
			this.defaultValue = defaultValue;
		}

		public int value() {
			return get.get() & 0xFFFFFF;
		}

		public static String hex(int rgb) {
			return String.format(java.util.Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
		}

		/** "#ff8800", "ff8800" oder "f80" → RGB, sonst -1 */
		public static int parseHex(String s) {
			if (s == null) return -1;
			String t = s.trim();
			if (t.startsWith("#")) t = t.substring(1);
			if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
			if (t.length() != 6) return -1;
			try {
				return Integer.parseInt(t, 16);
			} catch (NumberFormatException e) {
				return -1;
			}
		}

		/** HSV (je 0..1) → RGB */
		public static int hsv(float h, float s, float v) {
			h = (h % 1f + 1f) % 1f;
			float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c;
			float r, g, b;
			switch ((int) (h * 6)) {
				case 0 -> { r = c; g = x; b = 0; }
				case 1 -> { r = x; g = c; b = 0; }
				case 2 -> { r = 0; g = c; b = x; }
				case 3 -> { r = 0; g = x; b = c; }
				case 4 -> { r = x; g = 0; b = c; }
				default -> { r = c; g = 0; b = x; }
			}
			return Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
		}

		/** RGB → {h, s, v} (je 0..1) */
		public static float[] toHsv(int rgb) {
			float r = (rgb >> 16 & 0xFF) / 255f, g = (rgb >> 8 & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
			float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
			float h;
			if (d == 0) h = 0;
			else if (max == r) h = ((g - b) / d) / 6f;
			else if (max == g) h = ((b - r) / d + 2) / 6f;
			else h = ((r - g) / d + 4) / 6f;
			if (h < 0) h += 1;
			return new float[]{h, max == 0 ? 0 : d / max, max};
		}
	}

	public static final class Action extends Option {
		public final String button;
		public final Runnable run;

		Action(String label, String desc, String button, Runnable run) {
			super(label, desc);
			this.button = button;
			this.run = run;
		}
	}

	public static final class Header extends Option {
		Header(String text) {
			super(text, "");
		}

		@Override
		public boolean matches(String query) {
			return false;
		}
	}

	// ------------------------------------------------------------------ Fabriken

	public static Option toggle(String label, String desc, Supplier<Boolean> get, Consumer<Boolean> set) {
		return new Toggle(label, desc, get, set);
	}

	public static <T> Option choice(String label, String desc, List<T> values, Function<T, String> format, Supplier<T> get, Consumer<T> set) {
		return new Choice<>(label, desc, values, format, get, set);
	}

	public static Option slider(String label, String desc, double min, double max, double step, Supplier<Double> get,
								Consumer<Double> set, DoubleFunction<String> format) {
		return new Slider(label, desc, min, max, step, get, set, format);
	}

	public static Option color(String label, String desc, Supplier<Integer> get, Consumer<Integer> set) {
		return new Color(label, desc, get, set, null);
	}

	public static Option color(String label, String desc, Supplier<Integer> get, Consumer<Integer> set, int defaultValue) {
		return new Color(label, desc, get, set, defaultValue);
	}

	public static Option action(String label, String desc, String button, Runnable run) {
		return new Action(label, desc, button, run);
	}

	public static Option header(String text) {
		return new Header(text);
	}
}
