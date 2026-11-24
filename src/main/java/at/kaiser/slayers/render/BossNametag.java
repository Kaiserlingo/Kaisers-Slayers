package at.kaiser.slayers.render;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import at.kaiser.slayers.world.BossState;
import at.kaiser.slayers.world.NameParser;
import at.kaiser.slayers.world.WorldScanner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/** Text, der als Namensschild direkt über deinem Boss erscheint: Zeit, HP und die aktuelle Phase. */
public final class BossNametag {
	private BossNametag() {}

	public static Component textFor(Entity entity) {
		BossState st = KaisersSlayers.scanner().state();
		if (!st.found || st.entityId != entity.getId() || st.type == null) return null;
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.feature(st.type, SlayerFeature.BOSS_NAMETAG)) return null;
		String s = build(st, KaisersSlayers.tracker(), cfg, System.currentTimeMillis());
		return s.isEmpty() ? null : Component.literal(s);
	}

	/** Eine kompakte Zeile, z. B. "⏱ 2:41 ┃ 12.3M❤ 45% ┃ HITS 45" */
	public static String build(BossState st, SlayerTracker tracker, ModConfig cfg, long now) {
		StringBuilder b = new StringBuilder();
		long left = timeLeftMs(st, tracker, cfg, now);
		if (left >= 0) {
			b.append(left <= cfg.bossTimeWarnSec * 1000L ? "§c§l" : "§e").append("⏱ ").append(TextUtil.formatDuration(left));
		}
		double pct = st.hpPercent();
		if (st.hp >= 0) {
			sep(b).append(hpColor(pct)).append(TextUtil.formatCoins(st.hp)).append("❤");
			if (pct >= 0) b.append(String.format(Locale.ROOT, " §7%.0f%%", pct * 100));
		}
		String phase = phase(st, cfg, now);
		if (!phase.isEmpty()) sep(b).append(phase);
		return b.toString();
	}

	/** Die wichtigste Phase/Info des jeweiligen Slayers */
	public static String phase(BossState st, ModConfig cfg, long now) {
		SlayerType t = st.type;
		if (t == null) return "";
		switch (t) {
			case ENDERMAN -> {
				if (!st.placedBeaconTimes.isEmpty()) {
					long left = WorldScanner.BEACON_EXPLODE_MS - (now - st.placedBeaconTimes.get(0));
					return "§4§lBEACON " + TextUtil.formatSeconds(Math.max(0, left));
				}
				if (st.laserLeftMs(now) >= 0) return "§c§lLASER " + TextUtil.formatSeconds(st.laserLeftMs(now));
				if (st.hits > 0) return "§d§lHITS " + st.hits;
				if (st.beaconHeld) return "§cHolding Beacon";
				return "§aDAMAGE";
			}
			case BLAZE -> {
				StringBuilder b = new StringBuilder();
				if (st.attunement != null) b.append(NameParser.attunementColorCode(st.attunement)).append("§l").append(st.attunement);
				if (st.pillarSec >= 0) b.append(b.isEmpty() ? "" : " ").append("§6Pillar ").append(st.pillarSec).append("s");
				return b.toString();
			}
			case VAMPIRE -> {
				if (st.steakReady) return "§c§lSTAKE!";
				if (st.mania) return st.maniaSafe ? "§a§lMANIA ✔" : "§c§lMANIA – green terracotta!";
				if (st.lastTwinclaws > 0 && st.tier >= 2) {
					long next = 7000 - ((now - st.lastTwinclaws) % 7000);
					return "§bHoly Ice " + TextUtil.formatSeconds(next);
				}
				return "";
			}
			case WOLF -> {
				return st.pupCount > 0 ? "§f§lPUPS " + st.pupCount : "";
			}
			case SPIDER -> {
				if (now - st.jumpAt < 1200) return "§4§lLEAP!";
				if (st.cocoonAt > 0 && now - st.cocoonAt < 10_000) return "§fCOCOON " + TextUtil.formatSeconds(now - st.cocoonAt);
				return "";
			}
			case ZOMBIE -> {
				if (st.healAt > 0 && now - st.healAt < 3000) return "§a§lHEALING!";
				return st.tntCount > 0 ? "§c§lTNT x" + st.tntCount : "";
			}
		}
		return "";
	}

	public static long timeLeftMs(BossState st, SlayerTracker tracker, ModConfig cfg, long now) {
		if (st.timeLeftSec >= 0) return st.timeLeftSec * 1000L;
		if (tracker.bossSpawnTime() > 0) return Math.max(0, cfg.bossTimeLimitSec * 1000L - (now - tracker.bossSpawnTime()));
		return -1;
	}

	public static String hpColor(double pct) {
		if (pct < 0) return "§c";
		if (pct > 0.6) return "§a";
		if (pct > 0.3) return "§e";
		return "§c";
	}

	private static StringBuilder sep(StringBuilder b) {
		if (!b.isEmpty()) b.append(" §8┃ ");
		return b;
	}
}
