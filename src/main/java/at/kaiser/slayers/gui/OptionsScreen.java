package at.kaiser.slayers.gui;

import at.kaiser.slayers.KaisersSlayers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * Allgemeiner Einstellungs-Bildschirm: zeigt Optionen als Buttons in zwei Spalten.
 * Bei vielen Optionen gibt es Seiten (◀ ▶).
 */
public class OptionsScreen extends Screen {
	private static final int BUTTON_W = 170;
	private static final int BUTTON_H = 20;
	private static final int GAP = 4;

	protected final Screen parent;
	private final String subtitle;
	private final Supplier<List<Option>> options;
	private int page;

	public OptionsScreen(Screen parent, String title, String subtitle, Supplier<List<Option>> options) {
		super(Component.literal(title));
		this.parent = parent;
		this.subtitle = subtitle;
		this.options = options;
	}

	@Override
	protected void init() {
		List<Option> opts = options.get();
		int top = 46;
		int rowsFit = Math.max(1, (this.height - top - 40) / (BUTTON_H + GAP));
		int perPage = rowsFit * 2;
		int pages = Math.max(1, (opts.size() + perPage - 1) / perPage);
		page = Math.min(page, pages - 1);

		int left = this.width / 2 - BUTTON_W - GAP / 2;
		int from = page * perPage;
		int to = Math.min(opts.size(), from + perPage);
		for (int i = from; i < to; i++) {
			Option o = opts.get(i);
			int idx = i - from;
			int col = idx % 2, row = idx / 2;
			int x = left + col * (BUTTON_W + GAP);
			int y = top + row * (BUTTON_H + GAP);
			Button.Builder b = Button.builder(Component.literal(o.text()), btn -> {
				o.click();
				KaisersSlayers.config().save();
				btn.setMessage(Component.literal(o.text()));
				onOptionChanged();
			}).bounds(x, y, BUTTON_W, BUTTON_H);
			if (o.tooltip != null && !o.tooltip.isEmpty()) b.tooltip(Tooltip.create(Component.literal(o.tooltip)));
			this.addRenderableWidget(b.build());
		}

		int bottom = this.height - 28;
		if (pages > 1) {
			this.addRenderableWidget(Button.builder(Component.literal("◀"), btn -> {
				page = (page - 1 + pages) % pages;
				rebuild();
			}).bounds(this.width / 2 - 154, bottom, 40, BUTTON_H).build());
			this.addRenderableWidget(Button.builder(Component.literal("▶"), btn -> {
				page = (page + 1) % pages;
				rebuild();
			}).bounds(this.width / 2 + 114, bottom, 40, BUTTON_H).build());
		}
		this.addRenderableWidget(Button.builder(Component.literal(parent instanceof MainMenuScreen ? "Zurück" : "Fertig"),
				btn -> onClose()).bounds(this.width / 2 - 100, bottom, 200, BUTTON_H).build());
	}

	/** Für Unterklassen: z. B. Menü neu aufbauen, wenn sich die Anzahl der Optionen ändert. */
	protected void onOptionChanged() {
	}

	protected void rebuild() {
		this.clearWidgets();
		this.init();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		String t = this.title.getString();
		graphics.text(this.font, t, this.width / 2 - this.font.width(t) / 2, 14, 0xFFFFFFFF, true);
		if (subtitle != null) {
			graphics.text(this.font, subtitle, this.width / 2 - this.font.width(subtitle) / 2, 28, 0xFFAAAAAA, true);
		}
	}

	@Override
	public void onClose() {
		KaisersSlayers.config().save();
		this.minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
