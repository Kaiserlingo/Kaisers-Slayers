package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gui.Ui;
import at.kaiser.slayers.render.BossNametag;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import at.kaiser.slayers.world.BossState;
import at.kaiser.slayers.world.NameParser;
import at.kaiser.slayers.world.Waypoints;
import at.kaiser.slayers.world.WorldScanner;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Boss-HP-Leiste oben, Boss-Infos unter dem Fadenkreuz und Waypoint-Pfeile. */
public final class BossPanelHud {
	private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

	private BossPanelHud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || GamblingAnimation.hidesHud()) return;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return;
		ModConfig cfg = KaisersSlayers.config();
		long now = System.currentTimeMillis();
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();

		SlayerTracker tracker = KaisersSlayers.tracker();
		BossState st = KaisersSlayers.scanner().state();
		SlayerType type = tracker.type();
		boolean fight = type != null && st.found && tracker.phase() == SlayerTracker.Phase.BOSS_ALIVE;

		// ---------- Boss-HP-Leiste ----------
		int arrowsY = 22;
		if (fight && cfg.feature(type, SlayerFeature.BOSS_BAR)) {
			// nicht mit dem Slayer-HUD überlappen
			ModConfig.HudPos hp0 = cfg.slayerHud;
			int hudRight = hp0.enabled ? Math.round(hp0.x * sw + SlayerHud.lastWidth * hp0.scale) + 10 : 0;
			int bw = Math.min(200, sw - 16);
			int bx = Math.max(sw / 2 - bw / 2, hudRight);
			if (bx + bw > sw - 8) bw = Math.max(110, sw - 8 - bx);
			int by = 22;
			double pct = st.hpPercent();
			Ui.panel(g, bx - 4, by - 4, bx + bw + 4, by + 22, 0xD0101016, Ui.argb(type.defaultGlow, 0xA0));
			String name = type.color + "§l" + type.bossName + " " + TextUtil.toRoman(tracker.tier());
			Ui.draw(g, mc.font, name, bx, by - 1, 0xFFFFFFFF, true);
			long left = BossNametag.timeLeftMs(st, tracker, cfg, now);
			if (left >= 0) {
				String time = (left <= cfg.bossTimeWarnSec * 1000L ? "§c§l" : "§e") + "⏱ " + TextUtil.formatDuration(left);
				Ui.rightText(g, mc.font, time, bx + bw, by - 1, 0xFFFFFFFF);
			}
			int from = pct > 0.6 ? 0xFF2ECC71 : pct > 0.3 ? 0xFFF1C40F : 0xFFE74C3C;
			Ui.bar(g, bx, by + 9, bw, 9, pct < 0 ? 1 : pct, from, Ui.mix(from, 0xFFFFFFFF, 0.35f));
			String hp = st.hp >= 0 ? TextUtil.formatCoins(st.hp) + "❤" + (pct >= 0 ? String.format(Locale.ROOT, "  %.0f%%", pct * 100) : "") : "? ❤";
			Ui.centered(g, mc.font, "§f" + hp, bx + bw / 2f, by + 10, 0.75f, 0xFFFFFFFF, true);
			arrowsY = by + 30;
		}

		// ---------- Waypoint-Pfeile ----------
		if (cfg.waypointArrow) {
			int y = arrowsY;
			for (Waypoints.Waypoint w : Waypoints.active(now)) {
				double dx = w.x - mc.player.getX(), dz = w.z - mc.player.getZ();
				double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(w.y - mc.player.getY(), 2));
				String line = "§l" + arrow(dx, dz, mc.player.getYRot()) + " §r§f" + w.label + " §7"
						+ String.format(Locale.ROOT, dist < 10 ? "%.1fm" : "%.0fm", dist);
				int lw = Ui.width(mc.font, line);
				Ui.round(g, sw / 2 - lw / 2 - 5, y - 3, sw / 2 + lw / 2 + 5, y + mc.font.lineHeight + 2, 0xB0101016);
				g.fill(sw / 2 - lw / 2 - 5, y - 3, sw / 2 - lw / 2 - 3, y + mc.font.lineHeight + 2, 0xFF000000 | w.color);
				Ui.draw(g, mc.font, line, sw / 2 - lw / 2, y, 0xFF000000 | w.color, true);
				y += mc.font.lineHeight + 7;
			}
		}

		// ---------- Boss-Panel unter dem Fadenkreuz ----------
		if (!fight || !cfg.feature(type, SlayerFeature.BOSS_PANEL)) return;
		List<String> lines = buildLines(st, type, tracker, cfg, now);
		int y = sh / 2 + cfg.bossPanelOffsetY;
		for (String l : lines) {
			Ui.centered(g, mc.font, l, sw / 2f, y, 1.0f, 0xFFFFFFFF, true);
			y += mc.font.lineHeight + 1;
		}
	}

	/** Spezial-Infos je Slayer (Phasen, Zähler, Warnungen) */
	public static List<String> buildLines(BossState st, SlayerType type, SlayerTracker tracker, ModConfig cfg, long now) {
		List<String> out = new ArrayList<>();
		switch (type) {
			case ZOMBIE -> {
				if (st.healAt > 0 && now - st.healAt < 3000) out.add("§a§lBOSS IS HEALING!");
				if (cfg.feature(type, SlayerFeature.ZOMBIE_TNT_WARNING) && st.tntCount > 0) {
					out.add("§c§lTNT x" + st.tntCount + " §7(" + String.format(Locale.ROOT, "%.1fm", st.nearestTnt) + ")");
				}
			}
			case SPIDER -> {
				if (cfg.feature(type, SlayerFeature.SPIDER_JUMP_WARNING) && now - st.jumpAt < 1200) out.add("§4§lLEAP!");
				if (cfg.feature(type, SlayerFeature.SPIDER_BROOD_COUNTER) && st.broodCount > 0) out.add("§7Brood: §f" + st.broodCount);
				if (st.cocoonAt > 0 && now - st.cocoonAt < 15_000) out.add("§f§lCOCOON §7" + TextUtil.formatSeconds(now - st.cocoonAt));
			}
			case WOLF -> {
				if (cfg.feature(type, SlayerFeature.WOLF_PUP_COUNTER) && st.pupCount > 0) out.add("§f§lPups: " + st.pupCount + " §8(boss invulnerable)");
			}
			case ENDERMAN -> {
				if (cfg.feature(type, SlayerFeature.ENDERMAN_BEACON_OVERLAY)) {
					if (!st.placedBeaconTimes.isEmpty()) {
						long left = WorldScanner.BEACON_EXPLODE_MS - (now - st.placedBeaconTimes.get(0));
						out.add("§4§lBEACON! §f" + TextUtil.formatSeconds(Math.max(0, left)) + (st.placedBeaconTimes.size() > 1 ? " §7(x" + st.placedBeaconTimes.size() + ")" : ""));
					} else if (st.beaconFlying) out.add("§c§lBEACON THROWN!");
					else if (st.beaconHeld) out.add("§cBoss holds the beacon");
				}
				if (cfg.feature(type, SlayerFeature.ENDERMAN_HITS_DISPLAY) && st.hits > 0) out.add("§d§lHITS: " + st.hits);
				if (cfg.feature(type, SlayerFeature.ENDERMAN_LASER_TIMER) && st.laserLeftMs(now) >= 0) {
					out.add("§c§lLASER §f" + TextUtil.formatSeconds(st.laserLeftMs(now)));
				}
				if (cfg.feature(type, SlayerFeature.ENDERMAN_SKULL_COUNTER) && st.skullCount > 0) out.add("§dSkulls: §f" + st.skullCount);
			}
			case BLAZE -> {
				if (cfg.feature(type, SlayerFeature.BLAZE_DAGGER_DISPLAY) && st.attunement != null) {
					if ("IMMUNE".equals(st.attunement)) out.add("§c§lIMMUNE");
					else {
						out.add(NameParser.attunementColorCode(st.attunement) + "§l" + st.attunement + " §8→ §f" + NameParser.daggerFor(st.attunement));
						boolean right = NameParser.holdsRightDagger(st.heldItem, st.attunement);
						boolean anyDagger = NameParser.holdsRightDagger(st.heldItem, "ASHEN") || NameParser.holdsRightDagger(st.heldItem, "SPIRIT");
						if (right) out.add("§a✔ Right dagger");
						else if (anyDagger) out.add("§c§l✘ WRONG DAGGER!");
					}
				}
				if (cfg.feature(type, SlayerFeature.BLAZE_PILLAR_TIMER) && st.pillarSec >= 0) {
					out.add((st.pillarSec <= 3 ? "§c§l" : "§6") + "Pillar: " + st.pillarSec + "s §7· §f" + st.pillarHits + " hits");
				}
				if (st.demonCount > 0) out.add("§dDemons: §f" + st.demonCount);
			}
			case VAMPIRE -> {
				if (cfg.feature(type, SlayerFeature.VAMPIRE_STEAK_ALERT) && st.steakReady) out.add("§c§lSTAKE READY!");
				if (cfg.feature(type, SlayerFeature.VAMPIRE_MANIA_ALERT) && st.mania) {
					out.add(st.maniaSafe ? "§a§lMANIA §7– you're safe" : "§c§lMANIA §7– stand on green terracotta!");
				}
				if (cfg.feature(type, SlayerFeature.VAMPIRE_TWINCLAWS_TIMER) && tracker.tier() >= 2 && st.lastTwinclaws > 0) {
					long next = 7000 - ((now - st.lastTwinclaws) % 7000);
					out.add("§bHoly Ice in: §f" + TextUtil.formatSeconds(next));
				}
			}
		}
		return out;
	}

	/** Pfeil in Blickrichtung-relativer Richtung zum Ziel. */
	public static String arrow(double dx, double dz, float playerYaw) {
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = targetYaw - playerYaw;
		rel = ((rel % 360) + 540) % 360 - 180;
		int idx = (int) Math.round(rel / 45.0);
		idx = ((idx % 8) + 8) % 8;
		return ARROWS[idx];
	}
}
