package at.kaiser.slayers;

import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.slayer.PickupTracker;
import at.kaiser.slayers.slayer.SlayerTracker;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Drop-Namen, Inventar-Drops und Auswahl des besten Drops. */
class DropsTest {
	@Test
	void dropNamesWithoutMagicFind() {
		assertEquals("Summoning Eye", SlayerTracker.cleanDropName("(Summoning Eye) (+187% ✯ Magic Find)"));
		assertEquals("Summoning Eye", SlayerTracker.cleanDropName("Summoning Eye (+187 ✯ Magic Find)"));
		assertEquals("Summoning Eye", SlayerTracker.cleanDropName("(Summoning Eye) (+42%  Magic Find)"));
		assertEquals("2x Foul Flesh", SlayerTracker.cleanDropName("(2x Foul Flesh) (+100% ✯ Magic Find)"));
		assertEquals("Enchanted Book (Smite VI)", SlayerTracker.cleanDropName("(Enchanted Book (Smite VI)) (+20% ✯ Magic Find)"));
		assertEquals("Judgement Core", SlayerTracker.cleanDropName("(Judgement Core)"));
	}

	@Test
	void chatDropsAreCountedOnce() {
		SlayerTracker t = new SlayerTracker();
		long now = 1_000_000;
		t.onChat("SLAYER QUEST STARTED!", now);
		java.util.List<String> board = new java.util.ArrayList<>(java.util.List.of("Slayer Quest", "Inferno Demonlord IV", "(10/2,400) Combat XP"));
		t.onScoreboard(board, now + 100);
		Map<String, Long> inv = new HashMap<>(Map.of("Blaze Rod", 10L, "Aspect of the Dragons", 1L));
		t.onInventory(inv, now + 200); // Basis
		inv.put("Blaze Rod", 34L); // +24 ohne Chat
		inv.put("Derelict Ashe", 3L);
		t.onInventory(inv, now + 400);
		t.onChat("RARE DROP! (High Class Archfiend Dice) (+187% ✯ Magic Find)", now + 500);
		inv.put("High Class Archfiend Dice", 1L); // kam schon über den Chat
		t.onInventory(inv, now + 700);
		t.pickup().block(now + 2000);
		inv.put("Enchanted Diamond", 64L); // aus einer Kiste geholt → ignorieren
		t.onInventory(inv, now + 900);
		var items = t.profit().session(at.kaiser.slayers.slayer.SlayerType.BLAZE).items;
		assertEquals(24L, items.get("Blaze Rod"));
		assertEquals(3L, items.get("Derelict Ashe"));
		assertEquals(1L, items.get("High Class Archfiend Dice"));
		assertNull(items.get("Enchanted Diamond"));
		assertNull(items.get("Aspect of the Dragons"));
	}

	@Test
	void pickupIgnoresBooksAndMenus() {
		PickupTracker p = new PickupTracker();
		Map<String, Long> inv = new HashMap<>();
		p.update(inv, 0, true);
		inv.put("Enchanted Book", 1L);
		assertTrue(p.update(inv, 100, true).isEmpty());
		inv.put("Null Sphere", 5L);
		assertEquals(5L, p.update(inv, 200, true).get("Null Sphere"));
		inv.put("Null Sphere", 9L);
		assertTrue(p.update(inv, 300, false).isEmpty()); // zählt gerade nicht
	}

	@Test
	void mostValuableDropWins() {
		// höherer Wert schlägt höhere Seltenheit
		assertTrue(GamblingAnimation.better(41_000_000, 1, 2_000_000, 3));
		assertFalse(GamblingAnimation.better(2_000_000, 4, 41_000_000, 1));
		// beide ohne Preis → Seltenheit entscheidet
		assertTrue(GamblingAnimation.better(0, 3, 0, 2));
	}
}
