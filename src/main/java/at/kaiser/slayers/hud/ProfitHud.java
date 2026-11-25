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
	/** Höhe beim letzten Zeichnen (zum Im-Bild-Halten) */
	public static int lastHeight = 0;
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
		// immer ganz im Bild halten (z. B. nach Auflösungs-Wechsel oder wenn die Karte breiter wird)
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		x = Math.max(0, Math.min(x, sw - Math.round(lastWidth * cfg.profitHud.scale) - 1));
		y = Math.max(0, Math.min(y, sh - Math.round(lastHeight * cfg.profitHud.scale) - 1));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(cfg.profitHud.scale, cfg.profitHud.scale);
		lastHeight = draw(g, mc.font, type, b, cfg);
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

	/** Farbe eines Item-Namens: Coins gold, sonst die echte Seltenheits-Farbe aus dem Inventar, sonst die Drop-Seltenheit */
	private static int nameColor(Row r) {
		if (ProfitTracker.MOB_COINS.equals(r.name())) return 0xFFFFAA00;
		int learned = KaisersSlayers.tracker().profit().colorOf(r.name());
		if (learned >= 0) return 0xFF000000 | learned;
		return r.tier() <= 0 ? 0xFFFFFFFF : LootPool.tierColor(r.tier());
	}

	/** Wann sich die Anzahl eines Items zuletzt erhöht hat (für die kurze grüne Hervorhebung) */
	private static final Map<String, long[]> LAST_COUNT = new java.util.HashMap<>();

	private static boolean recentlyGained(Row r, long now) {
		long[] v = LAST_COUNT.get(r.name());
		if (v == null) {
			LAST_COUNT.put(r.name(), new long[]{r.count(), 0});
			return false;
		}
		if (r.count() > v[0]) v[1] = now;
		v[0] = r.count();
		return v[1] > 0 && now - v[1] < 4000;
	}

	public static int draw(GuiGraphicsExtractor g, Font font, SlayerType type, ProfitTracker.Bucket b, ModConfig cfg) {
		return draw(g, font, type, b, cfg, null);
	}

	/**
	 * Zeichnet die Karte bei (0,0) und gibt ihre Höhe zurück. Breite steht danach in {@link #lastWidth}.
	 * Stil: gelber Titel, Tabelle "Anzahl · Item (Seltenheits-Farbe) · Wert", darunter Kosten, Bosse, Profit, pro Stunde, Uptime.
	 */
	public static int draw(GuiGraphicsExtractor g, Font font, SlayerType type, ProfitTracker.Bucket b, ModConfig cfg, Edit edit) {
		long now = System.currentTimeMillis();
		List<Row> rows = rows(type, b);
		double itemValue = 0;
		for (Row r : rows) itemValue += r.value();
		double profit = itemValue - b.cost;
		double perHour = b.perHour(profit);
		boolean total = "TOTAL".equals(cfg.profitMode);

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

		// ---------- Texte ----------
		String title = "§e§l" + type.bossName + " " + TextUtil.toRoman(KaisersSlayers.tracker().tier()) + " Profit Tracker";
		String mode = total ? "Total" : "Session";
		List<String> stats = new ArrayList<>();
		stats.add("§7Slayer Spawn Costs: §c-" + TextUtil.formatCoins(b.cost));
		stats.add("§7Bosses killed: §e" + b.kills + (b.minibosses > 0 ? " §8· §7Minibosses: §e" + b.minibosses : ""));
		stats.add("§6" + mode + " Profit: " + (profit >= 0 ? "§6" : "§c") + TextUtil.formatInt(Math.round(profit)) + " coins");
		stats.add("§6Profit Per Hour: " + (perHour >= 0 ? "§6" : "§c") + TextUtil.formatInt(Math.round(perHour)) + " coins");
		stats.add("§6Total Uptime: §b" + TextUtil.formatDuration(b.activeMs));

		// ---------- Spalten ----------
		int countW = 0, nameW = 0, valueW = 0;
		List<Row> all = new ArrayList<>(shown);
		all.addAll(hiddenRows);
		for (Row r : all) {
			countW = Math.max(countW, Ui.width(font, "§l" + TextUtil.formatInt(r.count()) + "x"));
			nameW = Math.max(nameW, Ui.width(font, r.name()));
			valueW = Math.max(valueW, Ui.width(font, valueText(r)));
		}
		if (restCount > 0) {
			countW = Math.max(countW, Ui.width(font, "+" + TextUtil.formatInt(restCount)));
			nameW = Math.max(nameW, Ui.width(font, "more items"));
		}
		int countX = PAD + 4 + countW;          // Anzahl rechtsbündig bis hier
		int nameX = countX + 4;
		nameW = Math.min(nameW, 150);
		int valueX = nameX + nameW + 8;         // Werte linksbündig in eigener Spalte
		int w = Math.max(WIDTH, valueX + valueW + PAD);
		w = Math.max(w, PAD * 2 + Ui.width(font, title) + 4);
		for (String st : stats) w = Math.max(w, PAD * 2 + Ui.width(font, st));
		w = Math.min(MAX_WIDTH + 40, w);
		lastWidth = w;

		int itemLines = Math.max(1, shown.size() + (restCount > 0 ? 1 : 0)) + hiddenRows.size();
		int h = 15 + itemLines * LINE + 4 + stats.size() * LINE + 3;
		if (edit != null) h += 20;

		if (cfg.hudBackground || edit != null) {
			// schlicht: dunkle Fläche, kaum Rand (im Bearbeiten-Modus deckend + goldener Rand)
			Ui.panel(g, 0, 0, w, h, edit != null ? 0xF8101015 : 0xB0000000, edit != null ? 0xC0FFC234 : 0x20FFFFFF);
		}

		// ---------- Titel ----------
		Ui.draw(g, font, title, PAD, 4, 0xFFFFFFFF, true);
		if (edit != null) {
			// Session/Total umschalten (klickbarer Chip rechts oben)
			int chipW = Ui.width(font, mode) + 8;
			int cx2 = w - PAD, cx1 = cx2 - chipW;
			boolean hover = edit.hover(cx1, 2, cx2, 13);
			Ui.round(g, cx1, 2, cx2, 13, hover ? 0xA0FFC234 : 0x50FFC234);
			Ui.draw(g, font, "§f" + mode, cx1 + 4, 4, 0xFFFFFFFF, false);
			edit.hits.add(new Hit(cx1, 2, cx2, 13, edit.toggleMode));
		}

		// ---------- Items ----------
		int y = 16;
		if (shown.isEmpty() && restCount == 0 && hiddenRows.isEmpty()) {
			Ui.draw(g, font, "§8No drops yet", PAD + 4, y, 0xFFFFFFFF, true);
			y += LINE;
		}
		for (Row r : shown) y = itemRow(g, font, r, y, w, countX, nameX, nameW, valueX, false, edit, now);
		if (restCount > 0) {
			String c = "+" + TextUtil.formatInt(restCount);
			Ui.draw(g, font, "§8" + c, countX - Ui.width(font, c), y, 0xFFFFFFFF, true);
			Ui.draw(g, font, "§8more items", nameX, y, 0xFFFFFFFF, true);
			Ui.draw(g, font, "§6" + TextUtil.formatCoins(restValue), valueX, y, 0xFFFFFFFF, true);
			y += LINE;
		}
		for (Row r : hiddenRows) y = itemRow(g, font, r, y, w, countX, nameX, nameW, valueX, true, edit, now);

		// ---------- Statistik ----------
		y += 4;
		for (String st : stats) {
			Ui.draw(g, font, st, PAD, y, 0xFFFFFFFF, true);
			y += LINE;
		}

		// ---------- Bearbeiten-Leiste (nur im Inventar) ----------
		if (edit != null) {
			y += 3;
			int bw = (w - PAD * 2 - 4) / 2;
			String price = cfg.instantSellPrices ? "Instant Sell" : "Sell Offer";
			button(g, font, edit, PAD, y, PAD + bw, y + 13, "§e⇄ §f" + price, 0xFF2A2A36, edit.togglePrice);
			String reset = edit.resetArmed ? "§c§lConfirm?" : "§fReset " + mode;
			button(g, font, edit, w - PAD - bw, y, w - PAD, y + 13, reset, edit.resetArmed ? 0xFF8A2A2A : 0xFF4A2228, edit.reset);
		}
		return h;
	}

	private static int itemRow(GuiGraphicsExtractor g, Font font, Row r, int y, int w, int countX, int nameX, int nameW, int valueX,
							   boolean hidden, Edit edit, long now) {
		boolean hover = edit != null && edit.hover(2, y - 1, w - 2, y + LINE - 1);
		if (hover) g.fill(2, y - 1, w - 2, y + LINE - 1, 0x25FFFFFF);
		boolean fresh = !hidden && recentlyGained(r, now);
		String c = (fresh ? "§a§l" : "§7") + TextUtil.formatInt(r.count()) + "x";
		String name = fit(font, r.name(), nameW);
		Ui.draw(g, font, hidden ? "§8" + c.substring(2) : c, countX - Ui.width(font, c), y, 0xFFFFFFFF, true);
		if (hidden) {
			Ui.draw(g, font, "§8§m" + name, nameX, y, 0xFFFFFFFF, true);
			Ui.draw(g, font, "§8hidden", valueX, y, 0xFFFFFFFF, true);
		} else {
			Ui.draw(g, font, name, nameX, y, nameColor(r), true);
			Ui.draw(g, font, valueText(r), valueX, y, 0xFFFFFFFF, true);
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


	private static void button(GuiGraphicsExtractor g, Font font, Edit edit, int x1, int y1, int x2, int y2, String text, int color, Runnable action) {
		boolean hover = edit.hover(x1, y1, x2, y2);
		Ui.round(g, x1, y1, x2, y2, hover ? Ui.mix(color, 0xFFFFFFFF, 0.2f) : color);
		String t = fit(font, text, x2 - x1 - 4);
		Ui.draw(g, font, t, (x1 + x2) / 2 - Ui.width(font, t) / 2, y1 + 3, 0xFFFFFFFF, true);
		edit.hits.add(new Hit(x1, y1, x2, y2, action));
	}

	private static String fit(Font font, String s, int max) {
		if (Ui.width(font, s) <= max) return s;
		String t = s;
		while (t.length() > 3 && Ui.width(font, t + "…") > max) t = t.substring(0, t.length() - 1);
		return t + "…";
	}
}
