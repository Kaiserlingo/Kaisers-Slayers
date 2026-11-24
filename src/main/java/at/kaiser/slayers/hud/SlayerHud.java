package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.price.PriceService;
import at.kaiser.slayers.slayer.LifetimeStats;
import at.kaiser.slayers.slayer.SessionStats;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Das Slayer-HUD links am Bildschirm. */
public final class SlayerHud {
	private SlayerHud() {}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.hudEnabled || mc.options.hideGui || mc.player == null) return;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return;

		SlayerTracker tracker = KaisersSlayers.tracker();
		if (cfg.hudOnlyWhenActive && tracker.phase() == SlayerTracker.Phase.NONE
				&& tracker.session().kills == 0) return;

		List<String> lines = buildLines(tracker, cfg, KaisersSlayers.prices(), System.currentTimeMillis());

		int width = 0;
		for (String l : lines) width = Math.max(width, mc.font.width(l));
		int lineH = mc.font.lineHeight + 1;
		int height = lines.size() * lineH;

		graphics.pose().pushMatrix();
		graphics.pose().translate(cfg.hudX, cfg.hudY);
		graphics.pose().scale(cfg.hudScale, cfg.hudScale);
		if (cfg.hudBackground) {
			graphics.fill(-3, -3, width + 3, height + 2, 0x90000000);
			graphics.fill(-3, -3, -2, height + 2, 0xFFC0392B); // roter Akzentstreifen
		}
		int y = 0;
		for (String l : lines) {
			graphics.text(mc.font, l, 0, y, 0xFFFFFFFF, true);
			y += lineH;
		}
		graphics.pose().popMatrix();
	}

	/** Baut die HUD-Zeilen (mit §-Farbcodes). Öffentlich, damit /kslayers stats sie auch nutzen kann. */
	public static List<String> buildLines(SlayerTracker t, ModConfig cfg, PriceService prices, long now) {
		List<String> out = new ArrayList<>();
		out.add("§c§lKaiser's Slayers");

		SlayerType type = t.type();
		SessionStats s = t.session();
		LifetimeStats.Entry life = type != null ? t.lifetime().get(type) : null;

		// --- Quest-Zeile ---
		if (type != null) {
			String lvl = life != null && life.level >= 0 ? " §7(" + type.levelName + " LVL " + life.level + ")" : "";
			out.add(type.color + type.bossName + " " + TextUtil.toRoman(t.tier()) + lvl);
		}
		switch (t.phase()) {
			case GRINDING -> {
				String pct = t.progressMax() > 0
						? String.format(Locale.ROOT, "%.0f%%", 100.0 * t.progress() / t.progressMax()) : "?";
				String detail = t.progressMax() > 0
						? " §8(" + TextUtil.formatInt((long) t.progress()) + "/" + TextUtil.formatInt((long) t.progressMax()) + " " + t.progressUnit() + ")"
						: "";
				out.add("§7Grinding: §e" + pct + detail + " §8| §f" + TextUtil.formatDuration(now - t.questStart()));
			}
			case BOSS_ALIVE -> {
				if (t.bossSpawnTime() > 0) {
					long alive = now - t.bossSpawnTime();
					long left = cfg.bossTimeLimitSec * 1000L - alive;
					String leftColor = left <= cfg.bossTimeWarnSec * 1000L ? "§c§l" : "§a";
					out.add("§c§lBoss lebt! §f" + TextUtil.formatSeconds(alive)
							+ " §8| §7Limit: " + leftColor + TextUtil.formatDuration(Math.max(0, left)));
				} else {
					out.add("§c§lBoss lebt! §8(Spawn nicht erfasst)");
				}
			}
			case BOSS_SLAIN -> out.add("§a§lBoss besiegt! §7Neuen Quest starten");
			case NONE -> out.add("§8Kein Slayer-Quest aktiv");
		}

		// --- Session ---
		out.add("§7Session: §f" + s.kills + (s.kills == 1 ? " Boss" : " Bosse") + " §8| §f"
				+ String.format(Locale.ROOT, "%.1f", s.killsPerHour()) + "/h §8| §f" + TextUtil.formatDuration(s.activeMs)
				+ (s.fails > 0 ? " §8| §c" + s.fails + " failed" : ""));
		if (!s.killTimes.isEmpty() || !s.spawnTimes.isEmpty()) {
			out.add("§7Ø Spawn: §f" + (s.spawnTimes.isEmpty() ? "-" : TextUtil.formatSeconds(SessionStats.average(s.spawnTimes)))
					+ " §8| §7Ø Kill: §f" + (s.killTimes.isEmpty() ? "-" : TextUtil.formatSeconds(SessionStats.average(s.killTimes)))
					+ (life != null && life.bestKillMs > 0 ? " §8| §7Best: §a" + TextUtil.formatSeconds(life.bestKillMs) : ""));
		}

		// --- Profit ---
		if (cfg.showProfit && s.kills > 0) {
			double dropValue = dropValue(s, prices, cfg);
			double profit = dropValue - s.coinCost;
			double hours = s.activeMs / 3_600_000.0;
			String perHour = hours > 0.01 ? " §8(§6" + TextUtil.formatCoins(profit / hours) + "/h§8)" : "";
			out.add("§7Profit: " + (profit >= 0 ? "§6" : "§c") + TextUtil.formatCoins(profit) + perHour);
			out.add("§8Drops " + TextUtil.formatCoins(dropValue) + " · Kosten " + TextUtil.formatCoins(s.coinCost)
					+ (prices.hasData() ? "" : " · §cPreise laden..."));
		}

		// --- Level / RNG ---
		if (cfg.showLevelEta && type != null && life != null && life.level >= 0) {
			if (life.xpToNextLevel == 0) {
				out.add("§7" + type.levelName + " Slayer: §aLVL MAX");
			} else if (life.xpToNextLevel > 0) {
				int perBoss = type.xpPerBoss(t.tier());
				String eta = "";
				if (perBoss > 0) {
					long bosses = (life.xpToNextLevel + perBoss - 1) / perBoss;
					long avgQuest = !s.questTimes.isEmpty() ? SessionStats.average(s.questTimes)
							: (s.kills > 0 ? s.activeMs / s.kills : 0);
					eta = " §8(~" + bosses + " Bosse" + (avgQuest > 0 ? ", " + TextUtil.formatDuration(bosses * avgQuest) : "") + ")";
				}
				out.add("§7LVL " + (life.level + 1) + " in: §b" + TextUtil.formatInt(life.xpToNextLevel) + " XP" + eta);
			}
		}
		if (life != null && life.rngMeterXp >= 0) {
			out.add("§7RNG Meter: §d" + TextUtil.formatInt(life.rngMeterXp) + " XP");
		}

		// --- Drops ---
		if (cfg.showDrops && !s.drops.isEmpty()) {
			out.add("§7Drops (Session):");
			List<Map.Entry<String, Integer>> drops = new ArrayList<>(s.drops.entrySet());
			drops.sort(Comparator.comparingDouble((Map.Entry<String, Integer> e) ->
					prices.price(e.getKey(), cfg.instantSellPrices).value() * e.getValue()).reversed());
			int shown = 0;
			for (Map.Entry<String, Integer> e : drops) {
				if (shown++ >= 6) {
					out.add("§8  ... +" + (drops.size() - 6) + " weitere");
					break;
				}
				PriceService.Price p = prices.price(e.getKey(), cfg.instantSellPrices);
				String value = switch (p.source()) {
					case BAZAAR -> " §6" + TextUtil.formatCoins(p.value() * e.getValue());
					case AUCTION -> " §e" + TextUtil.formatCoins(p.value() * e.getValue()) + " §8(BIN)";
					case UNKNOWN -> " §8(?)";
				};
				out.add("§8 • §f" + e.getValue() + "x " + e.getKey() + value);
			}
		}
		return out;
	}

	public static double dropValue(SessionStats s, PriceService prices, ModConfig cfg) {
		double sum = 0;
		for (Map.Entry<String, Integer> e : s.drops.entrySet()) {
			sum += prices.price(e.getKey(), cfg.instantSellPrices).value() * e.getValue();
		}
		return sum;
	}
}
