package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.slayer.ProfitTracker;
import at.kaiser.slayers.slayer.SlayerType;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.List;

/**
 * Bearbeiten-Modus des Profit-HUDs: Solange ein Inventar / eine Kiste offen ist, wird das normale Profit-HUD
 * an seiner Position bedienbar – Item anklicken = aus-/einblenden, SESSION/TOTAL-Chip umschalten,
 * Instant Sell ↔ Sell Offer, Reset; Karte ziehen = verschieben, Mausrad = Größe.
 * Ändert nur Einstellungen der Mod, klickt nie etwas im Spiel-Inventar an.
 */
public final class InventoryTracker {
	private static Screen editingScreen;
	private static List<ProfitHud.Hit> hits = List.of();
	/** Karten-Rechteck in Bildschirm-Koordinaten (für Klick/Ziehen/Scrollen) */
	private static float boxX, boxY, boxW, boxH, boxScale = 1;
	private static long resetArmedUntil;
	private static boolean dragging;
	private static double dragOffX, dragOffY;

	private InventoryTracker() {}

	/** Wird gerade ein Inventar mit bearbeitbarem Profit-HUD angezeigt? (dann zeichnet das normale HUD nicht doppelt) */
	public static boolean isEditing() {
		return editingScreen != null && Minecraft.getInstance().gui.screen() == editingScreen;
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?>)) return;
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> {
				try {
					render(s, g, mx, my);
				} catch (Throwable t) {
					hits = List.of();
				}
			});
			ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !click(event.x(), event.y()));
			ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
				if (!dragging) return true;
				dragging = false;
				KaisersSlayers.config().save();
				return false;
			});
			ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, hor, v) -> !scroll(x, y, v));
			ScreenEvents.remove(screen).register(s -> {
				if (editingScreen == s) editingScreen = null;
				if (dragging) KaisersSlayers.config().save();
				dragging = false;
				hits = List.of();
			});
		});
	}

	private static SlayerType activeType() {
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.inventoryTracker || !cfg.profitEnabled || !cfg.profitHud.enabled) return null;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return null;
		return KaisersSlayers.tracker().type();
	}

	private static void render(Screen screen, GuiGraphicsExtractor g, int mx, int my) {
		SlayerType type = activeType();
		if (type == null) {
			editingScreen = null;
			hits = List.of();
			return;
		}
		editingScreen = screen;
		ModConfig cfg = KaisersSlayers.config();
		Minecraft mc = Minecraft.getInstance();
		int sw = screen.width, sh = screen.height;

		if (dragging) {
			cfg.profitHud.x = clamp((float) ((mx - dragOffX) / sw), 0f, 1f - boxW / sw);
			cfg.profitHud.y = clamp((float) ((my - dragOffY) / sh), 0f, 1f - boxH / sh);
		}
		float scale = cfg.profitHud.scale;
		float x = Math.round(cfg.profitHud.x * sw), y = Math.round(cfg.profitHud.y * sh);
		// komplett sichtbar halten (die Karte ist im Inventar durch die Buttons etwas höher)
		if (!dragging && boxH > 0) {
			y = Math.max(2, Math.min(y, sh - boxH - 2));
			x = Math.max(2, Math.min(x, sw - boxW - 2));
		}

		ProfitTracker.Bucket b = ProfitHud.bucket(type, cfg);
		ProfitHud.Edit edit = new ProfitHud.Edit((mx - x) / scale, (my - y) / scale, System.currentTimeMillis() < resetArmedUntil);
		edit.toggleMode = () -> cfg.profitMode = "TOTAL".equals(cfg.profitMode) ? "SESSION" : "TOTAL";
		edit.togglePrice = () -> cfg.instantSellPrices = !cfg.instantSellPrices;
		edit.toggleItem = item -> {
			if (!cfg.hiddenItems.remove(item)) cfg.hiddenItems.add(item);
		};
		edit.reset = () -> {
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
		};

		g.nextStratum(); // über Slots und Items des Inventars zeichnen
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		int h = ProfitHud.draw(g, mc.font, type, b, cfg, edit);
		g.pose().popMatrix();

		boxX = x;
		boxY = y;
		boxScale = scale;
		boxW = ProfitHud.lastWidth * scale;
		boxH = h * scale;
		hits = edit.hits;

		// kleiner Hinweis über der Karte
		boolean hover = mx >= boxX && mx < boxX + boxW && my >= boxY && my < boxY + boxH;
		if (hover || dragging) {
			String tip = "§7Drag = move · Scroll = size · Click item = hide";
			float ts = 0.7f;
			float ty = boxY - 8 >= 0 ? boxY - 8 : boxY + boxH + 2;
			at.kaiser.slayers.gui.Ui.text(g, mc.font, tip, boxX, ty, ts, 0xFFFFFFFF, true);
		}
	}

	/** true = Klick gehörte zur Karte (dann bekommt das Inventar ihn nicht) */
	private static boolean click(double mx, double my) {
		if (activeType() == null || !inside(mx, my)) return false;
		double lx = (mx - boxX) / boxScale, ly = (my - boxY) / boxScale;
		for (ProfitHud.Hit h : List.copyOf(hits)) {
			if (h.action() != null && lx >= h.x1() && lx < h.x2() && ly >= h.y1() && ly < h.y2()) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
				h.action().run();
				KaisersSlayers.config().save();
				return true;
			}
		}
		// freie Fläche der Karte: verschieben
		dragging = true;
		dragOffX = mx - boxX;
		dragOffY = my - boxY;
		return true;
	}

	private static boolean scroll(double mx, double my, double amount) {
		if (activeType() == null || !inside(mx, my) || amount == 0) return false;
		ModConfig cfg = KaisersSlayers.config();
		cfg.profitHud.scale = clamp(Math.round((cfg.profitHud.scale + (amount > 0 ? 0.05f : -0.05f)) * 100) / 100f, 0.5f, 2.0f);
		cfg.save();
		return true;
	}

	private static boolean inside(double mx, double my) {
		return mx >= boxX && mx < boxX + boxW && my >= boxY && my < boxY + boxH;
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(Math.max(min, max), v));
	}
}
