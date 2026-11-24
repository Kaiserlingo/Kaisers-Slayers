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
	private static final int ROW_H = 30;
	private static final int HEADER_ROW_H = 18;
	private static int lastCategory = 0;

	private int category = lastCategory;
	private double scroll, scrollTarget;
	private String search = "";
	private boolean searchFocused;
	private Option.Slider dragging;
	private int dragX1, dragX2;
	private float openAnim;
	private long opened;
	private final List<HitBox> hits = new ArrayList<>();

	private record HitBox(int x1, int y1, int x2, int y2, Runnable left, Runnable right) {}

	public KsScreen() {
		super(Component.literal("Kaiser's Slayers"));
	}

	// ============================================================ Layout

	private int px1() { return Math.max(6, width / 2 - 250); }
	private int px2() { return Math.min(width - 6, width / 2 + 250); }
	private int py1() { return Math.max(6, height / 2 - 160); }
	private int py2() { return Math.min(height - 6, height / 2 + 160); }
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
		Ui.text(g, font, "§8Slayer-Begleiter für Hypixel SkyBlock · v" + KaisersSlayers.version(), x1 + 28, y1 + 22, 0.7f, 0xFFFFFFFF, false);

		// Suchfeld
		int sx2 = x2 - 8, sx1 = sx2 - 130, sy1 = y1 + 9, sy2 = sy1 + 16;
		Ui.round(g, sx1, sy1, sx2, sy2, searchFocused ? Ui.argb(acc, 0xFF) : 0xFF2A2A36);
		Ui.round(g, sx1 + 1, sy1 + 1, sx2 - 1, sy2 - 1, 0xFF0E0E13);
		String shown = search.isEmpty() && !searchFocused ? "§8🔍 Suchen..." : "§f" + search + (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0 ? "_" : "");
		g.text(font, shown, sx1 + 5, sy1 + 4, 0xFFFFFFFF, false);
		hits.add(new HitBox(sx1, sy1, sx2, sy2, () -> searchFocused = true, () -> {
			search = "";
			searchFocused = true;
		}));

		// ---------- Seitenleiste ----------
		int sbx2 = x1 + SIDEBAR_W;
		g.fill(x1 + 1, y1 + HEADER_H + 1, sbx2, y2 - 1, 0x60000000);
		g.fill(sbx2, y1 + HEADER_H + 1, sbx2 + 1, y2 - 1, 0x30FFFFFF);
		int cy = y1 + HEADER_H + 6;
		for (int i = 0; i < Menus.CATEGORIES.size(); i++) {
			Menus.Category c = Menus.CATEGORIES.get(i);
			if (i == 7) {
				Ui.text(g, font, "§8SLAYER", x1 + 10, cy + 2, 0.7f, 0xFFFFFFFF, false);
				cy += 11;
			}
			int ry1 = cy, ry2 = cy + 18;
			boolean active = i == category && search.isEmpty();
			boolean hover = Ui.inside(mx, my, x1 + 4, ry1, sbx2 - 4, ry2);
			if (active) {
				Ui.round(g, x1 + 4, ry1, sbx2 - 4, ry2, Ui.argb(c.accent(), 0x40));
				g.fill(x1 + 4, ry1 + 2, x1 + 6, ry2 - 2, c.accent());
			} else if (hover) {
				Ui.round(g, x1 + 4, ry1, sbx2 - 4, ry2, 0x20FFFFFF);
			}
			Ui.icon(g, c.icon(), x1 + 9, ry1 + 2, 0.875f);
			String label = c.slayer() != null ? c.slayer().bossName : c.name();
			g.text(font, (active ? "§f§l" : hover ? "§f" : "§7") + fit(font, label, SIDEBAR_W - 34), x1 + 27, ry1 + 5, 0xFFFFFFFF, false);
			final int idx = i;
			hits.add(new HitBox(x1 + 4, ry1, sbx2 - 4, ry2, () -> select(idx), null));
			cy += 19;
		}

		// ---------- Inhalt ----------
		int cx1 = sbx2 + 10, cx2 = x2 - 10, cy1 = y1 + HEADER_H + 6, cy2 = y2 - FOOTER_H;
		Menus.Category cat = Menus.CATEGORIES.get(category);
		String title = search.isEmpty() ? (cat.slayer() != null ? cat.slayer().color + "§l" + cat.slayer().bossName : "§f§l" + cat.name())
				: "§f§lSuche: §7\"" + search + "\"";
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
		button(g, mx, my, x2 - 78, y2 - 21, x2 - 8, y2 - 5, "Fertig", acc, this::onClose);
		button(g, mx, my, x2 - 168, y2 - 21, x2 - 84, y2 - 5, "HUD-Editor", 0xFF3A3A48,
				() -> minecraft.gui.setScreen(new HudEditorScreen(this)));
		Ui.text(g, font, "§8Rechtsklick = zurück · Mausrad = scrollen", sbx2 + 10, y2 - 15, 0.7f, 0xFFFFFFFF, false);

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

	private void select(int idx) {
		category = idx;
		lastCategory = idx;
		search = "";
		searchFocused = false;
		scroll = scrollTarget = 0;
		click();
	}

	// ============================================================ Optionen zeichnen

	private int drawOptions(GuiGraphicsExtractor g, List<Option> opts, int x1, int y, int x2, int mx, int my, int clipTop, int clipBottom, boolean profitTable) {
		int startY = y;
		int acc = accent();
		boolean inClip;
		if (opts.isEmpty()) {
			g.text(font, "§8Nichts gefunden.", x1, y + 4, 0xFFFFFFFF, false);
			return 20;
		}
		for (Option o : opts) {
			if (o instanceof Option.Header) {
				y += 4;
				Ui.text(g, font, "§l" + o.label.toUpperCase(java.util.Locale.ROOT), x1 + 2, y + 4, 0.75f, Ui.argb(acc, 0xFF), false);
				g.fill(x1 + 2 + (int) (font.width("§l" + o.label.toUpperCase(java.util.Locale.ROOT)) * 0.75f) + 6, y + 7, x2, y + 8, 0x25FFFFFF);
				y += HEADER_ROW_H - 4;
				continue;
			}
			int ry1 = y, ry2 = y + ROW_H - 3;
			inClip = ry2 > clipTop && ry1 < clipBottom;
			boolean hover = inClip && Ui.inside(mx, my, x1, ry1, x2, ry2) && my >= clipTop && my < clipBottom;
			Ui.round(g, x1, ry1, x2, ry2, hover ? Ui.CARD_HOVER : Ui.CARD);
			String label = o.label;
			if (!search.isEmpty() && !o.category.isEmpty()) label = label + " §8· " + o.category;
			g.text(font, "§f" + label, x1 + 8, ry1 + 5, 0xFFFFFFFF, false);
			int ctrlW = controlWidth(o);
			String desc = fit(font, o.description, (int) ((x2 - x1 - ctrlW - 24) / 0.75f));
			Ui.text(g, font, "§7" + desc, x1 + 8, ry1 + 16, 0.75f, 0xFFFFFFFF, false);
			if (inClip) drawControl(g, o, x2 - 8 - ctrlW, ry1 + (ROW_H - 3) / 2, ctrlW, mx, my, acc, clipTop, clipBottom);
			y += ROW_H;
		}
		if (profitTable) y = drawProfitTable(g, x1, y + 2, x2, mx, my, clipTop, clipBottom);
		return y - startY + 6;
	}

	private int controlWidth(Option o) {
		return switch (o) {
			case Option.Toggle t -> 24;
			case Option.Choice<?> c -> 104;
			case Option.Slider sl -> 120;
			case Option.Color c -> 60;
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
				int col = 0xFF000000 | c.get.get();
				g.text(font, "§8◀", x, cy - 4, 0xFFFFFFFF, false);
				Ui.round(g, x + 12, cy - 7, x + w - 12, cy + 7, 0xFFFFFFFF);
				Ui.round(g, x + 13, cy - 6, x + w - 13, cy + 6, col);
				g.text(font, "§8▶", x + w - 7, cy - 4, 0xFFFFFFFF, false);
				hits.add(new HitBox(x, cy - 8, x + w, cy + 8, () -> {
					c.step(1);
					changed();
				}, () -> {
					c.step(-1);
					changed();
				}));
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
		g.text(font, type.color + type.bossName + " §8· " + ("TOTAL".equals(cfg.profitMode) ? "Gesamt" : "Session"), x1 + 2, y, 0xFFFFFFFF, false);
		y += 12;
		if (rows.isEmpty()) {
			g.text(font, "§8Noch keine Drops erfasst.", x1 + 2, y, 0xFFFFFFFF, false);
			return y + 12;
		}
		for (ProfitHud.Row r : rows) {
			boolean hidden = cfg.hiddenItems.contains(r.name());
			boolean hover = Ui.inside(mx, my, x1, y - 1, x2, y + 11) && my >= clipTop && my < clipBottom;
			if (hover) Ui.round(g, x1, y - 2, x2, y + 11, 0x20FFFFFF);
			g.fill(x1 + 3, y + 2, x1 + 6, y + 5, at.kaiser.slayers.gambling.LootPool.tierColor(r.tier()));
			g.text(font, (hidden ? "§8§m" : "§f") + TextUtil.formatInt(r.count()) + "x " + r.name(), x1 + 10, y, 0xFFFFFFFF, false);
			Ui.rightText(g, font, hidden ? "§8ausgeblendet" : "§6" + TextUtil.formatCoins(r.value()), x2 - 4, y, 0xFFFFFFFF);
			String name = r.name();
			hits.add(new HitBox(x1, y - 1, x2, y + 11, () -> {
				if (!cfg.hiddenItems.remove(name)) cfg.hiddenItems.add(name);
				changed();
			}, null));
			y += 12;
		}
		double profit = b.profit((n, c) -> ProfitHud.value(n, c));
		y += 4;
		g.text(font, "§7Kosten §c-" + TextUtil.formatCoins(b.cost) + "   §7Gewinn " + (profit >= 0 ? "§a" : "§c") + TextUtil.formatCoins(profit)
				+ "   §7/h §6" + TextUtil.formatCoins(b.perHour(profit)), x1 + 2, y, 0xFFFFFFFF, false);
		y += 14;
		button(g, mx, my, x1, y, x1 + 110, y + 16, "Gesamt zurücksetzen", 0xFF5A2A2A, () -> {
			KaisersSlayers.tracker().profit().resetTotal(type);
			KaisersSlayers.chat("§7Gesamt-Profit für " + type.bossName + " zurückgesetzt.");
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
			g.text(font, t.color + "§l" + t.bossName, cx + 34, cy + 8, 0xFFFFFFFF, true);
			String lvl = e.level >= 0 ? "LVL " + e.level : "LVL ?";
			Ui.text(g, font, "§7" + t.levelName + " Slayer · " + lvl, cx + 34, cy + 19, 0.75f, 0xFFFFFFFF, false);
			ProfitTracker.Bucket total = KaisersSlayers.tracker().profit().total(t);
			double profit = total.profit((n, c) -> ProfitHud.value(n, c));
			Ui.text(g, font, "§7Kills §f" + e.kills + "   §7Rekord §a" + (e.bestKillMs > 0 ? TextUtil.formatSeconds(e.bestKillMs) : "-"),
					cx + 8, cy + 34, 0.75f, 0xFFFFFFFF, false);
			Ui.text(g, font, "§7Profit gesamt " + (profit >= 0 ? "§6" : "§c") + TextUtil.formatCoins(profit)
					+ (e.rngMeterXp >= 0 ? "   §7RNG §d" + TextUtil.formatCoins(e.rngMeterXp) : ""), cx + 8, cy + 45, 0.75f, 0xFFFFFFFF, false);
			final int idx = 7 + i;
			hits.add(new HitBox(cx, cy, cx + cw, cy + ch, () -> select(idx), null));
		}
		int rows = (types.length + cols - 1) / cols;
		y += rows * (ch + gap) + 4;
		ModConfig cfg = KaisersSlayers.config();
		Ui.round(g, x1, y, x2, y + 30, Ui.CARD);
		g.text(font, "§f§lSession", x1 + 8, y + 5, 0xFFFFFFFF, false);
		var s = KaisersSlayers.tracker().session();
		Ui.text(g, font, "§7Bosse §f" + s.kills + "  §7pro Stunde §f" + String.format(java.util.Locale.ROOT, "%.1f", s.killsPerHour())
				+ "  §7Zeit §f" + TextUtil.formatDuration(s.activeMs) + "  §7Gambling " + (cfg.gamblingEnabled ? "§aAN" : "§cAUS"), x1 + 8, y + 17, 0.75f, 0xFFFFFFFF, false);
		return y - startY + 36;
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

	private double lastMouseX;

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
		boolean right = event.button() == 1;
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
		if (dragging != null) {
			dragging.setFraction((toLocalX(event.x()) - dragX1) / (double) (dragX2 - dragX1));
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			KaisersSlayers.config().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		scrollTarget -= v * 24;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
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
		KaisersSlayers.config().save();
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
