package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gambling.LootPool;
import at.kaiser.slayers.gui.Ui;
import at.kaiser.slayers.price.PriceService;
import at.kaiser.slayers.slayer.ProfitTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Profit-Tracker als schlichte Tabelle: Anzahl · Item (in Drop-Farbe) · Wert, darunter Kosten, Bosse, Profit, pro Stunde, Zeit.
 * Im Inventar wird dieselbe Karte im Bearbeiten-Modus gezeichnet ({@link Edit}): Items anklicken zum Ausblenden,
 * Session/Total und Preis-Modus umschalten, Reset – siehe {@link InventoryTracker}.
 */
public final class ProfitHud {
	/** Mindestbreite der Karte; die echte Breite passt sich den Item-Namen an */
	public static final int WIDTH = 150;
	private static final int MAX_WIDTH = 240;
	private static final int PAD = 5;
	private static final int LINE = 10;
	/** Breite der zuletzt gezeichneten Karte (für den HUD-Editor) */
	public static int lastWidth = WIDTH;

	/** Eine Zeile der Auswertung */
	public record Row(String name, long count, double value, int tier, PriceService.Source source) {}

	/** Klickbare Fläche im Bearbeiten-Modus (lokale Koordinaten der Karte) */
	public record Hit(int x1, int y1, int x2, int y2, Runnable action) {}

	/** Bearbeiten-Modus: sammelt klickbare Flächen und kennt die Mausposition (lokal) */
	public static final class Edit {
		public final List<Hit> hits = new ArrayList<>();
		public final double mx, my;
		public final boolean resetArmed;
		public Runnable toggleMode, togglePrice, reset;
		public java.util.function.Consumer<String> toggleItem;

		public Edit(double mx, double my, boolean resetArmed) {
			this.mx = mx;
			this.my = my;
			this.resetArmed = resetArmed;
		}

		boolean hover(int x1, int y1, int x2, int y2) {
			return mx >= x1 && mx < x2 && my >= y1 && my < y2;
		}
	}

	private ProfitHud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.profitEnabled || !cfg.profitHud.enabled || mc.player == null || GamblingAnimation.hidesHud()) return;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return;
		if (InventoryTracker.isEditing()) return; // im Inventar zeichnet der Bearbeiten-Modus die Karte
		SlayerType type = KaisersSlayers.tracker().type();
		if (type == null) return;
		ProfitTracker.Bucket b = bucket(type, cfg);
		if (b.kills == 0 && b.items.isEmpty()) return;

		int x = Math.round(cfg.profitHud.x * mc.getWindow().getGuiScaledWidth());
		int y = Math.round(cfg.profitHud.y * mc.getWindow().getGuiScaledHeight());
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(cfg.profitHud.scale, cfg.profitHud.scale);
		draw(g, mc.font, type, b, cfg);
		g.pose().popMatrix();
	}

	public static ProfitTracker.Bucket bucket(SlayerType type, ModConfig cfg) {
		ProfitTracker p = KaisersSlayers.tracker().profit();
		return "TOTAL".equals(cfg.profitMode) ? p.total(type) : p.session(type);
	}

	/** Wert eines Items (0, wenn ausgeblendet) */
	public static double value(String item, long count) {
		ModConfig cfg = KaisersSlayers.config();
		if (cfg.hiddenItems.contains(item)) return 0;
		return KaisersSlayers.prices().price(item, cfg.instantSellPrices).value() * count;
	}

	/** Alle Items sortiert nach Wert */
	public static List<Row> rows(SlayerType type, ProfitTracker.Bucket b) {
		ModConfig cfg = KaisersSlayers.config();
		List<Row> rows = new ArrayList<>();
		List<LootPool.Entry> pool = LootPool.build(type, cfg.extraItems(type), null);
		for (Map.Entry<String, Long> e : b.items.entrySet()) {
			PriceService.Price p = KaisersSlayers.prices().price(e.getKey(), cfg.instantSellPrices);
			double v = cfg.hiddenItems.contains(e.getKey()) ? 0 : p.value() * e.getValue();
			rows.add(new Row(e.getKey(), e.getValue(), v, LootPool.tierOf(pool, e.getKey(), 1), p.source()));
		}
		rows.sort((a, c) -> Double.compare(c.value(), a.value()));
		return rows;
	}

	/** Farbe eines Item-Namens: Coins gold, sonst die Drop-Seltenheit */
	private static int nameColor(Row r) {
		if (ProfitTracker.MOB_COINS.equals(r.name())) return 0xFFFFAA00;
		return r.tier() <= 0 ? 0xFFFFFFFF : LootPool.tierColor(r.tier());
	}

	public static int draw(GuiGraphicsExtractor g, Font font, SlayerType type, ProfitTracker.Bucket b, ModConfig cfg) {
		return draw(g, font, type, b, cfg, null);
	}

	/** Zeichnet die Karte bei (0,0) und gibt ihre Höhe zurück. Breite steht danach in {@link #lastWidth}. */
	public static int draw(GuiGraphicsExtractor g, Font font, SlayerType type, ProfitTracker.Bucket b, ModConfig cfg, Edit edit) {
		List<Row> rows = rows(type, b);
		double itemValue = 0;
		for (Row r : rows) itemValue += r.value();
		double profit = itemValue - b.cost;
		double perHour = b.perHour(profit);

		// Zeilen: die wertvollsten N einzeln, Rest zusammengefasst; ausgeblendete nur im Bearbeiten-Modus
		List<Row> shown = new ArrayList<>();
		List<Row> hiddenRows = new ArrayList<>();
		double restValue = 0;
		long restCount = 0;
		for (Row r : rows) {
			if (cfg.hiddenItems.contains(r.name())) {
				if (edit != null) hiddenRows.add(r);
			} else if (shown.size() < cfg.profitMaxItems && r.value() >= cfg.profitMinValue) {
				shown.add(r);
			} else {
				restValue += r.value();
				restCount += r.count();
			}
		}

		// ---------- Spaltenbreiten ----------
		String title = "§l" + type.bossName + " " + TextUtil.toRoman(KaisersSlayers.tracker().tier());
		String mode = "TOTAL".equals(cfg.profitMode) ? "TOTAL" : "SESSION";
		int chipW = (int) (font.width(mode) * 0.75f) + 8;
		int countW = 0, nameW = 0, valueW = 0;
		List<Row> all = new ArrayList<>(shown);
		all.addAll(hiddenRows);
		for (Row r : all) {
			countW = Math.max(countW, font.width(TextUtil.formatInt(r.count()) + "x"));
			nameW = Math.max(nameW, font.width(r.name()));
			valueW = Math.max(valueW, font.width(valueText(r)));
		}
		if (restCount > 0) {
			countW = Math.max(countW, font.width("+" + TextUtil.formatInt(restCount)));
			nameW = Math.max(nameW, font.width("more"));
		}
		int titleW = font.width(title) + 12 + chipW;
		int w = Math.max(WIDTH, Math.max(titleW, countW + nameW + valueW + 16) + PAD * 2);
		w = Math.min(MAX_WIDTH, w);
		int nameMax = w - PAD * 2 - countW - valueW - 14;
		lastWidth = w;

		// ---------- Höhe ----------
		int itemLines = Math.max(1, shown.size() + (restCount > 0 ? 1 : 0)) + hiddenRows.size();
		int statLines = 5;
		int h = 16 + itemLines * LINE + 5 + statLines * LINE + 3;
		if (edit != null) h += 20;

		if (cfg.hudBackground || edit != null) {
			Ui.panel(g, 0, 0, w, h, edit != null ? 0xF8101015 : 0xD8101015, edit != null ? 0xC0FFC234 : 0x60FFC234);
		}

		// ---------- Titel ----------
		g.text(font, type.color + title, PAD, 4, 0xFFFFFFFF, true);
		int cx2 = w - PAD, cx1 = cx2 - chipW;
		boolean chipHover = edit != null && edit.hover(cx1, 2, cx2, 13);
		Ui.round(g, cx1, 3, cx2, 12, chipHover ? 0xA0FFC234 : 0x50FFC234);
		Ui.text(g, font, mode, cx1 + 4, 5f, 0.75f, 0xFFFFE9A8, false);
		if (edit != null) edit.hits.add(new Hit(cx1, 2, cx2, 13, edit.toggleMode));

		// ---------- Items ----------
		int y = 16;
		int countX = PAD + countW, nameX = countX + 5;
		if (shown.isEmpty() && restCount == 0 && hiddenRows.isEmpty()) {
			g.text(font, "§8No drops yet", PAD, y, 0xFFFFFFFF, true);
			y += LINE;
		}
		for (Row r : shown) {
			y = itemRow(g, font, r, y, w, countX, nameX, nameMax, false, edit);
		}
		if (restCount > 0) {
			String c = "+" + TextUtil.formatInt(restCount);
			g.text(font, "§8" + c, countX - font.width(c), y, 0xFFFFFFFF, true);
			g.text(font, "§8more", nameX, y, 0xFFFFFFFF, true);
			Ui.rightText(g, font, "§6" + TextUtil.formatCoins(restValue), w - PAD, y, 0xFFFFFFFF);
			y += LINE;
		}
		for (Row r : hiddenRows) {
			y = itemRow(g, font, r, y, w, countX, nameX, nameMax, true, edit);
		}

		// ---------- Statistik ----------
		y += 2;
		g.fill(PAD, y, w - PAD, y + 1, 0x30FFFFFF);
		y += 3;
		stat(g, font, "Quest Cost", "§c-" + TextUtil.formatCoins(b.cost), y, w);
		y += LINE;
		stat(g, font, "Bosses Killed", "§e" + b.kills + (b.minibosses > 0 ? " §8· §e" + b.minibosses + " §7Minis" : ""), y, w);
		y += LINE;
		stat(g, font, "§f§lProfit", (profit >= 0 ? "§a§l" : "§c§l") + TextUtil.formatInt(Math.round(profit)) + " §r§7coins", y, w);
		y += LINE;
		stat(g, font, "Profit/Hour", "§6" + TextUtil.formatCoins(perHour), y, w);
		y += LINE;
		stat(g, font, "Uptime", "§b" + TextUtil.formatDuration(b.activeMs), y, w);
		y += LINE;

		// ---------- Bearbeiten-Leiste (nur im Inventar) ----------
		if (edit != null) {
			y += 3;
			int bw = (w - PAD * 2 - 4) / 2;
			String price = cfg.instantSellPrices ? "Instant Sell" : "Sell Offer";
			button(g, font, edit, PAD, y, PAD + bw, y + 13, "§e⇄ §f" + price, 0xFF2A2A36, edit.togglePrice);
			String reset = edit.resetArmed ? "§c§lConfirm?" : "§fReset " + ("TOTAL".equals(cfg.profitMode) ? "Total" : "Session");
			button(g, font, edit, w - PAD - bw, y, w - PAD, y + 13, reset, edit.resetArmed ? 0xFF8A2A2A : 0xFF4A2228, edit.reset);
		}
		return h;
	}

	private static int itemRow(GuiGraphicsExtractor g, Font font, Row r, int y, int w, int countX, int nameX, int nameMax,
							   boolean hidden, Edit edit) {
		boolean hover = edit != null && edit.hover(2, y - 1, w - 2, y + LINE - 1);
		if (hover) g.fill(2, y - 1, w - 2, y + LINE - 1, 0x25FFFFFF);
		String c = TextUtil.formatInt(r.count()) + "x";
		String name = fit(font, r.name(), nameMax);
		if (hidden) {
			g.text(font, "§8" + c, countX - font.width(c), y, 0xFFFFFFFF, true);
			g.text(font, "§8§m" + name, nameX, y, 0xFFFFFFFF, true);
			Ui.rightText(g, font, "§8hidden", w - PAD, y, 0xFFFFFFFF);
		} else {
			g.text(font, "§7" + c, countX - font.width(c), y, 0xFFFFFFFF, true);
			g.text(font, name, nameX, y, nameColor(r), true);
			Ui.rightText(g, font, valueText(r), w - PAD, y, 0xFFFFFFFF);
		}
		if (edit != null && edit.toggleItem != null) {
			String item = r.name();
			edit.hits.add(new Hit(2, y - 1, w - 2, y + LINE - 1, () -> edit.toggleItem.accept(item)));
		}
		return y + LINE;
	}

	private static String valueText(Row r) {
		return switch (r.source()) {
			case BAZAAR -> "§6" + TextUtil.formatCoins(r.value());
			case AUCTION -> "§e" + TextUtil.formatCoins(r.value());
			case UNKNOWN -> "§8–";
		};
	}

	private static void stat(GuiGraphicsExtractor g, Font font, String label, String value, int y, int w) {
		g.text(font, "§7" + label, PAD, y, 0xFFFFFFFF, true);
		Ui.rightText(g, font, value, w - PAD, y, 0xFFFFFFFF);
	}

	private static void button(GuiGraphicsExtractor g, Font font, Edit edit, int x1, int y1, int x2, int y2, String text, int color, Runnable action) {
		boolean hover = edit.hover(x1, y1, x2, y2);
		Ui.round(g, x1, y1, x2, y2, hover ? Ui.mix(color, 0xFFFFFFFF, 0.2f) : color);
		String t = fit(font, text, x2 - x1 - 4);
		g.text(font, t, (x1 + x2) / 2 - font.width(t) / 2, y1 + 3, 0xFFFFFFFF, true);
		edit.hits.add(new Hit(x1, y1, x2, y2, action));
	}

	private static String fit(Font font, String s, int max) {
		if (font.width(s) <= max) return s;
		String t = s;
		while (t.length() > 3 && font.width(t + "…") > max) t = t.substring(0, t.length() - 1);
		return t + "…";
	}
}
