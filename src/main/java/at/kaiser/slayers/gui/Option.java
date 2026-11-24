package at.kaiser.slayers.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Eine Einstellung im Menü. Jede Option ist ein Button. */
public abstract class Option {
	public final String label;
	public final String tooltip;

	protected Option(String label, String tooltip) {
		this.label = label;
		this.tooltip = tooltip;
	}

	/** Text auf dem Button */
	public abstract String text();

	/** Was beim Klick passiert */
	public abstract void click();

	/** Schalter an/aus */
	public static Option toggle(String label, String tooltip, Supplier<Boolean> get, Consumer<Boolean> set) {
		return new Option(label, tooltip) {
			@Override
			public String text() {
				return label + ": " + (get.get() ? "§aAN" : "§cAUS");
			}

			@Override
			public void click() {
				set.accept(!get.get());
			}
		};
	}

	/** Wert aus einer Liste durchschalten */
	public static <T> Option cycle(String label, String tooltip, List<T> values, Function<T, String> format,
								   Supplier<T> get, Consumer<T> set) {
		return new Option(label, tooltip) {
			@Override
			public String text() {
				return label + ": §e" + format.apply(get.get());
			}

			@Override
			public void click() {
				int idx = values.indexOf(get.get());
				set.accept(values.get((idx + 1) % values.size()));
			}
		};
	}

	/** Einfacher Knopf, z. B. "Vorschau" oder Untermenü öffnen */
	public static Option action(String label, String tooltip, Runnable run) {
		return new Option(label, tooltip) {
			@Override
			public String text() {
				return label;
			}

			@Override
			public void click() {
				run.run();
			}
		};
	}
}
