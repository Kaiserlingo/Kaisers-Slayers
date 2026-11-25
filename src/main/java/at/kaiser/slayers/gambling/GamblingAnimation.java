package at.kaiser.slayers.gambling;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.gui.Ui;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Vollbild-Gambling beim Boss-Kill. Ab dem Kill werden HUD und Chat ausgeblendet (kein Spoiler),
 * dann dreht sich die gewählte Animation und landet immer auf deinem echten besten Drop.
 * Stile: CASE (Kisten-Öffnung), SLOTS (Slot-Machine), WHEEL (Glücksrad), CARDS (Karten aufdecken).
 */
public final class GamblingAnimation {
	public enum Style { CASE, SLOTS, WHEEL, CARDS }

	private enum Stage { IDLE, SUSPENSE, SPIN, REVEAL }

	private static final long SUSPENSE_MS = 2200; // Drops kommen kurz nach dem Kill in den Chat
	private static final long REVEAL_MS = 4200;
	private static final long FADE_MS = 400;
	private static final int STRIP = 60, TARGET = 50;
	private static final int SLOT_STRIP = 34, SLOT_TARGET = 28;
	private static final int WHEEL_SEGMENTS = 12;
	private static final int CARD_COUNT = 5;

	private static final Random RANDOM = new Random();

	// ---------- Zustand ----------
	private static Stage stage = Stage.IDLE;
	private static long stageStart;
	private static SlayerType type;
	private static int tier;
	private static Style style = Style.CASE;
	private static long duration;
	private static List<LootPool.Entry> strip = List.of();
	private static List<List<LootPool.Entry>> slots = List.of();
	private static List<LootPool.Entry> wheel = List.of();
	private static int wheelTarget;
	private static List<LootPool.Entry> cards = List.of();
	private static double jitter;
	private static LootPool.Entry result;
	private static int resultCount;
	private static double resultValue;
	private static boolean onlyCommon;
	private static boolean jackpot;
	private static boolean preview;
	private static int lastTick = -1;
	private static final int[] lastSlotTick = {-1, -1, -1};
	private static int lastCardFlipped = -1;
	private static long lastFrame;
	private static final List<Particle> PARTICLES = new ArrayList<>();
	private static float shake;
	private static float flash;

	private static final class Particle {
		float x, y, vx, vy, life, maxLife, size, rot, vr;
		int color;
	}

	/** Uhr – austauschbar für automatische Tests */
	public static java.util.function.LongSupplier clock = System::currentTimeMillis;

	private static long now() {
		return clock.getAsLong();
	}

	private GamblingAnimation() {}

	// ================================================================ Steuerung

	/** Vom Tracker-Listener beim Kill: ab jetzt HUD/Chat ausblenden und Drops sammeln. */
	public static void onKill(SlayerType t, int bossTier, long now) {
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.gamblingEnabled || t == null || !cfg.feature(t, SlayerFeature.GAMBLING) || at.kaiser.slayers.DemoData.loading) return;
		type = t;
		tier = bossTier;
		preview = false;
		stage = Stage.SUSPENSE;
		stageStart = now;
		PARTICLES.clear();
	}

	public static void tick(long now) {
		if (stage == Stage.SUSPENSE && now - stageStart >= SUSPENSE_MS) {
			startSpin(KaisersSlayers.tracker().lastKillDrops(), now);
		}
	}

	/** Vorschau aus dem Menü mit zufälligem Ergebnis. */
	public static void preview(SlayerType t, String forcedStyle) {
		type = t;
		tier = Math.min(4, t.maxTier());
		preview = true;
		List<LootPool.Entry> pool = pool(t);
		LootPool.Entry pick = pool.get(RANDOM.nextInt(pool.size()));
		List<SlayerTracker.Drop> drops = new ArrayList<>();
		if (pick.tier() > 0) drops.add(new SlayerTracker.Drop(pick.name(), 1, pick.tier(), now()));
		if (forcedStyle != null) KaisersSlayers.config().gamblingStyle = forcedStyle;
		PARTICLES.clear();
		stage = Stage.SUSPENSE;
		stageStart = now() - SUSPENSE_MS + 700;
		startSpin(drops, now());
		stageStart = now();
	}

	/** Überspringen (Taste): direkt zum Ergebnis bzw. beenden */
	public static void skip() {
		if (stage == Stage.SPIN) {
			stage = Stage.REVEAL;
			stageStart = now();
			onReveal();
		} else if (stage == Stage.REVEAL || stage == Stage.SUSPENSE) {
			stage = Stage.IDLE;
		}
	}

	/** Sollen HUD, Chat & Co. gerade ausgeblendet sein? */
	public static boolean hidesHud() {
		return stage != Stage.IDLE && KaisersSlayers.config().gamblingHideHud;
	}

	public static boolean isActiveOrPending() {
		return stage != Stage.IDLE;
	}

	private static void startSpin(List<SlayerTracker.Drop> drops, long now) {
		ModConfig cfg = KaisersSlayers.config();
		List<LootPool.Entry> pool = pool(type);

		// mehrere Drops: immer der WERTVOLLSTE (bei unbekanntem Preis entscheidet die Seltenheit)
		SlayerTracker.Drop best = null;
		double bestValue = -1;
		for (SlayerTracker.Drop d : drops) {
			double v = value(d.item(), d.count());
			if (best == null || better(v, d.tier(), bestValue, best.tier())) {
				best = d;
				bestValue = v;
			}
		}
		if (best == null && !preview && "RARE_ONLY".equals(cfg.gamblingTrigger)) {
			stage = Stage.IDLE;
			return;
		}
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

		style = switch (cfg.gamblingStyle.toUpperCase(Locale.ROOT)) {
			case "SLOTS" -> Style.SLOTS;
			case "WHEEL" -> Style.WHEEL;
			case "CARDS" -> Style.CARDS;
			case "RANDOM" -> Style.values()[RANDOM.nextInt(Style.values().length)];
			default -> Style.CASE;
		};
		duration = Math.max(2000, Math.min(12_000, cfg.gamblingDurationMs));

		strip = buildStrip(pool, result, STRIP, TARGET, RANDOM);
		jitter = (RANDOM.nextDouble() - 0.5) * 0.4; // Gewinner landet nahe am Zeiger, aber nicht immer exakt mittig
		slots = new ArrayList<>();
		for (int c = 0; c < 3; c++) {
			LootPool.Entry target = onlyCommon && c != 1 ? randomOther(pool, result, RANDOM) : result;
			slots.add(buildStrip(pool, target, SLOT_STRIP, SLOT_TARGET, RANDOM));
		}
		wheel = new ArrayList<>();
		for (int i = 0; i < WHEEL_SEGMENTS; i++) wheel.add(weighted(pool, RANDOM));
		wheelTarget = RANDOM.nextInt(WHEEL_SEGMENTS);
		wheel.set(wheelTarget, result);
		cards = new ArrayList<>();
		for (int i = 0; i < CARD_COUNT; i++) cards.add(randomOther(pool, result, RANDOM));
		cards.set(CARD_COUNT / 2, result);

		lastTick = -1;
		lastSlotTick[0] = lastSlotTick[1] = lastSlotTick[2] = -1;
		lastCardFlipped = -1;
		stage = Stage.SPIN;
		stageStart = now;
		sound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.6f);
	}

	/** Ist (v, tier) besser als (bestV, bestTier)? Wert zuerst, Seltenheit nur bei Gleichstand/unbekanntem Preis. */
	public static boolean better(double v, int tier, double bestV, int bestTier) {
		if (v > 0 || bestV > 0) {
			if (v != bestV) return v > bestV;
		}
		return tier > bestTier;
	}

	/**
	 * Drop kommt erst während des Drehens im Chat an (mehrere Drops kommen nacheinander):
	 * ist er wertvoller, wird das Ziel der Animation noch rechtzeitig ausgetauscht.
	 */
	public static void onLateDrop(String item, int count, int dropTier) {
		if (stage != Stage.SPIN || preview || result == null) return;
		double v = value(item, count);
		double curValue = onlyCommon ? -1 : resultValue;
		int curTier = onlyCommon ? -1 : result.tier();
		if (!better(v, dropTier, curValue, curTier)) return;
		LootPool.Entry e = new LootPool.Entry(item, dropTier);
		result = e;
		resultCount = count;
		resultValue = v;
		onlyCommon = false;
		jackpot = result.tier() >= 4 || resultValue >= KaisersSlayers.config().gamblingJackpotValue;
		if (strip.size() > TARGET) {
			strip = new ArrayList<>(strip);
			strip.set(TARGET, e);
		}
		List<List<LootPool.Entry>> ns = new ArrayList<>();
		for (List<LootPool.Entry> col : slots) {
			List<LootPool.Entry> c = new ArrayList<>(col);
			if (c.size() > SLOT_TARGET) c.set(SLOT_TARGET, e);
			ns.add(c);
		}
		slots = ns;
		if (wheel.size() > wheelTarget) {
			wheel = new ArrayList<>(wheel);
			wheel.set(wheelTarget, e);
		}
		if (cards.size() > CARD_COUNT / 2) {
			cards = new ArrayList<>(cards);
			cards.set(CARD_COUNT / 2, e);
		}
	}

	private static void onReveal() {
		ModConfig cfg = KaisersSlayers.config();
		int color = LootPool.tierColor(result.tier());
		if (cfg.gamblingFlash && !onlyCommon) flash = jackpot ? 1.0f : 0.55f;
		if (cfg.gamblingShake && !onlyCommon) shake = jackpot ? 9f : 3f;
		if (cfg.gamblingParticles && !onlyCommon) burst(jackpot ? 220 : 40 + result.tier() * 25, color);
		if (jackpot) {
			sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
			sound(SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f);
		} else if (!onlyCommon) {
			sound(SoundEvents.PLAYER_LEVELUP, 1.0f + result.tier() * 0.1f);
		} else {
			sound(SoundEvents.VILLAGER_NO, 0.9f);
		}
	}

	private static List<LootPool.Entry> pool(SlayerType t) {
		ModConfig cfg = KaisersSlayers.config();
		var own = cfg.gamblingUseOwnDrops ? KaisersSlayers.tracker().lifetime().get(t).drops : null;
		return LootPool.build(t, cfg.extraItems(t), own);
	}

	private static double value(String item, int count) {
		return KaisersSlayers.prices().price(item, KaisersSlayers.config().instantSellPrices).value() * count;
	}

	private static void sound(SoundEvent s, float pitch) {
		if (!KaisersSlayers.config().gamblingSounds) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch));
	}

	// ================================================================ reine Logik (testbar)

	public static List<LootPool.Entry> buildStrip(List<LootPool.Entry> pool, LootPool.Entry result, int length, int target, Random r) {
		List<LootPool.Entry> out = new ArrayList<>(length);
		for (int i = 0; i < length; i++) out.add(weighted(pool, r));
		out.set(target, result);
		if (target + 1 < length) out.set(target + 1, highest(pool, r)); // Beinahe-Treffer direkt daneben
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

	private static int weight(int t) {
		return switch (t) {
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

	/** Schnell starten, langsam auslaufen */
	public static double ease(double t) {
		t = Math.max(0, Math.min(1, t));
		return 1 - Math.pow(1 - t, 4);
	}

	/** Winkel (Grad), bei dem Segment `target` oben unter dem Zeiger steht, plus mehrere volle Umdrehungen */
	public static double wheelAngle(int target, int segments, int turns, double jitterFraction) {
		double seg = 360.0 / segments;
		double center = target * seg + seg / 2 + jitterFraction * seg * 0.6;
		return turns * 360.0 + (360.0 - center);
	}

	// ================================================================ Zeichnen

	/** Oberkante des Inhalts (unter der Kopfzeile) */
	private static float contentTop(int sh) {
		return sh * 0.04f + 28;
	}

	/** Senkrechte Mitte des Inhaltsbereichs zwischen Kopfzeile und Hinweiszeile – alle Stile + Ergebnis nutzen sie */
	static float centerY(int sh) {
		return (contentTop(sh) + (sh - 20)) / 2f;
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (stage == Stage.IDLE) return;
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = KaisersSlayers.config();
		long now = now();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
		lastFrame = now;
		long t = now - stageStart;

		// Stufen weiterschalten
		if (stage == Stage.SPIN && t >= duration) {
			stage = Stage.REVEAL;
			stageStart = now;
			t = 0;
			onReveal();
		} else if (stage == Stage.REVEAL && t >= REVEAL_MS) {
			stage = Stage.IDLE;
			lastFrame = 0;
			return;
		}

		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		float fadeIn = stage == Stage.SUSPENSE ? Math.min(1f, t / (float) FADE_MS) : 1f;
		float fadeOut = stage == Stage.REVEAL ? Math.min(1f, (REVEAL_MS - t) / (float) FADE_MS) : 1f;
		float alpha = Math.min(fadeIn, fadeOut);
		int accent = cfg.gamblingAccent & 0xFFFFFF;
		int rarity = result != null ? LootPool.tierColor(result.tier()) : 0xFFFFFFFF;

		// ---------- Hintergrund ----------
		int dim = (int) (255 * cfg.gamblingDim / 100f * alpha);
		g.fill(0, 0, sw, sh, Ui.argb(0x07070B, dim));
		g.fillGradient(0, 0, sw, sh / 3, Ui.argb(accent, (int) (40 * alpha)), 0x00000000);
		g.fillGradient(0, sh * 2 / 3, sw, sh, 0x00000000, Ui.argb(stage == Stage.REVEAL ? rarity : accent, (int) (50 * alpha)));

		// Lichtstrahlen
		float rayAngle = (now % 20_000) / 20_000f * (float) (Math.PI * 2);
		int rayColor = stage == Stage.REVEAL && !onlyCommon ? rarity : 0xFF000000 | accent;
		int rayAlpha = (int) ((stage == Stage.REVEAL ? (jackpot ? 34 : 22) : 9) * alpha);
		rays(g, sw / 2f, centerY(sh), Math.max(sw, sh), rayAngle, Ui.argb(rayColor, rayAlpha), stage == Stage.REVEAL ? 14 : 10);
		// Vignette (dunkle Ränder)
		int vig = (int) (200 * alpha);
		g.fillGradient(0, 0, sw, sh / 5, Ui.argb(0x000000, vig), 0x00000000);
		g.fillGradient(0, sh - sh / 5, sw, sh, 0x00000000, Ui.argb(0x000000, vig));
		for (int i = 0; i < 12; i++) {
			int a = (int) (vig * (1 - i / 12f) * 0.8f);
			g.fill(i * 3, 0, i * 3 + 3, sh, Ui.argb(0x000000, a));
			g.fill(sw - i * 3 - 3, 0, sw - i * 3, sh, Ui.argb(0x000000, a));
		}

		// Wackeln
		g.pose().pushMatrix();
		if (shake > 0.05f) {
			g.pose().translate((RANDOM.nextFloat() - 0.5f) * shake, (RANDOM.nextFloat() - 0.5f) * shake);
			shake *= (float) Math.pow(0.02, dt);
		}

		// Partikel hinter dem Vordergrund (Ergebnis-Text bleibt lesbar)
		updateParticles(g, dt, sw, sh);

		// Kopfzeile
		String head = type != null ? type.color + "§l" + type.bossName.toUpperCase(Locale.ROOT) + " " + TextUtil.toRoman(tier) : "";
		Ui.centered(g, mc.font, "§8KAISER'S SLAYERS · DROP ROULETTE" + (preview ? " · PREVIEW" : ""), sw / 2f, sh * 0.04f, 0.75f,
				Ui.argb(0xFFFFFF, (int) (255 * alpha)), false);
		Ui.centered(g, mc.font, head, sw / 2f, sh * 0.04f + 9, 1.5f, Ui.argb(0xFFFFFF, (int) (255 * alpha)), true);

		switch (stage) {
			case SUSPENSE -> renderSuspense(g, mc.font, sw, sh, now, accent, alpha);
			case SPIN -> {
				double p = Math.min(1.0, t / (double) duration);
				renderStyle(g, mc.font, sw, sh, p, t, false);
			}
			case REVEAL -> renderReveal(g, mc.font, sw, sh, t, alpha);
			default -> {}
		}
		g.pose().popMatrix();

		// Konfetti + Blitz
		if (jackpot && stage == Stage.REVEAL && cfg.gamblingParticles && RANDOM.nextFloat() < 0.5f) confetti(sw);
		if (flash > 0.01f) {
			g.fill(0, 0, sw, sh, Ui.argb(0xFFFFFF, (int) (flash * 220)));
			flash *= (float) Math.pow(0.004, dt);
		}
		if (stage != Stage.REVEAL || t < REVEAL_MS - 600) {
			Ui.centered(g, mc.font, "§7Press §f[" + KaisersSlayers.skipKeyName() + "]§7 to skip", sw / 2f, sh - 12, 0.75f, 0xFFFFFFFF, false);
		}
	}

	private static void renderSuspense(GuiGraphicsExtractor g, Font font, int sw, int sh, long now, int accent, float alpha) {
		float cx = sw / 2f, cy = centerY(sh) - 16;
		// drehender Lade-Ring
		for (int i = 0; i < 12; i++) {
			double a = i * Math.PI / 6 + now / 180.0;
			float px = cx + (float) Math.cos(a) * 22, py = cy + (float) Math.sin(a) * 22;
			int al = (int) (255 * alpha * ((i + 1) / 12f));
			g.fill((int) px - 2, (int) py - 2, (int) px + 2, (int) py + 2, Ui.argb(accent, al));
		}
		Ui.centered(g, font, "§f§lBOSS SLAIN!", cx, cy + 34, 2.0f, Ui.argb(0xFFFFFF, (int) (255 * alpha)), true);
		String dots = ".".repeat((int) (now / 300 % 4));
		Ui.centered(g, font, "§7Rolling your drops" + dots, cx, cy + 56, 1.0f, Ui.argb(0xFFFFFF, (int) (255 * alpha)), true);
	}

	private static void renderStyle(GuiGraphicsExtractor g, Font font, int sw, int sh, double p, long t, boolean settled) {
		switch (style) {
			case CASE -> renderCase(g, font, sw, sh, p, settled);
			case SLOTS -> renderSlots(g, font, sw, sh, t, settled);
			case WHEEL -> renderWheel(g, font, sw, sh, p, settled);
			case CARDS -> renderCards(g, font, sw, sh, p, settled);
		}
	}

	// ---------------------------------------------------------------- CASE

	private static void renderCase(GuiGraphicsExtractor g, Font font, int sw, int sh, double p, boolean settled) {
		int cardW = 70, cardH = 92, gap = 8, step = cardW + gap;
		int cx = sw / 2, cy = Math.round(centerY(sh));
		int half = Math.min(sw / 2 - 20, 4 * step);
		int top = cy - cardH / 2;
		double pos = (TARGET + jitter) * step * ease(p);

		Ui.panel(g, cx - half - 8, top - 16, cx + half + 8, top + cardH + 16, 0xE00E0E14, 0xFF000000 | KaisersSlayers.config().gamblingAccent);
		g.enableScissor(cx - half, top - 12, cx + half, top + cardH + 12);
		for (int i = 0; i < strip.size(); i++) {
			float x = (float) (cx - cardW / 2.0 + i * step - pos);
			if (x + cardW < cx - half - step || x > cx + half + step) continue;
			float distFromCenter = Math.abs(x + cardW / 2f - cx) / half;
			float scale = 1.0f - Math.min(0.25f, distFromCenter * 0.25f);
			boolean win = settled && i == TARGET;
			drawCard(g, font, strip.get(i), x + cardW / 2f, cy, cardW, cardH, scale, win, distFromCenter);
		}
		g.disableScissor();

		// Zeiger oben und unten (goldene Dreiecke)
		int gold = 0xFF000000 | KaisersSlayers.config().gamblingAccent;
		for (int i = 0; i < 6; i++) {
			g.fill(cx - 6 + i, top - 14 + i, cx + 6 - i, top - 13 + i, gold);
			g.fill(cx - 6 + i, top + cardH + 13 - i, cx + 6 - i, top + cardH + 14 - i, gold);
		}
		g.fill(cx, top - 8, cx + 1, top + cardH + 8, Ui.argb(gold, 0x90));

		int idx = (int) Math.floor((pos + step / 2.0) / step);
		if (!settled && idx != lastTick) {
			if (lastTick >= 0) sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.4f + (float) p * 0.4f);
			lastTick = idx;
		}
	}

	/** Eine Item-Karte, zentriert bei (cx, cy) */
	private static void drawCard(GuiGraphicsExtractor g, Font font, LootPool.Entry e, float cx, float cy, int w, int h,
								 float scale, boolean win, float fade) {
		int color = LootPool.tierColor(e.tier());
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		int x1 = -w / 2, y1 = -h / 2, x2 = w / 2, y2 = h / 2;
		if (win) Ui.round(g, x1 - 4, y1 - 4, x2 + 4, y2 + 4, Ui.argb(color, 0x90));
		Ui.round(g, x1, y1, x2, y2, win ? color : Ui.mix(0xFF2A2A36, color, 0.25f));
		Ui.round(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFF15151C);
		g.fillGradient(x1 + 1, y1 + 1, x2 - 1, y2 - 1, Ui.argb(color, 0x10), Ui.argb(color, 0x55));
		g.fill(x1 + 1, y2 - 5, x2 - 1, y2 - 1, color);
		Ui.icon(g, ItemIcons.of(e.name()), -16, y1 + 8, 2.0f);
		List<String> lines = wrap(font, e.name(), (int) ((w - 8) / 0.7f), 3);
		float lineH = 7f, glyphH = 5f;
		float areaTop = y1 + 44, areaBottom = y2 - 7;
		float ly = (areaTop + areaBottom) / 2f - ((lines.size() - 1) * lineH + glyphH) / 2f;
		for (String l : lines) {
			Ui.centered(g, font, l, 0, ly, 0.7f, 0xFFFFFFFF, true);
			ly += lineH;
		}
		if (fade > 0) g.fill(x1, y1, x2, y2, Ui.argb(0x000000, (int) (Math.min(1f, fade) * 120)));
		g.pose().popMatrix();
	}

	// ---------------------------------------------------------------- SLOTS

	private static void renderSlots(GuiGraphicsExtractor g, Font font, int sw, int sh, long t, boolean settled) {
		int colW = 96, cellH = 34, gap = 10;
		int totalW = colW * 3 + gap * 2;
		int left = sw / 2 - totalW / 2, cy = Math.round(centerY(sh)), top = cy - cellH * 3 / 2;
		double[] stops = {0.55, 0.78, 1.0};
		int gold = 0xFF000000 | KaisersSlayers.config().gamblingAccent;

		Ui.panel(g, left - 14, top - 18, left + totalW + 14, top + cellH * 3 + 18, 0xF0140C08, gold);
		long now = now();
		// blinkende Lichter am Rahmen
		for (int i = 0; i < 18; i++) {
			int lx = left - 10 + i * (totalW + 20) / 17;
			boolean on = ((now / 150) + i) % 2 == 0 || settled;
			g.fill(lx - 1, top - 15, lx + 2, top - 12, on ? gold : 0xFF3A2A10);
			g.fill(lx - 1, top + cellH * 3 + 12, lx + 2, top + cellH * 3 + 15, on ? gold : 0xFF3A2A10);
		}
		for (int c = 0; c < 3; c++) {
			double pc = settled ? 1 : Math.min(1.0, t / (duration * stops[c]));
			double pos = SLOT_TARGET * cellH * ease(pc);
			int x = left + c * (colW + gap);
			Ui.round(g, x, top, x + colW, top + cellH * 3, 0xFF0D0D12);
			g.enableScissor(x, top, x + colW, top + cellH * 3);
			List<LootPool.Entry> col = slots.get(c);
			for (int i = 0; i < col.size(); i++) {
				int y = (int) Math.round(top + cellH + i * cellH - pos);
				if (y + cellH < top || y > top + cellH * 3) continue;
				LootPool.Entry e = col.get(i);
				int color = LootPool.tierColor(e.tier());
				g.fill(x + 2, y + 2, x + 4, y + cellH - 2, color);
				Ui.icon(g, ItemIcons.of(e.name()), x + 7, y + (cellH - 16) / 2f, 1.0f);
				List<String> lines = wrap(font, e.name(), (int) ((colW - 31) / 0.7f), 2);
				float ly = y + cellH / 2f - ((lines.size() - 1) * 7f + 5f) / 2f;
				for (String l : lines) {
					Ui.text(g, font, l, x + 27, ly, 0.7f, color, true);
					ly += 7;
				}
			}
			g.fillGradient(x, top, x + colW, top + 14, 0xC0000000, 0x00000000);
			g.fillGradient(x, top + cellH * 3 - 14, x + colW, top + cellH * 3, 0x00000000, 0xC0000000);
			g.disableScissor();

			int tick = (int) Math.floor(SLOT_TARGET * ease(pc));
			if (!settled && pc < 1.0 && tick != lastSlotTick[c]) {
				if (lastSlotTick[c] >= 0 && c == spinningColumn(t, stops)) sound(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2f + c * 0.2f);
				lastSlotTick[c] = tick;
			}
			if (!settled && pc >= 1.0 && lastSlotTick[c] != Integer.MAX_VALUE) {
				lastSlotTick[c] = Integer.MAX_VALUE;
				sound(SoundEvents.WOODEN_BUTTON_CLICK_ON, 0.8f);
			}
		}
		int line = settled && !onlyCommon ? gold : 0x90FFFFFF;
		g.fill(left - 8, top + cellH, left + totalW + 8, top + cellH + 1, line);
		g.fill(left - 8, top + cellH * 2 - 1, left + totalW + 8, top + cellH * 2, line);
	}

	private static int spinningColumn(long t, double[] stops) {
		for (int c = 0; c < 3; c++) if (t < duration * stops[c]) return c;
		return -1;
	}

	// ---------------------------------------------------------------- WHEEL

	private static void renderWheel(GuiGraphicsExtractor g, Font font, int sw, int sh, double p, boolean settled) {
		float r = Math.min(Math.min(sh * 0.27f, (sh - 20 - contentTop(sh)) / 2f - 16), 110);
		float cx = sw / 2f, cy = centerY(sh) - 4;
		double total = wheelAngle(wheelTarget, WHEEL_SEGMENTS, 6, jitter);
		double angle = total * ease(p);
		double seg = 360.0 / WHEEL_SEGMENTS;

		// Segmente als viele dünne Strahlen
		for (int i = 0; i < WHEEL_SEGMENTS; i++) {
			LootPool.Entry e = wheel.get(i);
			int base = LootPool.tierColor(e.tier());
			int color = i % 2 == 0 ? Ui.mix(base, 0xFF000000, 0.35f) : Ui.mix(base, 0xFF000000, 0.55f);
			if (settled && i == wheelTarget) color = base;
			for (double a = 0; a < seg; a += 1.2) {
				double deg = angle + i * seg + a - 90;
				g.pose().pushMatrix();
				g.pose().translate(cx, cy);
				g.pose().rotate((float) Math.toRadians(deg));
				g.fill(0, -2, (int) r, 2, color);
				g.pose().popMatrix();
			}
		}
		// Trennlinien
		for (int i = 0; i < WHEEL_SEGMENTS; i++) {
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate((float) Math.toRadians(angle + i * seg - 90));
			g.fill(0, 0, (int) r, 1, 0xFF0A0A0E);
			g.pose().popMatrix();
		}
		// Icons
		for (int i = 0; i < WHEEL_SEGMENTS; i++) {
			double mid = Math.toRadians(angle + i * seg + seg / 2 - 90);
			float ix = cx + (float) Math.cos(mid) * r * 0.68f, iy = cy + (float) Math.sin(mid) * r * 0.68f;
			Ui.icon(g, ItemIcons.of(wheel.get(i).name()), ix - 8, iy - 8, 1.0f);
		}
		// Rand mit Lichtern
		long now = now();
		int gold = 0xFF000000 | KaisersSlayers.config().gamblingAccent;
		for (int i = 0; i < 36; i++) {
			double a = Math.toRadians(i * 10);
			float lx = cx + (float) Math.cos(a) * (r + 5), ly = cy + (float) Math.sin(a) * (r + 5);
			boolean on = ((now / 120) + i) % 3 == 0 || settled;
			g.fill((int) lx - 1, (int) ly - 1, (int) lx + 2, (int) ly + 2, on ? gold : 0xFF4A3A18);
		}
		Ui.round(g, (int) cx - 12, (int) cy - 12, (int) cx + 12, (int) cy + 12, gold);
		Ui.round(g, (int) cx - 9, (int) cy - 9, (int) cx + 9, (int) cy + 9, 0xFF15151C);
		Ui.icon(g, Ui.slayerIcon(type), cx - 8, cy - 8, 1.0f);
		// Zeiger oben
		for (int i = 0; i < 8; i++) g.fill((int) cx - 8 + i, (int) (cy - r - 14) + i, (int) cx + 8 - i, (int) (cy - r - 13) + i, gold);

		// Tick bei jedem Segmentwechsel unter dem Zeiger
		int idx = (int) Math.floor(angle / seg);
		if (!settled && idx != lastTick) {
			if (lastTick >= 0) sound(SoundEvents.NOTE_BLOCK_XYLOPHONE.value(), 1.0f + (float) p);
			lastTick = idx;
		}
		if (settled || p > 0.85) {
			LootPool.Entry under = wheel.get(Math.floorMod(WHEEL_SEGMENTS - 1 - (int) Math.floor((angle % 360) / seg), WHEEL_SEGMENTS));
			Ui.centered(g, font, "§f" + under.name(), cx, cy + r + 12, 1.0f, LootPool.tierColor(under.tier()), true);
		}
	}

	// ---------------------------------------------------------------- CARDS

	private static void renderCards(GuiGraphicsExtractor g, Font font, int sw, int sh, double p, boolean settled) {
		int cardW = 64, cardH = 90, gap = 12;
		int totalW = CARD_COUNT * cardW + (CARD_COUNT - 1) * gap;
		int left = sw / 2 - totalW / 2;
		float cy = centerY(sh);
		// Reihenfolge: außen zuerst, die mittlere Karte (Ergebnis) zuletzt und langsam
		int[] order = {0, 4, 1, 3, 2};
		for (int k = 0; k < CARD_COUNT; k++) {
			int i = order[k];
			double startP = k * 0.16, endP = k == CARD_COUNT - 1 ? 1.0 : startP + 0.14;
			double fp = settled ? 1 : Math.max(0, Math.min(1, (p - startP) / (endP - startP)));
			float flip = (float) Math.cos(fp * Math.PI); // 1 → -1
			boolean front = fp >= 0.5;
			float sx = Math.abs(flip);
			float cx = left + i * (cardW + gap) + cardW / 2f;
			float hover = (float) Math.sin(now() / 300.0 + i) * 2f;
			g.pose().pushMatrix();
			g.pose().translate(cx, cy + hover);
			g.pose().scale(Math.max(0.02f, sx), 1f);
			if (front) {
				g.pose().translate(-cx, -(cy + hover));
				drawCard(g, font, cards.get(i), cx, cy + hover, cardW, cardH, 1f, settled && i == CARD_COUNT / 2, i == CARD_COUNT / 2 ? 0 : 0.35f);
			} else {
				int gold = 0xFF000000 | KaisersSlayers.config().gamblingAccent;
				Ui.round(g, -cardW / 2, -cardH / 2, cardW / 2, cardH / 2, gold);
				Ui.round(g, -cardW / 2 + 2, -cardH / 2 + 2, cardW / 2 - 2, cardH / 2 - 2, 0xFF2B0F0F);
				for (int s = -cardH / 2 + 6; s < cardH / 2 - 6; s += 6) g.fill(-cardW / 2 + 6, s, cardW / 2 - 6, s + 1, Ui.argb(gold, 0x50));
				Ui.centered(g, font, "§6§l?", 0, -6, 2.0f, 0xFFFFFFFF, true);
			}
			g.pose().popMatrix();
			if (!settled && fp >= 0.5 && lastCardFlipped < k) {
				lastCardFlipped = k;
				sound(k == CARD_COUNT - 1 ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.WOODEN_BUTTON_CLICK_ON, k == CARD_COUNT - 1 ? 1.2f : 1.0f);
			}
		}
	}

	// ---------------------------------------------------------------- Ergebnis

	private static void renderReveal(GuiGraphicsExtractor g, Font font, int sw, int sh, long t, float alpha) {
		ModConfig cfg = KaisersSlayers.config();
		int color = LootPool.tierColor(result.tier());
		// Höhe der Karte aus dem Inhalt: Icon, Seltenheit, Name (1–2 Zeilen), Wert
		int w = 180;
		String name = (resultCount > 1 ? resultCount + "x " : "") + result.name();
		List<String> lines = wrap(font, name, (int) ((w - 16) / 1.2f), 2);
		boolean showValue = cfg.gamblingShowValue && resultValue > 0;
		int contentH = 48 + 7 + 11 + lines.size() * 12 + (showValue ? 6 + 9 : 0);
		int h = contentH + 28;
		float cx = sw / 2f, cy = centerY(sh);
		// Karte muss mit Platz für JACKPOT oben und Hinweis unten in den Bildschirm passen
		float room = sh - 20 - contentTop(sh) - (jackpot ? 30 : 0) - (onlyCommon ? 16 : 0);
		float fit = Math.min(1f, Math.min(room / (h + 16f), (sw - 20f) / (w + 16f)));
		if (jackpot) cy += 12 * fit;
		if (onlyCommon) cy -= 6 * fit;
		float pop = t < 250 ? 0.6f + 0.5f * (t / 250f) : (t < 400 ? 1.1f - 0.1f * ((t - 250) / 150f) : 1.0f);
		float pulse = 1f + (float) Math.sin(t / 140.0) * (jackpot ? 0.04f : 0.015f);
		float scale = fit * pop * pulse;
		int a = (int) (255 * alpha);

		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		int x1 = -w / 2, y1 = -h / 2, x2 = w / 2, y2 = h / 2;
		Ui.round(g, x1 - 6, y1 - 6, x2 + 6, y2 + 6, Ui.argb(color, (int) (0x70 * alpha)));
		Ui.round(g, x1, y1, x2, y2, Ui.argb(color, a));
		Ui.round(g, x1 + 2, y1 + 2, x2 - 2, y2 - 2, Ui.argb(0x101018, (int) (245 * alpha)));
		g.fillGradient(x1 + 2, y1 + 2, x2 - 2, y2 - 2, Ui.argb(color, (int) (0x10 * alpha)), Ui.argb(color, (int) (0x60 * alpha)));

		// Icon (48 px) oben mittig, darunter Seltenheit, Name (max. 2 Zeilen) und Wert – alles horizontal zentriert
		float y = y1 + 14;
		Ui.icon(g, ItemIcons.of(result.name()), -24, y, 3.0f);
		y += 48 + 7;
		String rarityName = onlyCommon ? "§7NO RARE DROP" : LootPool.tierName(result.tier());
		Ui.centered(g, font, rarityName, 0, y, 0.9f, Ui.argb(color, a), true);
		y += 11;
		for (String l : lines) {
			Ui.centered(g, font, "§f§l" + l, 0, y, 1.2f, Ui.argb(0xFFFFFF, a), true);
			y += 12;
		}
		if (showValue) {
			Ui.centered(g, font, "§6⛃ " + TextUtil.formatCoins(resultValue) + " coins", 0, y + 6, 1.0f, Ui.argb(0xFFFFFF, a), true);
		}
		g.pose().popMatrix();

		float cardTop = cy - (h / 2f + 6) * scale, cardBottom = cy + (h / 2f + 6) * scale;
		if (jackpot) {
			String jp = (t / 150 % 2 == 0 ? "§6§l" : "§e§l") + "★ JACKPOT ★";
			float js = (2.2f + (float) Math.sin(t / 120.0) * 0.12f) * fit;
			Ui.centered(g, font, jp, cx, cardTop - 6 - 9 * js, js, Ui.argb(0xFFFFFF, a), true);
		} else if (onlyCommon) {
			Ui.centered(g, font, "§7Only common drops – better luck next time!", cx, cardBottom + 6, 1.0f, Ui.argb(0xFFFFFF, a), true);
		}
	}

	// ---------------------------------------------------------------- Effekte

	private static void rays(GuiGraphicsExtractor g, float cx, float cy, float len, float angle, int color, int count) {
		for (int i = 0; i < count; i++) {
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate(angle + (float) (i * Math.PI * 2 / count));
			g.fill(0, -10, (int) len, 10, color);
			g.pose().popMatrix();
		}
	}

	private static void burst(int count, int color) {
		Minecraft mc = Minecraft.getInstance();
		float cx = mc.getWindow().getGuiScaledWidth() / 2f, cy = centerY(mc.getWindow().getGuiScaledHeight());
		for (int i = 0; i < count; i++) {
			Particle p = new Particle();
			double a = RANDOM.nextDouble() * Math.PI * 2;
			float speed = 60 + RANDOM.nextFloat() * 260;
			p.x = cx;
			p.y = cy;
			p.vx = (float) Math.cos(a) * speed;
			p.vy = (float) Math.sin(a) * speed - 80;
			p.maxLife = p.life = 1.2f + RANDOM.nextFloat() * 1.6f;
			p.size = 1.5f + RANDOM.nextFloat() * 3f;
			p.rot = RANDOM.nextFloat() * 6f;
			p.vr = (RANDOM.nextFloat() - 0.5f) * 12f;
			p.color = RANDOM.nextFloat() < 0.6f ? color : (RANDOM.nextBoolean() ? 0xFFFFD700 : 0xFFFFFFFF);
			PARTICLES.add(p);
		}
	}

	private static void confetti(int sw) {
		int[] colors = {0xFFFF5555, 0xFF55FF55, 0xFF5555FF, 0xFFFFFF55, 0xFFFF55FF, 0xFF55FFFF, 0xFFFFAA00};
		for (int i = 0; i < 3; i++) {
			Particle p = new Particle();
			p.x = RANDOM.nextFloat() * sw;
			p.y = -5;
			p.vx = (RANDOM.nextFloat() - 0.5f) * 40;
			p.vy = 40 + RANDOM.nextFloat() * 60;
			p.maxLife = p.life = 4f;
			p.size = 2 + RANDOM.nextFloat() * 2;
			p.rot = RANDOM.nextFloat() * 6f;
			p.vr = (RANDOM.nextFloat() - 0.5f) * 8f;
			p.color = colors[RANDOM.nextInt(colors.length)];
			PARTICLES.add(p);
		}
	}

	private static void updateParticles(GuiGraphicsExtractor g, float dt, int sw, int sh) {
		Iterator<Particle> it = PARTICLES.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			p.life -= dt;
			if (p.life <= 0 || p.y > sh + 10) {
				it.remove();
				continue;
			}
			p.vy += 120 * dt;
			p.vx *= (float) Math.pow(0.6, dt);
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.rot += p.vr * dt;
			int a = (int) (255 * Math.min(1f, p.life / p.maxLife * 2f));
			g.pose().pushMatrix();
			g.pose().translate(p.x, p.y);
			g.pose().rotate(p.rot);
			int s = Math.max(1, Math.round(p.size));
			g.fill(-s, -s / 2, s, s / 2 + 1, Ui.argb(p.color, a));
			g.pose().popMatrix();
		}
	}

	// ---------------------------------------------------------------- Text

	static List<String> wrap(Font font, String text, int maxW, int maxLines) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String test = line.isEmpty() ? word : line + " " + word;
			if (Ui.width(font, test) <= maxW || line.isEmpty()) {
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
		if (Ui.width(font, text) <= maxW) return text;
		String t = text;
		while (t.length() > 1 && Ui.width(font, t + "…") > maxW) t = t.substring(0, t.length() - 1);
		return t + "…";
	}

	/** Für Tests */
	static ItemStack icon(String name) {
		return ItemIcons.of(name);
	}
}
