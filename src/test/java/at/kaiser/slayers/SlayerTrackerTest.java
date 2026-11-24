package at.kaiser.slayers;

import at.kaiser.slayers.slayer.LifetimeStats;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Testet die Quest-Erkennung mit nachgestellten Hypixel-Nachrichten. */
class SlayerTrackerTest {
	private static List<String> board(String boss, String state) {
		return new ArrayList<>(List.of("Purse: 1,234", "", "Slayer Quest", boss, state, "www.hypixel.net"));
	}

	@Test
	void fullQuestCycle() {
		SlayerTracker t = new SlayerTracker();
		List<String> events = new ArrayList<>();
		t.setListener(new SlayerTracker.Listener() {
			public void onBossSpawned(SlayerType ty, int tier, long ms) { events.add("spawn " + ms); }
			public void onBossKilled(SlayerType ty, int tier, long ms, boolean nb) { events.add("kill " + ms); }
			public void onQuestFailed(SlayerType ty, int tier) { events.add("fail"); }
			public void onRareDrop(String r, String i, int c, boolean big) { events.add("drop " + i + " " + big); }
		});
		long now = 1_000_000;
		t.onChat("§5§l  SLAYER QUEST STARTED!", now);
		t.onScoreboard(board("Revenant Horror IV", "(120/1,500) Combat XP"), now + 500);
		assertEquals(SlayerTracker.Phase.GRINDING, t.phase());
		assertEquals(SlayerType.ZOMBIE, t.type());
		assertEquals(4, t.tier());
		assertEquals(1500, t.progressMax());

		t.onScoreboard(board("Revenant Horror IV", "Slay the boss!"), now + 1000); // veraltet -> ignorieren
		assertTrue(events.isEmpty());

		t.onScoreboard(board("Revenant Horror IV", "Slay the boss!"), now + 40_000);
		assertEquals(SlayerTracker.Phase.BOSS_ALIVE, t.phase());
		t.onChat("  NICE! SLAYER BOSS SLAIN!", now + 62_500);
		t.onScoreboard(board("Revenant Horror IV", "Boss slain!"), now + 63_000);
		assertEquals(List.of("spawn 40000", "kill 22500"), events);
		assertEquals(1, t.session().kills);
		assertEquals(50_000, t.session().coinCost);

		t.onChat("§d§lCRAZY RARE DROP! §r§7(§r§fRevenant Catalyst§r§7) §r§b(+214% §r§b✯ Magic Find§r§b)", now + 63_100);
		t.onChat("RARE DROP! (2x Foul Flesh) (+100% ✯ Magic Find)", now + 63_200);
		t.onChat("VERY RARE DROP! (Enchanted Book (Smite VI))", now + 63_300);
		assertEquals(1, t.session().drops.get("Revenant Catalyst"));
		assertEquals(2, t.session().drops.get("Foul Flesh"));
		assertEquals(1, t.session().drops.get("Smite VI"));
		assertEquals(3, t.lastKillDrops().size());
		assertTrue(events.contains("drop Revenant Catalyst true"));

		t.onChat("   Zombie Slayer LVL 7 - Next LVL in 50,000 XP!", now + 63_400);
		t.onChat("   RNG Meter - 123,456 Stored XP", now + 63_500);
		LifetimeStats.Entry e = t.lifetime().get(SlayerType.ZOMBIE);
		assertEquals(7, e.level);
		assertEquals(50_000, e.xpToNextLevel);
		assertEquals(123_456, e.rngMeterXp);

		t.onScoreboard(board("Revenant Horror IV", "(0/1,500) Combat XP"), now + 65_000);
		assertEquals(SlayerTracker.Phase.GRINDING, t.phase());
		assertTrue(t.questStartKnown());

		t.onChat("  SLAYER QUEST FAILED!", now + 70_000);
		assertEquals(1, t.session().fails);
		assertTrue(events.contains("fail"));
	}

	@Test
	void dropsOutsideQuestAreIgnored() {
		SlayerTracker t = new SlayerTracker();
		t.onChat("RARE DROP! Enchanted Ender Pearl", 1000);
		assertTrue(t.session().drops.isEmpty());
	}

	@Test
	void joinMidBossCountsKillWithoutTime() {
		SlayerTracker t = new SlayerTracker();
		t.onScoreboard(board("Voidgloom Seraph III", "Slay the boss!"), 5000);
		t.onChat("NICE! SLAYER BOSS SLAIN!", 9000);
		assertEquals(1, t.session().kills);
		assertTrue(t.session().killTimes.isEmpty());
		t.onChat("   Enderman Slayer LVL 9 - LVL MAXED OUT!", 9100);
		assertEquals(0, t.lifetime().get(SlayerType.ENDERMAN).xpToNextLevel);
	}

	@Test
	void vampireKillsProgress() {
		SlayerTracker t = new SlayerTracker();
		t.onScoreboard(board("Riftstalker Bloodfiend II", "(3/10) Kills"), 1000);
		assertEquals(SlayerType.VAMPIRE, t.type());
		assertEquals(10, t.progressMax());
		assertEquals("Kills", t.progressUnit());
	}
}
