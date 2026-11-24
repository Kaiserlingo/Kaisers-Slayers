package at.kaiser.slayers;

import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.world.BossState;
import at.kaiser.slayers.world.Waypoints;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Beispieldaten für "/ks test": füllt Tracker, Profit und Boss-Infos, damit man alle HUDs
 * auch außerhalb von Hypixel sehen und im HUD-Editor platzieren kann.
 */
public final class DemoData {
	private DemoData() {}

	public static void load() {
		SlayerTracker t = KaisersSlayers.tracker();
		long now = System.currentTimeMillis();
		List<String> board = new ArrayList<>(List.of("Slayer Quest", "Voidgloom Seraph IV", "(1,200/1,500) Combat XP"));
		t.onChat("SLAYER QUEST STARTED!", now - 400_000);
		t.onScoreboard(board, now - 399_000);
		board.set(2, "Slay the boss!");
		t.onScoreboard(board, now - 380_000);
		t.onChat("NICE! SLAYER BOSS SLAIN!", now - 350_000);
		t.onChat("RARE DROP! (Summoning Eye) (+187% ✯ Magic Find)", now - 349_500);
		t.onChat("   Enderman Slayer LVL 7 - Next LVL in 120,000 XP!", now - 349_000);
		t.onChat("   RNG Meter - 412,550 Stored XP", now - 348_000);
		t.onChat("SLAYER QUEST STARTED!", now - 60_000);
		board.set(2, "(1,200/1,500) Combat XP");
		t.onScoreboard(board, now - 59_000);
		board.set(2, "Slay the boss!");
		t.onScoreboard(board, now - 25_000);
		for (long x = now - 400_000; x <= now; x += 1000) t.tick(x);
		t.profit().onDemo(SlayerType.ENDERMAN, Map.of("Null Sphere", 412L, "Summoning Eye", 1L, "Twilight Arrow Poison", 18L,
				"Endersnake Rune", 2L, "Mana Steal I", 1L, "Enchanted Ender Pearl", 96L));

		BossState st = KaisersSlayers.scanner().state();
		st.found = true;
		st.confirmedOwner = true;
		st.type = SlayerType.ENDERMAN;
		st.tier = 4;
		st.hp = 61_500_000;
		st.maxHp = 210_000_000;
		st.hits = 37;
		st.timeLeftSec = 155;
		st.skullCount = 3;
		var mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player != null) {
			Waypoints.put("demo-mini", "Voidcrazed Maniac", mc.player.getX() + 12, mc.player.getY(), mc.player.getZ() + 8, 0x5555FF, now + 120_000);
		}
	}

	public static boolean phaseIsDemo() {
		return KaisersSlayers.tracker().phase() == SlayerTracker.Phase.BOSS_ALIVE;
	}
}
