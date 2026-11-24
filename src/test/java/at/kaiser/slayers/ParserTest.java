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
}
