package at.kaiser.slayers;

import at.kaiser.slayers.carry.CarryManager;
import at.kaiser.slayers.world.CarryScanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Carry-Kunden: Hinzufügen, Zählen, Bezahlung und Kill-Erkennung. */
class CarryTest {
	@Test
	void clientsAndProgress() {
		CarryManager m = new CarryManager();
		m.add("Technoblade", 10, 2_500_000, 0);
		m.add("technoblade", 5, 0, 0); // gleicher Kunde → Anzahl addiert, Preis bleibt
		CarryManager.Client c = m.find("TECHNOBLADE");
		assertEquals(15, c.total);
		assertEquals(2_500_000, c.price);
		assertEquals(37_500_000, c.due());

		for (int i = 0; i < 3; i++) assertNotNull(m.onBossKilled("Technoblade", "Voidgloom Seraph IV", 40_000, 1000 + i));
		assertEquals(3, c.done);
		assertEquals(12, m.totalLeft());
		assertEquals(40_000, c.averageKillMs());
		assertEquals("Voidgloom Seraph IV", c.lastBoss);

		m.addPaid("Technoblade", 10_000_000);
		assertEquals(27_500_000, c.due());
		m.adjust("Technoblade", -1);
		assertEquals(2, c.done);
		m.setDone("Technoblade", 99);
		assertEquals(15, c.done);
		assertTrue(c.finished());
		assertNull(m.onBossKilled("Technoblade", "", 1, 1)); // fertig → nicht mehr zählen
		assertNull(m.onBossKilled("Unknown", "", 1, 1));
		assertEquals(1, m.clearFinished());
		assertTrue(m.isEmpty());
		assertTrue(CarryManager.validName("jeb_"));
		assertFalse(CarryManager.validName("no spaces"));
	}

	@Test
	void ownerLabelsAndKillDetection() {
		assertEquals("Technoblade", CarryScanner.ownerName("Technoblade's SLAYER BOSS"));
		assertEquals("jeb_", CarryScanner.ownerName("Spawned by: jeb_"));
		assertNull(CarryScanner.ownerName("[Lv655]  Voidgloom Seraph IV 100 Hits"));
		// Todes-Animation gesehen → immer Kill
		assertTrue(CarryScanner.countsAsKill(true, 0.9, 50, 100));
		// bei niedriger HP verschwunden, Spieler in der Nähe → Kill
		assertTrue(CarryScanner.countsAsKill(false, 0.08, 40, 6));
		// Zeit abgelaufen → Quest failed, kein Kill
		assertFalse(CarryScanner.countsAsKill(false, 0.05, 0, 6));
		// volle HP verschwunden (z. B. Lobby gewechselt) → kein Kill
		assertFalse(CarryScanner.countsAsKill(false, 0.8, 40, 6));
		// zu weit weg → unsicher, kein Kill
		assertFalse(CarryScanner.countsAsKill(false, 0.05, 40, 60));
	}
}
