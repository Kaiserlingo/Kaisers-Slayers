package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import at.kaiser.slayers.world.BossState;
import at.kaiser.slayers.world.NameParser;
import at.kaiser.slayers.world.Waypoints;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Boss-Infos unter dem Fadenkreuz + Waypoint-Pfeile oben in der Mitte. */
public final class BossPanelHud {
	private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

	private BossPanelHud() {}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return;
		ModConfig cfg = KaisersSlayers.config();
		long now = System.currentTimeMillis();
		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();

		// ---------- Waypoint-Pfeile ----------
		if (cfg.waypointArrow) {
			int y = 22;
			for (Waypoints.Waypoint w : Waypoints.active(now)) {
				double dx = w.x - mc.player.getX(), dz = w.z - mc.player.getZ();
				double dist = Math.sqrt(dx * dx + dz * dz + Math.pow(w.y - mc.player.getY(), 2));
				String line = "§l" + arrow(dx, dz, mc.player.getYRot()) + " §r§f" + w.label + " §7"
						+ String.format(Locale.ROOT, dist < 10 ? "%.1fm" : "%.0fm", dist);
				int lw = mc.font.width(line);
				graphics.fill(screenW / 2 - lw / 2 - 3, y - 2, screenW / 2 + lw / 2 + 3, y + mc.font.lineHeight + 1, 0x80000000);
				graphics.text(mc.font, line, screenW / 2 - lw / 2, y, 0xFF000000 | w.color, true);
				y += mc.font.lineHeight + 4;
			}
		}

		// ---------- Boss-Panel ----------
		SlayerTracker tracker = KaisersSlayers.tracker();
		BossState st = KaisersSlayers.scanner().state();
		SlayerType type = tracker.type();
		if (type == null || !st.found || tracker.phase() != SlayerTracker.Phase.BOSS_ALIVE) return;
		if (!cfg.feature(type, SlayerFeature.BOSS_PANEL)) return;

		List<String> lines = buildLines(st, type, tracker, cfg, now);
		int y = screenH / 2 + cfg.bossPanelOffsetY;
		for (String l : lines) {
			int lw = mc.font.width(l);
			graphics.text(mc.font, l, screenW / 2 - lw / 2, y, 0xFFFFFFFF, true);
			y += mc.font.lineHeight + 1;
		}
	}

	static List<String> buildLines(BossState st, SlayerType type, SlayerTracker tracker, ModConfig cfg, long now) {
		List<String> out = new ArrayList<>();

		// Kopfzeile: Name + HP
		StringBuilder head = new StringBuilder(type.color + type.bossName + " " + TextUtil.toRoman(tracker.tier()));
		if (st.hp >= 0) {
			head.append(" §c").append(TextUtil.formatCoins(st.hp)).append("❤");
			double pct = st.hpPercent();
			if (pct >= 0) head.append(String.format(Locale.ROOT, " §8(%.0f%%)", pct * 100));
		}
		out.add(head.toString());

		// Zeit
		long left;
		if (st.timeLeftSec >= 0) left = st.timeLeftSec * 1000L;
		else if (tracker.bossSpawnTime() > 0) left = cfg.bossTimeLimitSec * 1000L - (now - tracker.bossSpawnTime());
		else left = -1;
		if (left >= 0) {
			String c = left <= cfg.bossTimeWarnSec * 1000L ? "§c§l" : "§7";
			String alive = tracker.bossSpawnTime() > 0 ? " §8| §7Kampf: §f" + TextUtil.formatSeconds(now - tracker.bossSpawnTime()) : "";
			out.add(c + "Zeit: " + TextUtil.formatDuration(left) + alive);
		}

		if (st.healAt > 0 && now - st.healAt < 3000) out.add("§a§lBOSS HEILT SICH!");

		switch (type) {
			case ZOMBIE -> {
				if (cfg.feature(type, SlayerFeature.ZOMBIE_TNT_WARNING) && st.tntCount > 0) {
					out.add("§c§lTNT x" + st.tntCount + " §7(" + String.format(Locale.ROOT, "%.1fm", st.nearestTnt) + ")");
				}
			}
			case SPIDER -> {
				if (cfg.feature(type, SlayerFeature.SPIDER_JUMP_WARNING) && now - st.jumpAt < 1200) out.add("§4§lSPRUNG!");
				if (cfg.feature(type, SlayerFeature.SPIDER_BROOD_COUNTER) && st.broodCount > 0) out.add("§7Brood: §f" + st.broodCount);
			}
			case WOLF -> {
				if (cfg.feature(type, SlayerFeature.WOLF_PUP_COUNTER) && st.pupCount > 0) {
					out.add("§f§lPups: " + st.pupCount + " §8(Boss unverwundbar)");
				}
			}
			case ENDERMAN -> {
				if (cfg.feature(type, SlayerFeature.ENDERMAN_HITS_DISPLAY) && st.hits > 0) out.add("§d§lHITS: " + st.hits);
				if (cfg.feature(type, SlayerFeature.ENDERMAN_LASER_TIMER) && st.laserSince > 0) {
					out.add("§c§lLASER §f" + TextUtil.formatSeconds(now - st.laserSince));
				}
				if (cfg.feature(type, SlayerFeature.ENDERMAN_BEACON_OVERLAY)) {
					if (st.beaconPlacedSince > 0) out.add("§4§lBEACON STEHT! §f" + TextUtil.formatSeconds(now - st.beaconPlacedSince));
					else if (st.beaconFlying) out.add("§c§lBEACON FLIEGT!");
					else if (st.beaconHeld) out.add("§cBoss hält Beacon");
				}
				if (cfg.feature(type, SlayerFeature.ENDERMAN_SKULL_COUNTER) && st.skullCount > 0) out.add("§dSkulls: §f" + st.skullCount);
			}
			case BLAZE -> {
				if (cfg.feature(type, SlayerFeature.BLAZE_DAGGER_DISPLAY) && st.attunement != null) {
					out.add("§7Attunement: " + NameParser.attunementColorCode(st.attunement) + "§l" + st.attunement
							+ " §8→ §f" + NameParser.daggerFor(st.attunement));
					boolean right = NameParser.holdsRightDagger(st.heldItem, st.attunement);
					boolean anyDagger = NameParser.holdsRightDagger(st.heldItem, "ASHEN") || NameParser.holdsRightDagger(st.heldItem, "SPIRIT");
					if (right) out.add("§a✔ Richtiger Dagger");
					else if (anyDagger) out.add("§c§l✘ FALSCHER DAGGER!");
				}
				if (cfg.feature(type, SlayerFeature.BLAZE_PILLAR_TIMER) && st.pillarSec >= 0) {
					out.add((st.pillarSec <= 2 ? "§c§l" : "§6") + "Pillar: " + st.pillarSec + "s §7· §f" + st.pillarHits + " Hits");
				}
				if (st.demonCount > 0) out.add("§dDemons: §f" + st.demonCount);
			}
			case VAMPIRE -> {
				if (cfg.feature(type, SlayerFeature.VAMPIRE_STEAK_ALERT) && st.steakReady) out.add("§c§lSTAKE BEREIT!");
				if (cfg.feature(type, SlayerFeature.VAMPIRE_TWINCLAWS_TIMER) && tracker.tier() >= 2 && st.lastTwinclaws > 0) {
					long next = 7000 - ((now - st.lastTwinclaws) % 7000);
					out.add("§7Twinclaws in: §f" + TextUtil.formatSeconds(next));
				}
			}
		}
		return out;
	}

	/** Pfeil in Blickrichtung-relativer Richtung zum Ziel. */
	static String arrow(double dx, double dz, float playerYaw) {
		double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
		double rel = targetYaw - playerYaw;
		rel = ((rel % 360) + 540) % 360 - 180; // -180..180, 0 = geradeaus
		int idx = (int) Math.round(rel / 45.0);
		idx = ((idx % 8) + 8) % 8;
		return ARROWS[idx];
	}
}
