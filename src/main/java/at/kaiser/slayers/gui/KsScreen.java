package at.kaiser.slayers.gui;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.hud.ProfitHud;
import at.kaiser.slayers.slayer.LifetimeStats;
import at.kaiser.slayers.slayer.ProfitTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Das Kaiser's-Slayers-Menü (/ks): Seitenleiste mit Kategorien, Suche, Schalter, Regler, Farbwahl,
 * Übersicht mit allen Slayern und eine Profit-Tabelle. Komplett selbst gezeichnet.
 */
public class KsScreen extends Screen {
	private static final int SIDEBAR_W = 122;
	private static final int HEADER_H = 34;
	private static final int FOOTER_H = 26;
	private static final int ROW_H = 26;
	private static final int SIDE_ROW = 16;
	private static final int HEADER_ROW_H = 18;
	private static int lastCategory = 0;

	private int category = lastCategory;
	private double scroll, scrollTarget;
	private double sideScroll;
	private String search = "";
	private boolean searchFocused;
	private Option.Slider dragging;
	private int dragX1, dragX2;
	private float openAnim;
	private long opened;
	private final List<HitBox> hits = new ArrayList<>();

	// ---------- Farbwähler (Popup) ----------
	private static final int PICK_W = 230, PICK_H = 166, SV_W = 120, SV_H = 90;
	private Option.Color picking;
	private int pickOriginal;
	private float pickH, pickS, pickV;
	private String hexInput = "";
	private boolean hexFocused;
	private int pickDrag; // 0 = nichts, 1 = Farbfeld, 2 = Farbton-Leiste
	private final List<HitBox> pickHits = new ArrayList<>();

	private record HitBox(int x1, int y1, int x2, int y2, Runnable left, Runnable right) {}

	public KsScreen() {
		super(Component.literal("Kaiser's Slayers"));
	}

	// ============================================================ Layout

	private int halfW() { return Math.min(300, (width - 12) / 2); }
	private int halfH() { return Math.min(200, (height - 12) / 2); }
	private int px1() { return width / 2 - halfW(); }
	private int px2() { return width / 2 + halfW(); }
	private int py1() { return height / 2 - halfH(); }
	private int py2() { return height / 2 + halfH(); }
	private int accent() { return 0xFF000000 | KaisersSlayers.config().accentColor; }

	@Override
	protected void init() {
		opened = System.currentTimeMillis();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		this.extractBlurredBackground(g);
		g.fill(0, 0, width, height, 0x90050508);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		hits.clear();
		pickHits.clear();
		openAnim = Math.min(1f, (System.currentTimeMillis() - opened) / 180f);
		scroll += (scrollTarget - scroll) * 0.35;
		ModConfig cfg = KaisersSlayers.config();
		int x1 = px1(), y1 = py1(), x2 = px2(), y2 = py2();
		int acc = accent();

		g.pose().pushMatrix();
		float s = 0.94f + 0.06f * ease(openAnim);
		g.pose().translate(width / 2f, height / 2f);
		g.pose().scale(s, s);
		g.pose().translate(-width / 2f, -height / 2f);

		Ui.panel(g, x1, y1, x2, y2, 0xF2111117, Ui.argb(acc, 0x80));

		// ---------- Kopfzeile ----------
		Ui.hGradient(g, x1 + 1, y1 + 1, x2 - 1, y1 + HEADER_H, Ui.argb(acc, 0x50), 0x00111117);
		g.fill(x1 + 1, y1 + HEADER_H, x2 - 1, y1 + HEADER_H + 1, Ui.argb(acc, 0x60));
		Ui.icon(g, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD), x1 + 8, y1 + 9, 1.0f);
		Ui.text(g, font, "§lKAISER'S SLAYERS", x1 + 28, y1 + 8, 1.35f, 0xFFFFFFFF, true);
		Ui.text(g, font, "§8Slayer companion for Hypixel SkyBlock · v" + KaisersSlayers.version(), x1 + 28, y1 + 22, 0.7f, 0xFFFFFFFF, false);

		// Suchfeld
		int sx2 = x2 - 8, sx1 = sx2 - 130, sy1 = y1 + 9, sy2 = sy1 + 16;
		Ui.round(g, sx1, sy1, sx2, sy2, searchFocused ? Ui.argb(acc, 0xFF) : 0xFF2A2A36);
		Ui.round(g, sx1 + 1, sy1 + 1, sx2 - 1, sy2 - 1, 0xFF0E0E13);
		String shown = search.isEmpty() && !searchFocused ? "§8🔍 Search..." : "§f" + search + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		g.text(font, shown, sx1 + 5, sy1 + 4, 0xFFFFFFFF, false);
		hits.add(new HitBox(sx1, sy1, sx2, sy2, () -> searchFocused = true, () -> {
			search = "";
			searchFocused = true;
		}));

		// ---------- Seitenleiste ----------
		int sbx2 = x1 + SIDEBAR_W;
		g.fill(x1 + 1, y1 + HEADER_H + 1, sbx2, y2 - 1, 0x60000000);
		g.fill(sbx2, y1 + HEADER_H + 1, sbx2 + 1, y2 - 1, 0x30FFFFFF);
		int sideTop = y1 + HEADER_H + 4, sideBottom = y2 - 4;
		int sideContent = Menus.CATEGORIES.size() * SIDE_ROW + 12;
		double maxSide = Math.max(0, sideContent - (sideBottom - sideTop));
		sideScroll = Math.max(0, Math.min(maxSide, sideScroll));
		g.enableScissor(x1 + 1, sideTop, sbx2, sideBottom);
		int cy = sideTop + 2 - (int) sideScroll;
		for (int i = 0; i < Menus.CATEGORIES.size(); i++) {
			Menus.Category c = Menus.CATEGORIES.get(i);
			if (i == Menus.FIRST_SLAYER) {
				Ui.text(g, font, "§8SLAYER", x1 + 10, cy + 3, 0.7f, 0xFFFFFFFF, false);
				g.fill(x1 + 38, cy + 5, sbx2 - 8, cy + 6, 0x20FFFFFF);
				cy += 10;
			}
			int ry1 = cy, ry2 = cy + SIDE_ROW - 1;
			boolean active = i == category && search.isEmpty();
			boolean hover = Ui.inside(mx, my, x1 + 4, ry1, sbx2 - 4, ry2) && my >= sideTop && my < sideBottom;
			if (active) {
				Ui.round(g, x1 + 4, ry1, sbx2 - 4, ry2, Ui.argb(c.accent(), 0x40));
				g.fill(x1 + 4, ry1 + 2, x1 + 6, ry2 - 2, c.accent());
			} else if (hover) {
				Ui.round(g, x1 + 4, ry1, sbx2 - 4, ry2, 0x20FFFFFF);
			}
			Ui.icon(g, c.icon(), x1 + 9, ry1 + 1.5f, 0.75f);
			String label = c.slayer() != null ? c.slayer().bossName : c.name();
			g.text(font, (active ? "§f§l" : hover ? "§f" : "§7") + fit(font, label, SIDEBAR_W - 34), x1 + 25, ry1 + 4, 0xFFFFFFFF, false);
			final int idx = i;
			if (ry2 > sideTop && ry1 < sideBottom) hits.add(new HitBox(x1 + 4, Math.max(ry1, sideTop), sbx2 - 4, Math.min(ry2, sideBottom), () -> select(idx), null));
			cy += SIDE_ROW;
		}
		g.disableScissor();
		if (maxSide > 0) {
			int vh = sideBottom - sideTop;
			int bh = Math.max(12, vh * vh / sideContent);
			int by = sideTop + (int) ((vh - bh) * (sideScroll / maxSide));
			g.fill(sbx2 - 3, by, sbx2 - 1, by + bh, 0x50FFFFFF);
		}

		// ---------- Inhalt ----------
		int cx1 = sbx2 + 10, cx2 = x2 - 10, cy1 = y1 + HEADER_H + 6, cy2 = y2 - FOOTER_H;
		Menus.Category cat = Menus.CATEGORIES.get(category);
		String title = search.isEmpty() ? (cat.slayer() != null ? cat.slayer().color + "§l" + cat.slayer().bossName : "§f§l" + cat.name())
				: "§f§lSearch: §7\"" + search + "\"";
		Ui.text(g, font, title, cx1, cy1 + 2, 1.2f, 0xFFFFFFFF, true);
		int listTop = cy1 + 18;

		g.enableScissor(cx1 - 2, listTop, cx2 + 2, cy2);
		int contentH;
		if (search.isEmpty() && cat.dashboard()) {
			contentH = drawDashboard(g, cx1, listTop - (int) scroll, cx2, mx, my, listTop, cy2);
		} else {
			List<Option> opts = currentOptions();
			contentH = drawOptions(g, opts, cx1, listTop - (int) scroll, cx2, mx, my, listTop, cy2, cat.profitTable() && search.isEmpty());
		}
		g.disableScissor();

		// Scrollbalken
		int viewH = cy2 - listTop;
		double maxScroll = Math.max(0, contentH - viewH);
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
		if (maxScroll > 0) {
			int barH = Math.max(16, (int) (viewH * viewH / (double) contentH));
			int barY = listTop + (int) ((viewH - barH) * (scroll / maxScroll));
			Ui.round(g, cx2 + 4, barY, cx2 + 7, barY + barH, Ui.argb(acc, 0x90));
		}
		// sanfte Kanten oben/unten
		g.fillGradient(cx1 - 2, listTop, cx2 + 2, listTop + 6, 0xFF111117, 0x00111117);
		g.fillGradient(cx1 - 2, cy2 - 6, cx2 + 2, cy2, 0x00111117, 0xFF111117);

		// ---------- Fußzeile ----------
		g.fill(sbx2 + 1, y2 - FOOTER_H, x2 - 1, y2 - FOOTER_H + 1, 0x30FFFFFF);
		button(g, mx, my, x2 - 78, y2 - 21, x2 - 8, y2 - 5, "Done", acc, this::onClose);
		button(g, mx, my, x2 - 168, y2 - 21, x2 - 84, y2 - 5, "HUD Editor", 0xFF3A3A48,
				() -> minecraft.gui.setScreen(new HudEditorScreen(this)));
		String hint = "§8Right-click = back · Scroll = scroll";
		if (font.width(hint) * 0.7f < (x2 - 172) - (sbx2 + 10)) Ui.text(g, font, hint, sbx2 + 10, y2 - 15, 0.7f, 0xFFFFFFFF, false);

		if (picking != null) drawPicker(g, mx, my);
		g.pose().popMatrix();
	}

	private List<Option> currentOptions() {
		if (!search.isEmpty()) {
			List<Option> all = new ArrayList<>();
			for (Menus.Category c : Menus.CATEGORIES) {
				for (Option o : Menus.options(c)) if (o.matches(search)) all.add(o);
			}
			return all;
		}
		return cachedOptions();
	}

	private List<Option> cached;
	private int cachedFor = -1;

	private List<Option> cachedOptions() {
		if (cachedFor != category) {
			cached = Menus.options(Menus.CATEGORIES.get(category));
			cachedFor = category;
		}
		return cached;
	}

	/** Für automatische Tests */
	public void selectCategory(int idx) {
		select(idx);
	}

	private void select(int idx) {
		category = idx;
		lastCategory = idx;
		search = "";
		searchFocused = false;
		scroll = scrollTarget = 0;
		click();
	}

	// ============================================================ Optionen zeichnen

	/**
	 * Optionen in Abschnitten: Abschnitts-Titel, darunter EINE Karte mit allen Zeilen (getrennt durch feine Linien).
	 * Weniger Kästen = ruhigeres, übersichtlicheres Menü.
	 */
	private int drawOptions(GuiGraphicsExtractor g, List<Option> opts, int x1, int y, int x2, int mx, int my, int clipTop, int clipBottom, boolean profitTable) {
		int startY = y;
		int acc = accent();
		if (opts.isEmpty()) {
			g.text(font, "§8Nothing found.", x1, y + 4, 0xFFFFFFFF, false);
			return 20;
		}
		int i = 0;
		while (i < opts.size()) {
			// Abschnitts-Titel
			if (opts.get(i) instanceof Option.Header hd) {
				if (y > startY) y += 6;
				String t = hd.label.toUpperCase(java.util.Locale.ROOT);
				Ui.text(g, font, "§l" + t, x1 + 2, y + 2, 0.7f, Ui.argb(acc, 0xFF), false);
				y += 11;
				i++;
				continue;
			}
			// alle Zeilen bis zum nächsten Titel gehören in eine Karte
			int j = i;
			while (j < opts.size() && !(opts.get(j) instanceof Option.Header)) j++;
			int cardTop = y, cardBottom = y + (j - i) * ROW_H;
			if (cardBottom > clipTop && cardTop < clipBottom) Ui.round(g, x1, cardTop, x2, cardBottom, 0xFF1A1A22);
			for (int k = i; k < j; k++) {
				Option o = opts.get(k);
				int ry1 = y, ry2 = y + ROW_H;
				boolean inClip = ry2 > clipTop && ry1 < clipBottom;
				if (inClip) {
					boolean hover = Ui.inside(mx, my, x1, ry1, x2, ry2) && my >= clipTop && my < clipBottom;
					if (hover) g.fill(x1 + 1, ry1 + (k == i ? 1 : 0), x2 - 1, ry2 - (k == j - 1 ? 1 : 0), 0x12FFFFFF);
					if (k > i) g.fill(x1 + 8, ry1, x2 - 8, ry1 + 1, 0x12FFFFFF);
					String label = o.label;
					if (!search.isEmpty() && !o.category.isEmpty()) label = label + " §8· " + o.category;
					int ctrlW = controlWidth(o);
					int textMax = x2 - x1 - ctrlW - 24;
					g.text(font, "§f" + fit(font, label, textMax), x1 + 8, ry1 + 5, 0xFFFFFFFF, false);
					String desc = fit(font, o.description, (int) (textMax / 0.7f));
					Ui.text(g, font, desc, x1 + 8, ry1 + 16, 0.7f, 0xFF8A8A98, false);
					drawControl(g, o, x2 - 8 - ctrlW, ry1 + ROW_H / 2, ctrlW, mx, my, acc, clipTop, clipBottom);
				}
				y += ROW_H;
			}
			y += 2;
			i = j;
		}
		if (profitTable) y = drawProfitTable(g, x1, y + 4, x2, mx, my, clipTop, clipBottom);
		return y - startY + 6;
	}

	private int controlWidth(Option o) {
		return switch (o) {
			case Option.Toggle t -> 24;
			case Option.Choice<?> c -> 104;
			case Option.Slider sl -> 120;
			case Option.Color c -> 74;
			case Option.Action a -> Math.max(56, font.width(a.button) + 16);
			default -> 0;
		};
	}

	private void drawControl(GuiGraphicsExtractor g, Option o, int x, int cy, int w, int mx, int my, int acc, int clipTop, int clipBottom) {
		boolean mouseIn = my >= clipTop && my < clipBottom;
		switch (o) {
			case Option.Toggle t -> {
				boolean on = t.get.get();
				if (t.anim < 0) t.anim = on ? 1 : 0;
				t.anim += ((on ? 1 : 0) - t.anim) * 0.3f;
				Ui.toggle(g, x + 1, cy - 5, t.anim, acc);
				hits.add(new HitBox(x - 4, cy - 10, x + w + 4, cy + 10, () -> {
					t.set.accept(!t.get.get());
					changed();
				}, null));
			}
			case Option.Choice<?> c -> {
				Ui.round(g, x, cy - 8, x + w, cy + 8, 0xFF0E0E13);
				boolean hl = mouseIn && Ui.inside(mx, my, x, cy - 8, x + 16, cy + 8);
				boolean hr = mouseIn && Ui.inside(mx, my, x + w - 16, cy - 8, x + w, cy + 8);
				g.text(font, (hl ? "§f" : "§8") + "◀", x + 5, cy - 4, 0xFFFFFFFF, false);
				g.text(font, (hr ? "§f" : "§8") + "▶", x + w - 11, cy - 4, 0xFFFFFFFF, false);
				String v = fit(font, c.text(), w - 34);
				g.text(font, "§e" + v, x + w / 2 - font.width(v) / 2, cy - 4, 0xFFFFFFFF, false);
				hits.add(new HitBox(x, cy - 8, x + w / 2, cy + 8, () -> {
					c.step(-1);
					changed();
				}, () -> {
					c.step(1);
					changed();
				}));
				hits.add(new HitBox(x + w / 2, cy - 8, x + w, cy + 8, () -> {
					c.step(1);
					changed();
				}, () -> {
					c.step(-1);
					changed();
				}));
			}
			case Option.Slider sl -> {
				String v = sl.format.apply(sl.get.get());
				int tw = font.width(v);
				int bx1 = x, bx2 = x + w - tw - 8;
				Ui.round(g, bx1, cy - 2, bx2, cy + 2, 0xFF0E0E13);
				int fx = bx1 + (int) ((bx2 - bx1) * Math.max(0, Math.min(1, sl.fraction())));
				Ui.round(g, bx1, cy - 2, Math.max(bx1 + 2, fx), cy + 2, acc);
				Ui.round(g, fx - 3, cy - 5, fx + 3, cy + 5, 0xFFFFFFFF);
				g.text(font, "§f" + v, x + w - tw, cy - 4, 0xFFFFFFFF, false);
				hits.add(new HitBox(bx1 - 4, cy - 8, bx2 + 4, cy + 8, () -> {
					dragging = sl;
					dragX1 = bx1;
					dragX2 = bx2;
					sl.setFraction((mouseX() - bx1) / (double) (bx2 - bx1));
					changed();
				}, null));
			}
			case Option.Color c -> {
				int col = 0xFF000000 | c.value();
				boolean hover = mouseIn && Ui.inside(mx, my, x, cy - 8, x + w, cy + 8);
				Ui.round(g, x, cy - 8, x + w, cy + 8, hover ? 0xFF2A2A36 : 0xFF0E0E13);
				Ui.round(g, x + 3, cy - 5, x + 21, cy + 5, 0xFFFFFFFF);
				Ui.round(g, x + 4, cy - 4, x + 20, cy + 4, col);
				g.text(font, (hover ? "§f" : "§7") + Option.Color.hex(c.value()), x + 25, cy - 4, 0xFFFFFFFF, false);
				hits.add(new HitBox(x, cy - 8, x + w, cy + 8, () -> openPicker(c), () -> openPicker(c)));
			}
			case Option.Action a -> {
				button(g, mx, my, x, cy - 8, x + w, cy + 8, a.button, acc, () -> {
					a.run.run();
					KaisersSlayers.config().save();
				});
			}
			default -> {}
		}
	}

	private int drawProfitTable(GuiGraphicsExtractor g, int x1, int y, int x2, int mx, int my, int clipTop, int clipBottom) {
		ModConfig cfg = KaisersSlayers.config();
		SlayerType type = KaisersSlayers.tracker().type() != null ? KaisersSlayers.tracker().type() : SlayerType.ZOMBIE;
		ProfitTracker.Bucket b = ProfitHud.bucket(type, cfg);
		List<ProfitHud.Row> rows = ProfitHud.rows(type, b);
		g.text(font, type.color + type.bossName + " §8· " + ("TOTAL".equals(cfg.profitMode) ? "Total" : "Session"), x1 + 2, y, 0xFFFFFFFF, false);
		y += 12;
		if (rows.isEmpty()) {
			g.text(font, "§8No drops tracked yet.", x1 + 2, y, 0xFFFFFFFF, false);
			return y + 12;
		}
		for (ProfitHud.Row r : rows) {
			boolean hidden = cfg.hiddenItems.contains(r.name());
			boolean hover = Ui.inside(mx, my, x1, y - 1, x2, y + 11) && my >= clipTop && my < clipBottom;
			if (hover) Ui.round(g, x1, y - 2, x2, y + 11, 0x20FFFFFF);
			g.fill(x1 + 3, y + 2, x1 + 6, y + 5, at.kaiser.slayers.gambling.LootPool.tierColor(r.tier()));
			g.text(font, (hidden ? "§8§m" : "§f") + TextUtil.formatInt(r.count()) + "x " + r.name(), x1 + 10, y, 0xFFFFFFFF, false);
			Ui.rightText(g, font, hidden ? "§8hidden" : "§6" + TextUtil.formatCoins(r.value()), x2 - 4, y, 0xFFFFFFFF);
			String name = r.name();
			hits.add(new HitBox(x1, y - 1, x2, y + 11, () -> {
				if (!cfg.hiddenItems.remove(name)) cfg.hiddenItems.add(name);
				changed();
			}, null));
			y += 12;
		}
		double profit = b.profit((n, c) -> ProfitHud.value(n, c));
		y += 4;
		g.text(font, "§7Cost §c-" + TextUtil.formatCoins(b.cost) + "   §7Profit " + (profit >= 0 ? "§a" : "§c") + TextUtil.formatCoins(profit)
				+ "   §7/h §6" + TextUtil.formatCoins(b.perHour(profit)), x1 + 2, y, 0xFFFFFFFF, false);
		y += 14;
		button(g, mx, my, x1, y, x1 + 110, y + 16, "Reset Total", 0xFF5A2A2A, () -> {
			KaisersSlayers.tracker().profit().resetTotal(type);
			KaisersSlayers.chat("§7Total profit for " + type.bossName + " reset.");
		});
		return y + 20;
	}

	// ============================================================ Übersicht

	private int drawDashboard(GuiGraphicsExtractor g, int x1, int y, int x2, int mx, int my, int clipTop, int clipBottom) {
		int startY = y;
		int gap = 6, cols = 2;
		int cw = (x2 - x1 - gap) / cols, ch = 58;
		SlayerType[] types = SlayerType.values();
		for (int i = 0; i < types.length; i++) {
			SlayerType t = types[i];
			int cx = x1 + (i % cols) * (cw + gap), cy = y + (i / cols) * (ch + gap);
			boolean hover = Ui.inside(mx, my, cx, cy, cx + cw, cy + ch) && my >= clipTop && my < clipBottom;
			int acc = Ui.slayerAccent(t);
			Ui.round(g, cx, cy, cx + cw, cy + ch, hover ? Ui.CARD_HOVER : Ui.CARD);
			Ui.hGradient(g, cx + 1, cy + 1, cx + cw - 1, cy + 3, acc, Ui.argb(acc, 0x00));
			Ui.icon(g, Ui.slayerIcon(t), cx + 6, cy + 8, 1.5f);
			LifetimeStats.Entry e = KaisersSlayers.tracker().lifetime().get(t);
			g.text(font, t.color + "§l" + fit(font, t.bossName, cw - 42), cx + 34, cy + 8, 0xFFFFFFFF, true);
			String lvl = e.level >= 0 ? "LVL " + e.level : "LVL ?";
			Ui.text(g, font, "§7" + t.levelName + " Slayer · " + lvl, cx + 34, cy + 19, 0.75f, 0xFFFFFFFF, false);
			ProfitTracker.Bucket total = KaisersSlayers.tracker().profit().total(t);
			double profit = total.profit((n, c) -> ProfitHud.value(n, c));
			Ui.text(g, font, "§7Kills §f" + e.kills + "   §7PB §a" + (e.bestKillMs > 0 ? TextUtil.formatSeconds(e.bestKillMs) : "-"),
					cx + 8, cy + 34, 0.75f, 0xFFFFFFFF, false);
			Ui.text(g, font, "§7Total profit " + (profit >= 0 ? "§6" : "§c") + TextUtil.formatCoins(profit)
					+ (e.rngMeterXp >= 0 ? "   §7RNG §d" + TextUtil.formatCoins(e.rngMeterXp) : ""), cx + 8, cy + 45, 0.75f, 0xFFFFFFFF, false);
			final int idx = Menus.FIRST_SLAYER + i;
			hits.add(new HitBox(cx, cy, cx + cw, cy + ch, () -> select(idx), null));
		}
		int rows = (types.length + cols - 1) / cols;
		y += rows * (ch + gap) + 4;
		ModConfig cfg = KaisersSlayers.config();
		Ui.round(g, x1, y, x2, y + 30, Ui.CARD);
		g.text(font, "§f§lSession", x1 + 8, y + 5, 0xFFFFFFFF, false);
		var s = KaisersSlayers.tracker().session();
		Ui.text(g, font, "§7Bosses §f" + s.kills + "  §7per hour §f" + String.format(java.util.Locale.ROOT, "%.1f", s.killsPerHour())
				+ "  §7Time §f" + TextUtil.formatDuration(s.activeMs) + "  §7Gambling " + (cfg.gamblingEnabled ? "§aON" : "§cOFF"), x1 + 8, y + 17, 0.75f, 0xFFFFFFFF, false);
		return y - startY + 36;
	}

	// ============================================================ Farbwähler

	/** Für automatische Tests */
	public void openPickerForTest(int index) {
		List<Option> opts = currentOptions();
		int n = 0;
		for (Option o : opts) {
			if (o instanceof Option.Color c && n++ == index) {
				openPicker(c);
				return;
			}
		}
	}

	private void openPicker(Option.Color c) {
		picking = c;
		pickOriginal = c.value();
		float[] hsv = Option.Color.toHsv(pickOriginal);
		pickH = hsv[0];
		pickS = hsv[1];
		pickV = hsv[2];
		hexInput = Option.Color.hex(pickOriginal).substring(1);
		hexFocused = false;
		pickDrag = 0;
		click();
	}

	private void closePicker(boolean keep) {
		if (picking == null) return;
		if (!keep) picking.set.accept(pickOriginal);
		picking = null;
		pickDrag = 0;
		hexFocused = false;
		changed();
	}

	/** Neue Farbe sofort übernehmen (Live-Vorschau in der Welt) */
	private void applyPick(int rgb, boolean updateHsv) {
		if (picking == null) return;
		rgb &= 0xFFFFFF;
		picking.set.accept(rgb);
		if (updateHsv) {
			float[] hsv = Option.Color.toHsv(rgb);
			if (hsv[1] > 0 && hsv[2] > 0) pickH = hsv[0]; // Farbton bei Grau/Schwarz behalten
			pickS = hsv[1];
			pickV = hsv[2];
		}
		if (!hexFocused) hexInput = Option.Color.hex(rgb).substring(1);
	}

	private int pickX1() { return width / 2 - PICK_W / 2; }
	private int pickY1() { return height / 2 - PICK_H / 2; }

	private void drawPicker(GuiGraphicsExtractor g, int mx, int my) {
		int acc = accent();
		g.fill(px1(), py1(), px2(), py2(), 0x99000000);
		int x1 = pickX1(), y1 = pickY1(), x2 = x1 + PICK_W, y2 = y1 + PICK_H;
		Ui.panel(g, x1, y1, x2, y2, 0xFA16161E, Ui.argb(acc, 0xC0));
		g.text(font, "§f§l" + fit(font, picking.label, PICK_W - 16), x1 + 8, y1 + 7, 0xFFFFFFFF, false);

		// Farbfeld (Sättigung → rechts, Helligkeit → oben)
		int sx = x1 + 8, sy = y1 + 22;
		for (int i = 0; i < SV_W; i += 2) {
			int top = 0xFF000000 | Option.Color.hsv(pickH, i / (float) (SV_W - 1), 1f);
			g.fillGradient(sx + i, sy, sx + i + 2, sy + SV_H, top, 0xFF000000);
		}
		int kx = sx + Math.round(pickS * (SV_W - 1)), ky = sy + Math.round((1 - pickV) * (SV_H - 1));
		g.fill(kx - 3, ky - 3, kx + 4, ky - 2, 0xFFFFFFFF);
		g.fill(kx - 3, ky + 3, kx + 4, ky + 4, 0xFFFFFFFF);
		g.fill(kx - 3, ky - 2, kx - 2, ky + 3, 0xFFFFFFFF);
		g.fill(kx + 3, ky - 2, kx + 4, ky + 3, 0xFFFFFFFF);
		pickHits.add(new HitBox(sx - 2, sy - 2, sx + SV_W + 2, sy + SV_H + 2, () -> {
			pickDrag = 1;
			dragPick(lastMouseX, lastMouseY);
		}, null));

		// Farbton-Leiste
		int hx = sx + SV_W + 6;
		for (int j = 0; j < SV_H; j++) g.fill(hx, sy + j, hx + 12, sy + j + 1, 0xFF000000 | Option.Color.hsv(j / (float) SV_H, 1f, 1f));
		int hy = sy + Math.round(pickH * (SV_H - 1));
		g.fill(hx - 2, hy - 1, hx + 14, hy + 2, 0xFFFFFFFF);
		g.fill(hx, hy, hx + 12, hy + 1, 0xFF000000 | Option.Color.hsv(pickH, 1f, 1f));
		pickHits.add(new HitBox(hx - 2, sy - 2, hx + 14, sy + SV_H + 2, () -> {
			pickDrag = 2;
			dragPick(lastMouseX, lastMouseY);
		}, null));

		// rechte Spalte: Vorschau alt/neu, Hex-Feld, Schnellauswahl
		int rx = hx + 20, rw = x2 - 8 - rx;
		int cur = picking.value();
		Ui.round(g, rx, sy, rx + rw, sy + 16, 0xFFFFFFFF);
		g.fill(rx + 1, sy + 1, rx + rw / 2, sy + 15, 0xFF000000 | pickOriginal);
		g.fill(rx + rw / 2, sy + 1, rx + rw - 1, sy + 15, 0xFF000000 | cur);
		Ui.text(g, font, "§7old", rx + 3, sy + 18, 0.65f, 0xFFFFFFFF, false);
		Ui.text(g, font, "§7new", rx + rw - 3 - font.width("new") * 0.65f, sy + 18, 0.65f, 0xFFFFFFFF, false);

		int fy = sy + 26;
		Ui.round(g, rx, fy, rx + rw, fy + 14, hexFocused ? acc : 0xFF2A2A36);
		Ui.round(g, rx + 1, fy + 1, rx + rw - 1, fy + 13, 0xFF0E0E13);
		String hexShown = "§8#§f" + hexInput + (hexFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		g.text(font, hexShown, rx + 4, fy + 3, 0xFFFFFFFF, false);
		pickHits.add(new HitBox(rx, fy, rx + rw, fy + 14, () -> {
			hexFocused = true;
			hexInput = "";
		}, null));

		int cols = 4, cell = Math.max(8, (rw - (cols - 1) * 2) / cols);
		int gy = fy + 18;
		for (int i = 0; i < Option.Color.PALETTE.size(); i++) {
			int col = Option.Color.PALETTE.get(i);
			int cx = rx + (i % cols) * (cell + 2), cyy = gy + (i / cols) * (cell + 2);
			boolean sel = col == cur;
			boolean hov = Ui.inside(mx, my, cx, cyy, cx + cell, cyy + cell);
			if (sel || hov) g.fill(cx - 1, cyy - 1, cx + cell + 1, cyy + cell + 1, sel ? 0xFFFFFFFF : 0x90FFFFFF);
			g.fill(cx, cyy, cx + cell, cyy + cell, 0xFF000000 | col);
			pickHits.add(new HitBox(cx, cyy, cx + cell, cyy + cell, () -> {
				hexFocused = false;
				applyPick(col, true);
				click();
			}, null));
		}

		// Buttons
		int by = y2 - 22;
		pickButton(g, mx, my, x2 - 60, by, x2 - 8, by + 15, "Done", acc, () -> closePicker(true));
		pickButton(g, mx, my, x2 - 116, by, x2 - 64, by + 15, "Cancel", 0xFF3A3A48, () -> closePicker(false));
		if (picking.defaultValue != null) {
			int def = picking.defaultValue;
			pickButton(g, mx, my, x1 + 8, by, x1 + 64, by + 15, "Default", 0xFF3A3A48, () -> {
				hexFocused = false;
				applyPick(def, true);
			});
		}
	}

	private void pickButton(GuiGraphicsExtractor g, int mx, int my, int x1, int y1, int x2, int y2, String text, int color, Runnable action) {
		boolean hover = Ui.inside(mx, my, x1, y1, x2, y2);
		Ui.round(g, x1, y1, x2, y2, hover ? Ui.mix(color, 0xFFFFFFFF, 0.2f) : color);
		g.text(font, "§f" + text, (x1 + x2) / 2 - font.width(text) / 2, (y1 + y2) / 2 - 4, 0xFFFFFFFF, true);
		pickHits.add(new HitBox(x1, y1, x2, y2, () -> {
			click();
			action.run();
		}, null));
	}

	private void dragPick(double mx, double my) {
		int sx = pickX1() + 8, sy = pickY1() + 22;
		if (pickDrag == 1) {
			pickS = (float) Math.max(0, Math.min(1, (mx - sx) / (SV_W - 1)));
			pickV = 1 - (float) Math.max(0, Math.min(1, (my - sy) / (SV_H - 1)));
		} else if (pickDrag == 2) {
			pickH = (float) Math.max(0, Math.min(0.999, (my - sy) / (SV_H - 1)));
		} else {
			return;
		}
		hexFocused = false;
		applyPick(Option.Color.hsv(pickH, pickS, pickV), false);
	}

	private boolean pickerClick(double mx, double my, boolean right) {
		for (int i = pickHits.size() - 1; i >= 0; i--) {
			HitBox h = pickHits.get(i);
			if (mx >= h.x1() && mx < h.x2() && my >= h.y1() && my < h.y2()) {
				if (!right && h.left() != null) h.left().run();
				return true;
			}
		}
		boolean inside = Ui.inside(mx, my, pickX1(), pickY1(), pickX1() + PICK_W, pickY1() + PICK_H);
		if (!inside) closePicker(!right); // Klick daneben = übernehmen, Rechtsklick daneben = abbrechen
		else hexFocused = false;
		return true;
	}

	// ============================================================ Helfer

	private void button(GuiGraphicsExtractor g, int mx, int my, int x1, int y1, int x2, int y2, String text, int color, Runnable action) {
		boolean hover = Ui.inside(mx, my, x1, y1, x2, y2);
		Ui.round(g, x1, y1, x2, y2, hover ? Ui.mix(color, 0xFFFFFFFF, 0.2f) : color);
		g.text(font, "§f" + text, (x1 + x2) / 2 - font.width(text) / 2, (y1 + y2) / 2 - 4, 0xFFFFFFFF, true);
		hits.add(new HitBox(x1, y1, x2, y2, () -> {
			click();
			action.run();
		}, null));
	}

	private double lastMouseX, lastMouseY;

	private double mouseX() {
		return lastMouseX;
	}

	private void changed() {
		KaisersSlayers.config().save();
		click();
	}

	private void click() {
		minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
	}

	private static float ease(float t) {
		return 1 - (1 - t) * (1 - t);
	}

	private static String fit(Font font, String s, int max) {
		if (max <= 0) return "";
		if (font.width(s) <= max) return s;
		String t = s;
		while (t.length() > 1 && font.width(t + "…") > max) t = t.substring(0, t.length() - 1);
		return t + "…";
	}

	// ============================================================ Eingabe

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = toLocalX(event.x()), my = toLocalY(event.y());
		lastMouseX = mx;
		lastMouseY = my;
		boolean right = event.button() == 1;
		if (picking != null) return pickerClick(mx, my, right);
		searchFocused = Ui.inside(mx, my, px2() - 138, py1() + 9, px2() - 8, py1() + 25);
		for (int i = hits.size() - 1; i >= 0; i--) {
			HitBox h = hits.get(i);
			if (mx >= h.x1() && mx < h.x2() && my >= h.y1() && my < h.y2()) {
				Runnable r = right ? h.right() : h.left();
				if (r != null) {
					r.run();
					return true;
				}
				break;
			}
		}
		if (right) {
			if (!search.isEmpty()) search = "";
			else onClose();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		lastMouseX = toLocalX(event.x());
		lastMouseY = toLocalY(event.y());
		if (picking != null) {
			dragPick(lastMouseX, lastMouseY);
			return true;
		}
		if (dragging != null) {
			dragging.setFraction((toLocalX(event.x()) - dragX1) / (double) (dragX2 - dragX1));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (pickDrag != 0) {
			pickDrag = 0;
			KaisersSlayers.config().save();
			return true;
		}
		if (dragging != null) {
			dragging = null;
			KaisersSlayers.config().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		if (picking != null) {
			// Mausrad im Farbwähler = Farbton drehen
			pickH = (float) (((pickH - v * 0.02) % 1 + 1) % 1);
			applyPick(Option.Color.hsv(pickH, pickS, pickV), false);
			return true;
		}
		if (toLocalX(x) < px1() + SIDEBAR_W) sideScroll -= v * 16;
		else scrollTarget -= v * 24;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (picking != null) {
			int key = event.key();
			if (hexFocused && key == 259) {
				if (!hexInput.isEmpty()) hexInput = hexInput.substring(0, hexInput.length() - 1);
				return true;
			}
			if (key == 257 || key == 335) { // Enter
				if (hexFocused) {
					int rgb = Option.Color.parseHex(hexInput);
					hexFocused = false;
					if (rgb >= 0) applyPick(rgb, true);
					else hexInput = Option.Color.hex(picking.value()).substring(1);
				} else {
					closePicker(true);
				}
				return true;
			}
			if (key == 256) { // Escape = abbrechen
				closePicker(false);
				return true;
			}
			return true;
		}
		if (searchFocused) {
			int key = event.key();
			if (key == 259) { // Backspace
				if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
				scrollTarget = 0;
				return true;
			}
			if (key == 256) { // Escape
				searchFocused = false;
				search = "";
				return true;
			}
			if (key == 257) { // Enter
				searchFocused = false;
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (picking != null) {
			if (hexFocused && hexInput.length() < 6) {
				String c = event.codepointAsString();
				if (c.matches("[0-9a-fA-F]")) {
					hexInput += c.toUpperCase(java.util.Locale.ROOT);
					if (hexInput.length() == 6) applyPick(Option.Color.parseHex(hexInput), true);
				}
			}
			return true;
		}
		if (searchFocused && event.isAllowedChatCharacter() && search.length() < 30) {
			search += event.codepointAsString();
			scrollTarget = 0;
			return true;
		}
		return super.charTyped(event);
	}

	/** Mauskoordinaten in das (leicht skalierte) Panel-Koordinatensystem umrechnen */
	private double toLocalX(double x) {
		float s = 0.94f + 0.06f * ease(openAnim);
		return (x - width / 2.0) / s + width / 2.0;
	}

	private double toLocalY(double y) {
		float s = 0.94f + 0.06f * ease(openAnim);
		return (y - height / 2.0) / s + height / 2.0;
	}

	@Override
	public void onClose() {
		if (picking != null) {
			closePicker(true);
			return;
		}
		KaisersSlayers.config().save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
