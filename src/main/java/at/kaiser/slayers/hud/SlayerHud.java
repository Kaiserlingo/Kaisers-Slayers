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
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(cfg.slayerHud.scale, cfg.slayerHud.scale);
		draw(g, mc.font, t, cfg, System.currentTimeMillis());
		g.pose().popMatrix();
	}

	/** Zeichnet die Karte bei (0,0). Gibt die Höhe zurück (für den HUD-Editor). */
	public static int draw(GuiGraphicsExtractor g, Font font, SlayerTracker t, ModConfig cfg, long now) {
		SlayerType type = t.type();
		SessionStats s = t.session();
		LifetimeStats.Entry life = type != null ? t.lifetime().get(type) : null;
		int accent = Ui.slayerAccent(type);

		List<String> rows = new ArrayList<>();
		double barPct = -1;
		String barLabel = null;
		int barFrom = accent, barTo = Ui.mix(accent, 0xFFFFFFFF, 0.4f);

		switch (t.phase()) {
			case GRINDING -> {
				if (t.progressMax() > 0) {
					barPct = t.progress() / t.progressMax();
					barLabel = String.format(Locale.ROOT, "§f%.0f%% §7%s/%s %s", barPct * 100,
							TextUtil.formatCoins(t.progress()), TextUtil.formatCoins(t.progressMax()), t.progressUnit());
				}
				rows.add("§7Quest running for §f" + TextUtil.formatDuration(now - t.questStart()));
			}
			case BOSS_ALIVE -> {
				long alive = t.bossSpawnTime() > 0 ? now - t.bossSpawnTime() : -1;
				long limit = cfg.bossTimeLimitSec * 1000L;
				int stand = KaisersSlayers.scanner().state().timeLeftSec;
				long left = stand >= 0 ? stand * 1000L : (alive >= 0 ? limit - alive : -1);
				if (left >= 0) {
					barPct = Math.max(0, left / (double) limit);
					barFrom = left <= cfg.bossTimeWarnSec * 1000L ? Ui.RED : 0xFFFFAA00;
					barTo = left <= cfg.bossTimeWarnSec * 1000L ? 0xFFFF8080 : 0xFFFFE080;
					barLabel = "§c§lBOSS §f" + TextUtil.formatDuration(Math.max(0, left)) + " left";
				}
				rows.add(alive >= 0 ? "§7Fight: §f" + TextUtil.formatSeconds(alive) : "§7Fight in progress");
			}
			case BOSS_SLAIN -> rows.add("§a§l✔ Boss slain");
			case NONE -> rows.add("§8No Slayer Quest active");
		}

		rows.add("§7Bosses §f" + s.kills + " §8· §f" + String.format(Locale.ROOT, "%.1f", s.killsPerHour()) + "/h §8· §f"
				+ TextUtil.formatDuration(s.activeMs) + (s.fails > 0 ? " §8· §c" + s.fails + "✘" : ""));
		if (!s.killTimes.isEmpty() || !s.spawnTimes.isEmpty()) {
			rows.add("§7Avg Spawn §f" + (s.spawnTimes.isEmpty() ? "-" : TextUtil.formatSeconds(SessionStats.average(s.spawnTimes)))
					+ " §8· §7Avg Kill §f" + (s.killTimes.isEmpty() ? "-" : TextUtil.formatSeconds(SessionStats.average(s.killTimes))));
		}
		if (life != null && life.bestKillMs > 0) rows.add("§7PB §a" + TextUtil.formatSeconds(life.bestKillMs));
		if (cfg.showLevelEta && type != null && life != null && life.level >= 0) {
			if (life.xpToNextLevel == 0) rows.add("§7" + type.levelName + " Slayer §a§lMAX");
			else if (life.xpToNextLevel > 0) {
				int perBoss = Math.max(1, type.xpPerBoss(t.tier()));
				long bosses = (life.xpToNextLevel + perBoss - 1) / perBoss;
				long avg = !s.questTimes.isEmpty() ? SessionStats.average(s.questTimes) : (s.kills > 0 ? s.activeMs / s.kills : 0);
				rows.add("§7LVL " + (life.level + 1) + " §b" + TextUtil.formatCoins(life.xpToNextLevel) + " XP §8(" + bosses + " bosses"
						+ (avg > 0 ? ", ~" + TextUtil.formatDuration(bosses * avg) : "") + ")");
			}
		}
		if (life != null && life.rngMeterXp >= 0) rows.add("§7RNG Meter §d" + TextUtil.formatInt(life.rngMeterXp) + " XP");

		// ---------- Zeichnen ----------
		int w = WIDTH;
		for (String r : rows) w = Math.max(w, font.width(r) + 12);
		w = Math.min(w, 240);
		lastWidth = w;
		int h = 22 + (barPct >= 0 ? 16 : 0) + rows.size() * 10 + 4;
		if (cfg.hudBackground) {
			Ui.panel(g, 0, 0, w, h, 0xE0121218, Ui.argb(accent, 0x90));
			Ui.hGradient(g, 1, 1, w - 1, 19, Ui.argb(accent, 0x60), 0x00121218);
		}
		Ui.icon(g, Ui.slayerIcon(type), 4, 2, 1.0f);
		String title = type != null ? type.color + "§l" + type.bossName + " §7" + TextUtil.toRoman(t.tier()) : "§c§lKaiser's Slayers";
		g.text(font, title, 23, 3, 0xFFFFFFFF, true);
		String sub = life != null && life.level >= 0 ? "§8" + type.levelName + " Slayer LVL " + life.level : "§8Slayer Tracker";
		Ui.text(g, font, sub, 23, 12, 0.75f, 0xFFFFFFFF, false);

		int y = 22;
		if (barPct >= 0) {
			Ui.bar(g, 5, y, w - 10, 11, barPct, barFrom, barTo);
			if (barLabel != null) Ui.centered(g, font, barLabel, w / 2f, y + 2, 0.75f, 0xFFFFFFFF, true);
			y += 16;
		}
		for (String r : rows) {
			g.text(font, r, 6, y, 0xFFFFFFFF, true);
			y += 10;
		}
		return h;
	}
}
