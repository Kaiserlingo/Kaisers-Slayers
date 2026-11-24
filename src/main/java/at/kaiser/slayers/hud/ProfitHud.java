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

/** Profit-Tracker als eigene, übersichtliche Karte: Drops mit Wert, Kosten, Gewinn, pro Stunde, pro Boss. */
public final class ProfitHud {
	public static final int WIDTH = 176;

	/** Eine Zeile der Auswertung */
	public record Row(String name, long count, double value, int tier, PriceService.Source source) {}

	private ProfitHud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.profitEnabled || !cfg.profitHud.enabled || mc.player == null || GamblingAnimation.hidesHud()) return;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return;
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

	public static int draw(GuiGraphicsExtractor g, Font font, SlayerType type, ProfitTracker.Bucket b, ModConfig cfg) {
		List<Row> rows = rows(type, b);
		double itemValue = 0;
		for (Row r : rows) itemValue += r.value();
		double profit = itemValue - b.cost;
		double perHour = b.perHour(profit);
		double perBoss = b.kills > 0 ? profit / b.kills : 0;

		// Zeilen auswählen: die wertvollsten N, Rest zusammenfassen
		List<Row> shown = new ArrayList<>();
		double restValue = 0;
		long restCount = 0;
		for (Row r : rows) {
			boolean hidden = cfg.hiddenItems.contains(r.name());
			if (!hidden && shown.size() < cfg.profitMaxItems && r.value() >= cfg.profitMinValue) shown.add(r);
			else if (!hidden) {
				restValue += r.value();
				restCount += r.count();
			}
		}

		int w = WIDTH;
		int lines = shown.size() + (restCount > 0 ? 1 : 0);
		int h = 22 + Math.max(1, lines) * 10 + 6 + 10 + 4 + 20 + 12;
		int gold = 0xFFFFC234;
		if (cfg.hudBackground) {
			Ui.panel(g, 0, 0, w, h, 0xE0121218, 0x90FFC234);
			Ui.hGradient(g, 1, 1, w - 1, 19, 0x60FFC234, 0x00121218);
		}
		g.text(font, "§6§l⛃ PROFIT", 6, 3, 0xFFFFFFFF, true);
		String mode = "TOTAL".equals(cfg.profitMode) ? "GESAMT" : "SESSION";
		int mw = (int) (font.width(mode) * 0.75f) + 6;
		Ui.round(g, w - mw - 5, 3, w - 5, 12, 0x60FFC234);
		Ui.text(g, font, mode, w - mw - 2, 4.5f, 0.75f, 0xFFFFE9A8, false);
		Ui.text(g, font, type.color + type.bossName + " §8· §7" + b.kills + " Bosse" + (b.minibosses > 0 ? " · " + b.minibosses + " Minis" : ""),
				6, 12, 0.75f, 0xFFFFFFFF, false);

		int y = 22;
		if (shown.isEmpty() && restCount == 0) {
			g.text(font, "§8Noch keine Drops", 6, y, 0xFFFFFFFF, true);
			y += 10;
		}
		for (Row r : shown) {
			int dot = LootPool.tierColor(r.tier());
			g.fill(6, y + 3, 9, y + 6, dot);
			String name = "§7" + TextUtil.formatInt(r.count()) + "x §f" + r.name();
			String val = switch (r.source()) {
				case BAZAAR -> "§6" + TextUtil.formatCoins(r.value());
				case AUCTION -> "§e" + TextUtil.formatCoins(r.value());
				case UNKNOWN -> "§8?";
			};
			int maxName = w - 18 - font.width(val) - 6;
			g.text(font, fit(font, name, maxName), 12, y, 0xFFFFFFFF, true);
			Ui.rightText(g, font, val, w - 6, y, 0xFFFFFFFF);
			y += 10;
		}
		if (restCount > 0) {
			g.text(font, "§8+ " + TextUtil.formatInt(restCount) + " weitere", 12, y, 0xFFFFFFFF, true);
			Ui.rightText(g, font, "§6" + TextUtil.formatCoins(restValue), w - 6, y, 0xFFFFFFFF);
			y += 10;
		}

		y += 3;
		g.fill(6, y, w - 6, y + 1, 0x40FFFFFF);
		y += 4;
		g.text(font, "§7Quest-Kosten", 6, y, 0xFFFFFFFF, true);
		Ui.rightText(g, font, "§c-" + TextUtil.formatCoins(b.cost), w - 6, y, 0xFFFFFFFF);
		y += 12;

		// Gewinn groß
		Ui.round(g, 4, y - 1, w - 4, y + 19, profit >= 0 ? 0x302ECC71 : 0x30E74C3C);
		Ui.text(g, font, "§7GEWINN", 8, y + 1, 0.75f, 0xFFFFFFFF, false);
		String big = (profit >= 0 ? "§a§l" : "§c§l") + TextUtil.formatCoins(profit);
		float bs = 1.5f;
		Ui.text(g, font, big, w - 8 - font.width(big) * bs, y + 3, bs, 0xFFFFFFFF, true);
		Ui.text(g, font, "§8" + TextUtil.formatDuration(b.activeMs), 8, y + 10, 0.75f, 0xFFFFFFFF, false);
		y += 22;
		g.text(font, "§7/h §6" + TextUtil.formatCoins(perHour) + "  §7/Boss §6" + TextUtil.formatCoins(perBoss), 6, y, gold, true);
		return h;
	}

	private static String fit(Font font, String s, int max) {
		if (font.width(s) <= max) return s;
		String t = s;
		while (t.length() > 3 && font.width(t + "…") > max) t = t.substring(0, t.length() - 1);
		return t + "…";
	}
}
