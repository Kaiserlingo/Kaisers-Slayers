package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gui.Ui;
import at.kaiser.slayers.slayer.LifetimeStats;
import at.kaiser.slayers.slayer.SessionStats;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Das Slayer-HUD: Quest, Fortschritt, Zeiten, Level-Prognose – als Karte im Kaiser's-Slayers-Design. */
public final class SlayerHud {
	public static final int WIDTH = 168;
	/** Tatsächliche Breite beim letzten Zeichnen (wächst mit langen Zeilen) */
	/** Höhe beim letzten Zeichnen (zum Im-Bild-Halten) */
	public static int lastHeight = 0;
	public static int lastWidth = WIDTH;

	private SlayerHud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.slayerHud.enabled || mc.player == null || GamblingAnimation.hidesHud()) return;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return;
		SlayerTracker t = KaisersSlayers.tracker();
		if (cfg.hudOnlyWhenActive && t.phase() == SlayerTracker.Phase.NONE && t.session().kills == 0) return;

		int x = Math.round(cfg.slayerHud.x * mc.getWindow().getGuiScaledWidth());
		int y = Math.round(cfg.slayerHud.y * mc.getWindow().getGuiScaledHeight());
		// immer ganz im Bild halten (z. B. nach Auflösungs-Wechsel oder wenn die Karte breiter wird)
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		x = Math.max(0, Math.min(x, sw - Math.round(lastWidth * cfg.slayerHud.scale) - 1));
		y = Math.max(0, Math.min(y, sh - Math.round(lastHeight * cfg.slayerHud.scale) - 1));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(cfg.slayerHud.scale, cfg.slayerHud.scale);
		lastHeight = draw(g, mc.font, t, cfg, System.currentTimeMillis());
		g.pose().popMatrix();
	}

	/** Eine Zelle im Statistik-Raster: Bezeichnung + Wert */
	private record Cell(String label, String value) {}

	/**
	 * Zeichnet die Karte bei (0,0). Gibt die Höhe zurück (für den HUD-Editor).
	 * Aufbau: Kopf (Icon, Boss, Level-Chip) · Status mit schlankem Balken · 2-spaltiges Statistik-Raster · Level/RNG-Zeilen.
	 */
	public static int draw(GuiGraphicsExtractor g, Font font, SlayerTracker t, ModConfig cfg, long now) {
		SlayerType type = t.type();
		SessionStats s = t.session();
		LifetimeStats.Entry life = type != null ? t.lifetime().get(type) : null;
		int accent = Ui.slayerAccent(type);
		final int pad = 5;

		// ---------- Status ----------
		String statusL, statusR = "";
		double barPct = -1;
		int barCol = accent;
		switch (t.phase()) {
			case GRINDING -> {
				statusL = "§7Spawning";
				if (t.progressMax() > 0) {
					barPct = Math.min(1, t.progress() / t.progressMax());
					statusR = String.format(Locale.ROOT, "§f%s§7/%s §8%s §f%.0f%%", TextUtil.formatCoins(t.progress()),
							TextUtil.formatCoins(t.progressMax()), t.progressUnit(), barPct * 100);
				} else {
					statusR = "§f" + TextUtil.formatDuration(now - t.questStart());
				}
			}
			case BOSS_ALIVE -> {
				long alive = t.bossSpawnTime() > 0 ? now - t.bossSpawnTime() : -1;
				long limit = cfg.bossTimeLimitSec * 1000L;
				int stand = KaisersSlayers.scanner().state().timeLeftSec;
				long left = stand >= 0 ? stand * 1000L : (alive >= 0 ? limit - alive : -1);
				boolean warn = left >= 0 && left <= cfg.bossTimeWarnSec * 1000L;
				statusL = "§c§lBoss Alive";
				if (left >= 0) {
					barPct = Math.max(0, Math.min(1, left / (double) limit));
					barCol = warn ? Ui.RED : 0xFFFFAA00;
					statusR = (warn ? "§c§l" : "§e") + "⏱ " + TextUtil.formatDuration(Math.max(0, left));
				} else if (alive >= 0) {
					statusR = "§f" + TextUtil.formatSeconds(alive);
				}
			}
			case BOSS_SLAIN -> statusL = "§a§l✔ Boss Slain";
			default -> statusL = "§8No active Slayer Quest";
		}

		// ---------- Raster ----------
		List<Cell> cells = new ArrayList<>();
		cells.add(new Cell("Bosses", "§f" + s.kills + (s.fails > 0 ? " §c" + s.fails + "✘" : "")));
		cells.add(new Cell("Per Hour", "§f" + String.format(Locale.ROOT, "%.1f", s.killsPerHour())));
		cells.add(new Cell("Avg Spawn", "§f" + (s.spawnTimes.isEmpty() ? "–" : TextUtil.formatSeconds(SessionStats.average(s.spawnTimes)))));
		cells.add(new Cell("Avg Kill", "§f" + (s.killTimes.isEmpty() ? "–" : TextUtil.formatSeconds(SessionStats.average(s.killTimes)))));
		cells.add(new Cell("PB", life != null && life.bestKillMs > 0 ? "§a" + TextUtil.formatSeconds(life.bestKillMs) : "§8–"));
		cells.add(new Cell("Session", "§f" + TextUtil.formatDuration(s.activeMs)));

		List<String> extra = new ArrayList<>();
		if (cfg.showLevelEta && type != null && life != null && life.level >= 0) {
			if (life.xpToNextLevel == 0) extra.add("§7Level §a§lMAX");
			else if (life.xpToNextLevel > 0) {
				int perBoss = Math.max(1, type.xpPerBoss(t.tier()));
				long bosses = (life.xpToNextLevel + perBoss - 1) / perBoss;
				long avg = !s.questTimes.isEmpty() ? SessionStats.average(s.questTimes) : (s.kills > 0 ? s.activeMs / s.kills : 0);
				extra.add("§7LVL " + (life.level + 1) + " §b" + TextUtil.formatCoins(life.xpToNextLevel) + " XP §8· " + bosses + " bosses"
						+ (avg > 0 ? " · ~" + TextUtil.formatDuration(bosses * avg) : ""));
			}
		}
		if (life != null && life.rngMeterXp >= 0) extra.add("§7RNG Meter §d" + TextUtil.formatInt(life.rngMeterXp) + " XP");

		// ---------- Größe ----------
		String title = type != null ? "§l" + type.bossName + " " + TextUtil.toRoman(t.tier()) : "§lKaiser's Slayers";
		String chip = life != null && life.level >= 0 ? "LVL " + life.level : "";
		int chipW = chip.isEmpty() ? 0 : (int) (font.width(chip) * 0.75f) + 8;
		int cellW = 0;
		for (Cell c : cells) cellW = Math.max(cellW, font.width(c.label() + " ") + font.width(c.value()));
		int w = Math.max(WIDTH, Math.max(pad * 2 + cellW * 2 + 10, 22 + font.width(title) + 8 + chipW + pad));
		w = Math.max(w, pad * 2 + font.width(statusL) + 8 + font.width(statusR));
		for (String e : extra) w = Math.max(w, pad * 2 + (int) (font.width(e) * 0.85f));
		w = Math.min(w, 240);
		lastWidth = w;
		int gridRows = (cells.size() + 1) / 2;
		int h = 18 + 12 + (barPct >= 0 ? 5 : 0) + 4 + gridRows * 10 + (extra.isEmpty() ? 0 : 3 + extra.size() * 9) + 3;

		// ---------- Zeichnen ----------
		if (cfg.hudBackground) Ui.panel(g, 0, 0, w, h, 0xD8101015, Ui.argb(accent, 0x70));
		// Kopf: Icon + Name in Slayer-Farbe, Level-Chip rechts, feine Akzent-Linie darunter
		Ui.icon(g, Ui.slayerIcon(type), 3, 1, 1.0f);
		g.text(font, (type != null ? type.color : "§c") + title, 22, 5, 0xFFFFFFFF, true);
		if (!chip.isEmpty()) {
			int cx2 = w - pad, cx1 = cx2 - chipW;
			Ui.round(g, cx1, 4, cx2, 13, Ui.argb(accent, 0x60));
			Ui.text(g, font, chip, cx1 + 4, 6f, 0.75f, 0xFFFFFFFF, false);
		}
		Ui.hGradient(g, pad, 17, w - pad, 18, accent, Ui.argb(accent, 0x00));

		int y = 21;
		g.text(font, statusL, pad, y, 0xFFFFFFFF, true);
		if (!statusR.isEmpty()) Ui.rightText(g, font, statusR, w - pad, y, 0xFFFFFFFF);
		y += 11;
		if (barPct >= 0) {
			g.fill(pad, y, w - pad, y + 3, 0x40FFFFFF);
			int fw = (int) Math.round((w - pad * 2) * barPct);
			if (fw > 0) Ui.hGradient(g, pad, y, pad + fw, y + 3, barCol, Ui.mix(barCol, 0xFFFFFFFF, 0.35f));
			y += 5;
		}
		y += 3;

		// 2-spaltiges Raster: Bezeichnung grau, Wert weiß
		int colX2 = w / 2 + 2;
		for (int i = 0; i < cells.size(); i++) {
			Cell c = cells.get(i);
			int cx = i % 2 == 0 ? pad : colX2;
			int cy = y + (i / 2) * 10;
			g.text(font, "§7" + c.label(), cx, cy, 0xFFFFFFFF, true);
			int right = i % 2 == 0 ? colX2 - 6 : w - pad;
			Ui.rightText(g, font, c.value(), right, cy, 0xFFFFFFFF);
		}
		y += gridRows * 10;
		if (!extra.isEmpty()) {
			y += 1;
			g.fill(pad, y, w - pad, y + 1, 0x25FFFFFF);
			y += 3;
			for (String e : extra) {
				Ui.text(g, font, e, pad, y, 0.85f, 0xFFFFFFFF, true);
				y += 9;
			}
		}
		return h;
	}
}
