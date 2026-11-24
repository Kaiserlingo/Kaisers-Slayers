package at.kaiser.slayers;

import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.slayer.ProfitTracker;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProfitAndGamblingTest {
	@Test
	void sackHoverText() {
		String hover = "Added items:\n  +1,234 Revenant Flesh (Combat Sack)\n  +3 Foul Flesh (Combat Sack)\n  -5 Enchanted Rotten Flesh (Combat Sack)\nThis message can be disabled";
		Map<String, Long> items = ProfitTracker.parseSackText(hover);
		assertEquals(1234L, items.get("Revenant Flesh"));
		assertEquals(3L, items.get("Foul Flesh"));
		assertFalse(items.containsKey("Enchanted Rotten Flesh"));
	}

	@Test
	void profitCountsKillsCostsAndSacks() {
		SlayerTracker t = new SlayerTracker();
		t.setCostMultiplier(0.5);
		t.onChat("SLAYER QUEST STARTED!", 0);
		t.onScoreboard(new ArrayList<>(List.of("Revenant Horror IV", "(10/1,500) Combat XP")), 100);
		t.onScoreboard(new ArrayList<>(List.of("Revenant Horror IV", "Slay the boss!")), 30_000);
		t.onChat("NICE! SLAYER BOSS SLAIN!", 40_000);
		t.onChat("RARE DROP! (Revenant Catalyst)", 40_500);
		t.onSackItems(Map.of("Revenant Flesh", 50L), 45_000);
		ProfitTracker.Bucket b = t.profit().session(SlayerType.ZOMBIE);
		assertEquals(1, b.kills);
		assertEquals(25_000, b.cost);
		assertEquals(50L, b.items.get("Revenant Flesh"));
		assertEquals(1L, b.items.get("Revenant Catalyst"));
		double profit = b.profit((n, c) -> n.equals("Revenant Catalyst") ? 600_000.0 * c : 100.0 * c);
		assertEquals(600_000 + 5_000 - 25_000, profit, 0.001);
		// außerhalb eines Quests zählen Sacks nicht
		t.onScoreboard(new ArrayList<>(List.of("Purse: 5")), 200_000);
		t.onSackItems(Map.of("Revenant Flesh", 999L), 300_000);
		assertEquals(50L, b.items.get("Revenant Flesh"));
	}

	@Test
	void t5NamesAndMinibossLists() {
		SlayerTracker t = new SlayerTracker();
		t.onScoreboard(new ArrayList<>(List.of("Slayer Quest", "Atoned Horror V", "(1/2,400) Combat XP")), 0);
		assertEquals(SlayerType.ZOMBIE, t.type());
		assertEquals(5, t.tier());
		assertTrue(SlayerType.SPIDER.matchesBossName("☠ Conjoined Brood 9M❤"));
		assertTrue(SlayerType.ZOMBIE.minibosses(5).contains("Atoned Revenant"));
		assertFalse(SlayerType.ZOMBIE.minibosses(3).contains("Atoned Revenant"));
		assertEquals(10_000_000, SlayerType.ZOMBIE.maxHp(5));
	}

	@Test
	void minibossAndCocoonMessages() {
		SlayerTracker t = new SlayerTracker();
		List<String> ev = new ArrayList<>();
		t.setListener(new SlayerTracker.Listener() {
			public void onBossSpawned(SlayerType ty, int tier, long ms) {}
			public void onBossKilled(SlayerType ty, int tier, long ms, boolean nb) {}
			public void onQuestFailed(SlayerType ty, int tier) { ev.add("fail"); }
			public void onRareDrop(String r, String i, int c, boolean big) {}
			public void onMinibossMessage(String m) { ev.add("mini"); }
			public void onCocoon() { ev.add("cocoon"); }
			public void onLevelUp(SlayerType ty, int level) { ev.add("lvl " + ty + " " + level); }
		});
		t.onChat("SLAYER MINI-BOSS Revenant Champion HAS SPAWNED!", 0);
		t.onChat("YOU COCOONED YOUR SLAYER BOSS", 0);
		t.onChat("  LVL UP! ➜ Zombie Slayer LVL 8", 0);
		t.onChat("Your Slayer Quest has been cancelled!", 0);
		assertEquals(List.of("mini", "cocoon", "lvl ZOMBIE 8", "fail"), ev);
	}

	@Test
	void wheelLandsOnTarget() {
		for (int target = 0; target < 12; target++) {
			double a = GamblingAnimation.wheelAngle(target, 12, 6, 0.2);
			double seg = 30;
			int under = (int) Math.floor(Math.floorMod((long) Math.round(-a * 1000), 360_000L) / 1000.0 / seg);
			assertEquals(target, under, "Segment " + target);
		}
	}
}
