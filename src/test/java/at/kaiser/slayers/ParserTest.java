package at.kaiser.slayers;

import at.kaiser.slayers.gambling.LootPool;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import at.kaiser.slayers.world.NameParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParserTest {
	@Test
	void nameTags() {
		assertEquals(12_300_000, NameParser.hp("☠ Revenant Horror 12.3M❤"), 1);
		assertEquals(45, NameParser.hits("Voidgloom Seraph 45 Hits"));
		assertEquals("ASHEN", NameParser.attunement("☠ Inferno Demonlord 45M❤ ASHEN ♨8"));
		assertEquals(161, NameParser.timerSeconds("02:41"));
		assertArrayEquals(new int[]{7, 8}, NameParser.pillar("7s 8 hits"));
		assertEquals("Kaiser", NameParser.spawnedBy("Spawned by: Kaiser"));
		assertTrue(NameParser.holdsRightDagger("Firedust Dagger", "AURIC"));
		assertFalse(NameParser.holdsRightDagger("Twilight Dagger", "ASHEN"));
		assertTrue(NameParser.isBlazeDemon("ⓉⓎⓅⒽⓄⒺⓊⓈ 2M❤"));
	}

	@Test
	void ownerLabels() {
		// echtes Format aus dem Debug-Dump (Hypixel 2026)
		assertTrue(NameParser.isOwnerLabel("Kaiserlingo's SLAYER BOSS", "Kaiserlingo"));
		assertTrue(NameParser.isOwnerLabel("kaiserlingo's slayer boss", "Kaiserlingo"));
		assertFalse(NameParser.isOwnerLabel("Steve's SLAYER BOSS", "Kaiserlingo"));
		assertFalse(NameParser.isOwnerLabel("Kaiserlingo2's SLAYER BOSS", "Kaiserlingo"));
		assertFalse(NameParser.isOwnerLabel("xKaiserlingo's SLAYER BOSS", "Kaiserlingo"));
		assertTrue(NameParser.isOwnerLabel("Spawned by: Kaiserlingo", "Kaiserlingo"));
		assertTrue(NameParser.isOwnerMarker("Steve's SLAYER BOSS"));
		assertFalse(NameParser.isOwnerMarker("NICE! SLAYER BOSS SLAIN!"));
		assertFalse(NameParser.isOwnerMarker("[Lv655]  Voidgloom Seraph IV 100 Hits"));
		assertEquals(100, NameParser.hits("[Lv655]  Voidgloom Seraph IV 100 Hits"));
		assertEquals(238, NameParser.timerSeconds("03:58"));
	}

	@Test
	void numbersAndTime() {
		assertEquals(1500, TextUtil.parseNumber("1.5k"), 0.001);
		assertEquals("1:05", TextUtil.formatDuration(65_000));
		assertEquals("1.23M", TextUtil.formatCoins(1_234_567));
		assertEquals(4, TextUtil.romanToInt("IV"));
	}

	@Test
	void lootPool() {
		List<LootPool.Entry> pool = LootPool.build(SlayerType.ZOMBIE, List.of("Warden Heart|5", "My Item|3", ""),
				Map.of("Custom Drop", 1));
		assertTrue(pool.stream().anyMatch(e -> e.name().equals("My Item") && e.tier() == 3));
		assertEquals(1, pool.stream().filter(e -> e.name().equals("Warden Heart")).count());
		assertTrue(pool.stream().anyMatch(e -> e.name().equals("Custom Drop")));
	}

	@Test
	void ownMinibossMatching() {
		long msg = 10_000;
		// 1 = fremder Miniboss (schon lange da), 2 = deiner (taucht mit der Meldung auf), 3 = zeitgleich, aber weiter weg
		Map<Integer, Long> seen = Map.of(1, 0L, 2, 10_400L, 3, 9_800L);
		Map<Integer, Double> dist = Map.of(1, 3.0, 2, 4.0, 3, 12.0);
		assertEquals(2, at.kaiser.slayers.world.WorldScanner.pickOwnMini(seen, dist, java.util.Set.of(), msg));
		assertEquals(3, at.kaiser.slayers.world.WorldScanner.pickOwnMini(seen, dist, java.util.Set.of(2), msg));
		assertEquals(-1, at.kaiser.slayers.world.WorldScanner.pickOwnMini(seen, dist, java.util.Set.of(2, 3), msg));
	}
}
