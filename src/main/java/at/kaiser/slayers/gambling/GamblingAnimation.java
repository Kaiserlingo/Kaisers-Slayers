package at.kaiser.slayers.gambling;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.hud.AlertOverlay;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Gambling-Animation beim Boss-Kill (ähnlich wie Kisten-Öffnungen in anderen Mods).
 * Die Walze landet immer auf deinem echten besten Drop des Kills.
 */
public final class GamblingAnimation {
	public enum Style { REEL, SLOTS }

	private static final long COLLECT_DELAY_MS = 1500; // Drops kommen kurz nach dem Kill in den Chat
	private static final long RESULT_MS = 3000;
	private static final int REEL_LENGTH = 50;
	private static final int REEL_TARGET = 42;
	private static final int SLOT_LENGTH = 30;
	private static final int SLOT_TARGET = 24;

	private static final Random RANDOM = new Random();

	// --- Zustand ---
	private static long pendingAt;
	private static SlayerType pendingType;
	private static boolean active;
	private static Style style;
	private static long startAt;
	private static long duration;
	private static List<LootPool.Entry> reel = List.of();
	private static double jitter;
	private static List<List<LootPool.Entry>> slotColumns = List.of();
	private static boolean slotMatch;
	private static LootPool.Entry result;
	private static int resultCount;
	private static double resultValue;
	private static boolean onlyCommon;
	private static boolean jackpot;
	private static boolean finishedFx;
	private static int lastTickIdx = -1;

	private GamblingAnimation() {}

	// ================================================================ Steuerung

	/** Vom Tracker-Listener beim Kill aufrufen. */
	public static void onKill(SlayerType type, long now) {
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.gamblingEnabled || type == null || !cfg.feature(type, SlayerFeature.GAMBLING)) return;
		pendingType = type;
		pendingAt = now + COLLECT_DELAY_MS;
	}

	public static void tick(long now) {
		if (pendingType != null && now >= pendingAt) {
			SlayerType type = pendingType;
			pendingType = null;
			SlayerTracker tracker = KaisersSlayers.tracker();
			start(type, tracker.lastKillDrops(), false);
		}
	}

	/** Vorschau aus dem Menü: zufälliges Ergebnis. */
	public static void preview(SlayerType type) {
		List<LootPool.Entry> pool = pool(type);
		LootPool.Entry pick = pool.get(RANDOM.nextInt(pool.size()));
		List<SlayerTracker.Drop> drops = new ArrayList<>();
		if (pick.tier() > 0) drops.add(new SlayerTracker.Drop(pick.name(), 1, pick.tier(), System.currentTimeMillis()));
		start(type, drops, true);
	}

	private static void start(SlayerType type, List<SlayerTracker.Drop> drops, boolean preview) {
		ModConfig cfg = KaisersSlayers.config();
		List<LootPool.Entry> pool = pool(type);

		// bestes Ergebnis: höchste Stufe, dann höchster Wert
		SlayerTracker.Drop best = null;
		double bestValue = -1;
		for (SlayerTracker.Drop d : drops) {
			double v = value(d.item(), d.count());
			if (best == null || d.tier() > best.tier() || (d.tier() == best.tier() && v > bestValue)) {
				best = d;
				bestValue = v;
			}
		}
		if (best == null && !preview && "RARE_ONLY".equals(cfg.gamblingTrigger)) return;

		if (best != null) {
			result = new LootPool.Entry(best.item(), best.tier());
			resultCount = best.count();
			resultValue = bestValue;
			onlyCommon = false;
		} else {
			result = LootPool.common(type);
			resultCount = 1;
			resultValue = value(result.name(), 1);
			onlyCommon = true;
		}
		jackpot = !onlyCommon && (result.tier() >= 4 || resultValue >= cfg.gamblingJackpotValue);

		style = "SLOTS".equalsIgnoreCase(cfg.gamblingStyle) ? Style.SLOTS : Style.REEL;
		duration = Math.max(1500, Math.min(10_000, cfg.gamblingDurationMs));
		reel = buildStrip(pool, result, REEL_LENGTH, REEL_TARGET, RANDOM);
		jitter = (RANDOM.nextDouble() - 0.5) * 0.7; // landet nicht immer exakt mittig
		slotMatch = !onlyCommon;
		slotColumns = new ArrayList<>();
		for (int c = 0; c < 3; c++) {
			LootPool.Entry target = slotMatch ? result : (c == 1 ? result : randomOther(pool, result, RANDOM));
			slotColumns.add(buildStrip(pool, target, SLOT_LENGTH, SLOT_TARGET, RANDOM));
		}
		startAt = System.currentTimeMillis();
		active = true;
		finishedFx = false;
		lastTickIdx = -1;
	}

	private static List<LootPool.Entry> pool(SlayerType type) {
		ModConfig cfg = KaisersSlayers.config();
		var own = cfg.gamblingUseOwnDrops ? KaisersSlayers.tracker().lifetime().get(type).drops : null;
		return LootPool.build(type, cfg.extraItems(type), own);
	}

	private static double value(String item, int count) {
		return KaisersSlayers.prices().price(item, KaisersSlayers.config().instantSellPrices).value() * count;
	}

	// ================================================================ reine Logik (testbar)

	/** Zufällige Item-Folge, an Position target liegt das Ergebnis; danach ein "Beinahe-Treffer". */
	public static List<LootPool.Entry> buildStrip(List<LootPool.Entry> pool, LootPool.Entry result, int length, int target, Random r) {
		List<LootPool.Entry> out = new ArrayList<>(length);
		for (int i = 0; i < length; i++) out.add(weighted(pool, r));
		out.set(target, result);
		if (target + 1 < length) out.set(target + 1, highest(pool, r));
		return out;
	}

	static LootPool.Entry weighted(List<LootPool.Entry> pool, Random r) {
		int total = 0;
		for (LootPool.Entry e : pool) total += weight(e.tier());
		int roll = r.nextInt(Math.max(1, total));
		for (LootPool.Entry e : pool) {
			roll -= weight(e.tier());
			if (roll < 0) return e;
		}
		return pool.get(0);
	}

	private static int weight(int tier) {
		return switch (tier) {
			case 0 -> 40;
			case 1 -> 25;
			case 2 -> 15;
			case 3 -> 8;
			case 4 -> 4;
			default -> 2;
		};
	}

	private static LootPool.Entry highest(List<LootPool.Entry> pool, Random r) {
		List<LootPool.Entry> top = new ArrayList<>();
		int max = 0;
		for (LootPool.Entry e : pool) max = Math.max(max, e.tier());
		for (LootPool.Entry e : pool) if (e.tier() >= Math.max(3, max - 1)) top.add(e);
		return top.isEmpty() ? pool.get(r.nextInt(pool.size())) : top.get(r.nextInt(top.size()));
	}

	private static LootPool.Entry randomOther(List<LootPool.Entry> pool, LootPool.Entry not, Random r) {
		for (int i = 0; i < 20; i++) {
			LootPool.Entry e = pool.get(r.nextInt(pool.size()));
			if (!e.name().equals(not.name())) return e;
		}
		return not;
	}

	/** Ease-Out (schnell starten, langsam auslaufen) */
	public static double ease(double t) {
		t = Math.max(0, Math.min(1, t));
		return 1 - Math.pow(1 - t, 4);
	}

	// ================================================================ Zeichnen

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (!active) return;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		long elapsed = now - startAt;
		if (elapsed > duration + RESULT_MS) {
			active = false;
			return;
		}
		ModConfig cfg = KaisersSlayers.config();
		float s = Math.max(0.5f, Math.min(2f, cfg.gamblingScale));
		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();
		int cx = screenW / 2;
		int cy = (int) (screenH * 0.24);
		double p = Math.min(1.0, elapsed / (double) duration);

		if (style == Style.REEL) renderReel(g, mc.font, cfg, cx, cy, s, p);
		else renderSlots(g, mc.font, cfg, cx, cy, s, elapsed);

		if (p >= 1.0) {
			if (!finishedFx) {
				finishedFx = true;
				if (cfg.gamblingSounds) {
					if (jackpot) AlertOverlay.playSound(SoundEvents.PLAYER_LEVELUP, 1.0f);
					else if (!onlyCommon) AlertOverlay.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f);
					else AlertOverlay.playSound(SoundEvents.VILLAGER_NO, 0.6f);
				}
			}
			renderResultText(g, mc.font, cfg, cx, cy + (int) (62 * s), now);
		}
	}

	private static void renderReel(GuiGraphicsExtractor g, Font font, ModConfig cfg, int cx, int cy, float s, double p) {
		int cardW = (int) (52 * s), gap = (int) (4 * s), step = cardW + gap;
		int cardH = (int) (44 * s);
		int boxHalf = (int) (150 * s);
		int top = cy - cardH / 2;

		double dist = (REEL_TARGET + jitter) * step;
		double pos = dist * ease(p);

		// Rahmen
		g.fill(cx - boxHalf - 4, top - 6, cx + boxHalf + 4, top + cardH + 6, 0xD0101018);
		g.fill(cx - boxHalf - 4, top - 6, cx + boxHalf + 4, top - 5, 0xFFC0392B);
		g.fill(cx - boxHalf - 4, top + cardH + 5, cx + boxHalf + 4, top + cardH + 6, 0xFFC0392B);

		g.enableScissor(cx - boxHalf, top - 4, cx + boxHalf, top + cardH + 4);
		for (int i = 0; i < reel.size(); i++) {
			int x = (int) Math.round(cx - cardW / 2.0 + i * step - pos);
			if (x + cardW < cx - boxHalf || x > cx + boxHalf) continue;
			boolean highlight = p >= 1.0 && i == REEL_TARGET;
			drawCard(g, font, reel.get(i), x, top, cardW, cardH, s, highlight);
		}
		g.disableScissor();

		// Zeiger
		g.fill(cx - 1, top - 8, cx + 1, top + cardH + 8, 0xFFFFD700);

		// Tick-Sound pro Karte
		int idx = (int) Math.floor((pos + step / 2.0) / step);
		if (idx != lastTickIdx) {
			if (lastTickIdx >= 0 && p < 1.0 && cfg.gamblingSounds) {
				AlertOverlay.playSound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.6f);
			}
			lastTickIdx = idx;
		}
	}

	private static void renderSlots(GuiGraphicsExtractor g, Font font, ModConfig cfg, int cx, int cy, float s, long elapsed) {
		int colW = (int) (92 * s), cellH = (int) (18 * s), gap = (int) (6 * s);
		int totalW = colW * 3 + gap * 2;
		int left = cx - totalW / 2;
		int top = cy - cellH * 3 / 2;
		double[] stops = {0.6, 0.8, 1.0};

		g.fill(left - 6, top - 8, left + totalW + 6, top + cellH * 3 + 8, 0xD0101018);
		g.fill(left - 6, top - 8, left + totalW + 6, top - 7, 0xFFFFD700);
		g.fill(left - 6, top + cellH * 3 + 7, left + totalW + 6, top + cellH * 3 + 8, 0xFFFFD700);

		int spinning = -1;
		for (int c = 0; c < 3; c++) {
			double pc = Math.min(1.0, elapsed / (duration * stops[c]));
			if (pc < 1.0 && spinning < 0) spinning = c;
			double pos = SLOT_TARGET * cellH * ease(pc);
			int x = left + c * (colW + gap);
			g.fill(x, top, x + colW, top + cellH * 3, 0xFF1E1E28);
			g.enableScissor(x, top, x + colW, top + cellH * 3);
			List<LootPool.Entry> col = slotColumns.get(c);
			for (int i = 0; i < col.size(); i++) {
				// Ergebnis soll in der mittleren Reihe landen
				int y = (int) Math.round(top + cellH + i * cellH - pos);
				if (y + cellH < top || y > top + cellH * 3) continue;
				LootPool.Entry e = col.get(i);
				String label = fit(font, e.name(), (int) ((colW - 6) / (0.75f * s)));
				g.pose().pushMatrix();
				g.pose().translate(x + 3, y + (cellH - font.lineHeight * 0.75f * s) / 2f);
				g.pose().scale(0.75f * s, 0.75f * s);
				g.text(font, label, 0, 0, LootPool.tierColor(e.tier()), true);
				g.pose().popMatrix();
			}
			g.disableScissor();
		}
		// Gewinnlinie
		int lineColor = (elapsed >= duration && slotMatch) ? 0xFFFFD700 : 0x90FFFFFF;
		g.fill(left - 4, top + cellH, left + totalW + 4, top + cellH + 1, lineColor);
		g.fill(left - 4, top + cellH * 2 - 1, left + totalW + 4, top + cellH * 2, lineColor);

		// Tick-Sound für die gerade drehende Walze
		if (spinning >= 0) {
			double pc = Math.min(1.0, elapsed / (duration * stops[spinning]));
			int idx = (int) Math.floor(SLOT_TARGET * ease(pc));
			if (idx != lastTickIdx) {
				if (lastTickIdx >= 0 && cfg.gamblingSounds) AlertOverlay.playSound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 1.4f);
				lastTickIdx = idx;
			}
		}
	}

	private static void drawCard(GuiGraphicsExtractor g, Font font, LootPool.Entry e, int x, int y, int w, int h, float s, boolean highlight) {
		int color = LootPool.tierColor(e.tier());
		g.fill(x, y, x + w, y + h, highlight ? color : 0xFF2A2A35);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1A1A22);
		g.fill(x + 1, y + h - 4, x + w - 1, y + h - 1, color); // Stufen-Balken unten

		float ts = 0.6f * s;
		int maxW = (int) ((w - 4) / ts);
		List<String> lines = wrap(font, e.name(), maxW, 3);
		float lineH = font.lineHeight * ts;
		float startY = y + (h - 4 - lines.size() * lineH) / 2f;
		for (int i = 0; i < lines.size(); i++) {
			String l = lines.get(i);
			float lw = font.width(l) * ts;
			g.pose().pushMatrix();
			g.pose().translate(x + (w - lw) / 2f, startY + i * lineH);
			g.pose().scale(ts, ts);
			g.text(font, l, 0, 0, 0xFFFFFFFF, false);
			g.pose().popMatrix();
		}
	}

	private static void renderResultText(GuiGraphicsExtractor g, Font font, ModConfig cfg, int cx, int y, long now) {
		String title;
		if (onlyCommon) title = "§7Kein Treffer – nur Common Drops";
		else if (jackpot) title = (((now / 200) % 2 == 0) ? "§6§l" : "§e§l") + "JACKPOT! " + LootPool.tierName(result.tier());
		else title = "§d§l" + LootPool.tierName(result.tier()) + "!";
		String item = "§f" + (resultCount > 1 ? resultCount + "x " : "") + result.name();
		if (cfg.gamblingShowValue && resultValue > 0) item += " §6" + TextUtil.formatCoins(resultValue);

		int tw = font.width(title), iw = font.width(item);
		g.text(font, title, cx - tw / 2, y, 0xFFFFFFFF, true);
		g.text(font, item, cx - iw / 2, y + font.lineHeight + 2, 0xFFFFFFFF, true);
	}

	// ================================================================ Text-Helfer

	static List<String> wrap(Font font, String text, int maxW, int maxLines) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = line.isEmpty() ? word : line + " " + word;
			if (font.width(test) <= maxW || line.isEmpty()) {
				line.setLength(0);
				line.append(test);
			} else {
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) out.add(line.toString());
		if (out.size() > maxLines) {
			List<String> cut = new ArrayList<>(out.subList(0, maxLines));
			cut.set(maxLines - 1, fit(font, cut.get(maxLines - 1) + "…", maxW));
			return cut;
		}
		for (int i = 0; i < out.size(); i++) out.set(i, fit(font, out.get(i), maxW));
		return out;
	}

	static String fit(Font font, String text, int maxW) {
		if (font.width(text) <= maxW) return text;
		String t = text;
		while (t.length() > 1 && font.width(t + "…") > maxW) t = t.substring(0, t.length() - 1);
		return t + "…";
	}

	public static boolean isActive() {
		return active;
	}

	/** true, wenn gerade eine Animation läuft oder gleich startet (dann keine Spoiler-Alerts). */
	public static boolean isActiveOrPending() {
		return active || pendingType != null;
	}
}
