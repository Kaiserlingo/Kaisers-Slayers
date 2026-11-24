package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.gui.Ui;
import at.kaiser.slayers.mixin.AbstractContainerScreenAccessor;
import at.kaiser.slayers.slayer.ProfitTracker;
import at.kaiser.slayers.slayer.SlayerType;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Profit-Tracker neben jedem Inventar / jeder Kiste – mit Buttons zum Umschalten
 * (Session/Total, Instant Sell/Sell Offer) und zum Zurücksetzen der Session.
 * Nur Anzeige + eigene Einstellungen, klickt nie etwas im Spiel-Inventar an.
 */
public final class InventoryTracker {
	private static final int BTN_H = 14;
	private static final List<Button> BUTTONS = new ArrayList<>();
	private static long resetArmedUntil;

	private record Button(float x1, float y1, float x2, float y2, Runnable action) {
		boolean inside(double mx, double my) {
			return mx >= x1 && mx < x2 && my >= y1 && my < y2;
		}
	}

	/** Position + Skalierung des Panels (in GUI-Koordinaten) */
	private record Layout(float x, float y, float scale) {}

	private InventoryTracker() {}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
				try {
					render(s, g, mx, my);
				} catch (Throwable t) {
					BUTTONS.clear();
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(event.x(), event.y()));
		});
	}

	private static SlayerType activeType() {
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.inventoryTracker || !cfg.profitEnabled) return null;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return null;
		return KaisersSlayers.tracker().type();
	}

	private static void render(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
		BUTTONS.clear();
		SlayerType type = activeType();
		if (type == null) return;
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = KaisersSlayers.config();
		ProfitTracker.Bucket b = ProfitHud.bucket(type, cfg);
		Font font = mc.font;

		int panelH = ProfitHud.height(type, b, cfg);
		int totalH = panelH + 4 + 3 * (BTN_H + 2);
		Layout l = layout(screen, ProfitHud.WIDTH, totalH);
		if (l == null) return;

		g.pose().pushMatrix();
		g.pose().translate(l.x(), l.y());
		g.pose().scale(l.scale(), l.scale());
		boolean bg = cfg.hudBackground;
		cfg.hudBackground = true; // im Inventar immer mit Hintergrund (sonst unlesbar)
		ProfitHud.draw(g, font, type, b, cfg);
		cfg.hudBackground = bg;

		int w = ProfitHud.WIDTH;
		int y = panelH + 4;
		double lmx = (mx - l.x()) / l.scale(), lmy = (my - l.y()) / l.scale();
		boolean total = "TOTAL".equals(cfg.profitMode);
		segment(g, font, l, 0, y, w, "Session", "Total", !total, lmx, lmy, v -> {
			cfg.profitMode = v ? "SESSION" : "TOTAL";
			cfg.save();
		});
		y += BTN_H + 2;
		segment(g, font, l, 0, y, w, "Instant Sell", "Sell Offer", cfg.instantSellPrices, lmx, lmy, v -> {
			cfg.instantSellPrices = v;
			cfg.save();
		});
		y += BTN_H + 2;
		boolean armed = System.currentTimeMillis() < resetArmedUntil;
		String label = armed ? "§c§lClick again to reset!" : (total ? "Reset Total (" + type.levelName + ")" : "Reset Session");
		button(g, font, l, 0, y, w, y + BTN_H, label, armed ? 0xFF8A2A2A : 0xFF5A2A2A, lmx, lmy, () -> {
			if (System.currentTimeMillis() < resetArmedUntil) {
				resetArmedUntil = 0;
				if ("TOTAL".equals(cfg.profitMode)) {
					KaisersSlayers.tracker().profit().resetTotal(type);
					KaisersSlayers.chat("§7Total profit for " + type.bossName + " reset.");
				} else {
					KaisersSlayers.tracker().resetSession();
					KaisersSlayers.chat("§aSession reset.");
				}
				KaisersSlayers.saveAll();
			} else {
				resetArmedUntil = System.currentTimeMillis() + 3000;
			}
		});
		g.pose().popMatrix();
	}

	/** Links neben dem Inventar, sonst rechts daneben; verkleinert, wenn wenig Platz ist. */
	private static Layout layout(Screen screen, int w, int h) {
		if (!(screen instanceof AbstractContainerScreenAccessor acc)) return null;
		int left = acc.kaisersSlayers$getLeftPos();
		int top = acc.kaisersSlayers$getTopPos();
		int sw = screen.width, sh = screen.height;
		float spaceLeft = left - 8;
		float spaceRight = sw - (left + acc.kaisersSlayers$getImageWidth()) - 8;
		float space = Math.max(spaceLeft, spaceRight);
		float scale = Math.min(1f, Math.min(space / w, (sh - 8f) / h));
		if (scale < 0.45f) return null;
		float x = spaceLeft >= spaceRight ? left - 4 - w * scale : sw - 4 - w * scale;
		float y = Math.max(4, Math.min(top, sh - 4 - h * scale));
		return new Layout(x, y, scale);
	}

	private interface BoolAction {
		void accept(boolean v);
	}

	private static void segment(GuiGraphicsExtractor g, Font font, Layout l, int x, int y, int w, String a, String b, boolean aActive,
								double mx, double my, BoolAction set) {
		int half = (w - 2) / 2;
		int accent = 0xFF000000 | KaisersSlayers.config().accentColor;
		Ui.round(g, x, y, x + w, y + BTN_H, 0xF0121218);
		drawSeg(g, font, l, x + 1, y + 1, x + half, y + BTN_H - 1, a, aActive, accent, mx, my, () -> set.accept(true));
		drawSeg(g, font, l, x + w - half, y + 1, x + w - 1, y + BTN_H - 1, b, !aActive, accent, mx, my, () -> set.accept(false));
	}

	private static void drawSeg(GuiGraphicsExtractor g, Font font, Layout l, int x1, int y1, int x2, int y2, String text, boolean active,
								int accent, double mx, double my, Runnable action) {
		boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
		if (active) Ui.round(g, x1, y1, x2, y2, accent);
		else if (hover) Ui.round(g, x1, y1, x2, y2, 0x40FFFFFF);
		g.text(font, (active ? "§f§l" : hover ? "§f" : "§7") + text, (x1 + x2) / 2 - font.width((active ? "§l" : "") + text) / 2, (y1 + y2) / 2 - 4,
				0xFFFFFFFF, active);
		addButton(l, x1, y1, x2, y2, action);
	}

	private static void button(GuiGraphicsExtractor g, Font font, Layout l, int x1, int y1, int x2, int y2, String text, int color,
							   double mx, double my, Runnable action) {
		boolean hover = mx >= x1 && mx < x2 && my >= y1 && my < y2;
		Ui.round(g, x1, y1, x2, y2, hover ? Ui.mix(color, 0xFFFFFFFF, 0.2f) : color);
		g.text(font, "§f" + text, (x1 + x2) / 2 - font.width(text) / 2, (y1 + y2) / 2 - 4, 0xFFFFFFFF, true);
		addButton(l, x1, y1, x2, y2, action);
	}

	private static void addButton(Layout l, int x1, int y1, int x2, int y2, Runnable action) {
		BUTTONS.add(new Button(l.x() + x1 * l.scale(), l.y() + y1 * l.scale(), l.x() + x2 * l.scale(), l.y() + y2 * l.scale(), action));
	}

	/** true = Klick gehörte zu unserem Panel (dann bekommt das Inventar ihn nicht) */
	private static boolean click(double mx, double my) {
		if (activeType() == null) return false;
		for (Button b : List.copyOf(BUTTONS)) {
			if (b.inside(mx, my)) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
				b.action().run();
				return true;
			}
		}
		return false;
	}
}
