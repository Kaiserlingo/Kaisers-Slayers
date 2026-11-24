package at.kaiser.slayers.world;

import at.kaiser.slayers.slayer.SlayerType;

/** Was der WorldScanner gerade über deinen Boss weiß (fürs Boss-Panel). */
public class BossState {
	public boolean found;
	public SlayerType type;
	public int entityId = -1;
	public double hp = -1;
	public double maxHpSeen = -1;
	public int hits = -1;
	public String attunement;
	public int timeLeftSec = -1;
	public long healAt;

	// Revenant
	public int tntCount;
	public double nearestTnt = -1;

	// Tarantula
	public long jumpAt;
	public int broodCount;

	// Sven
	public int pupCount;

	// Voidgloom
	public boolean beaconHeld;
	public boolean beaconFlying;
	public long beaconPlacedSince;
	public int skullCount;
	public long laserSince;

	// Blaze
	public int pillarSec = -1;
	public int pillarHits = -1;
	public int demonCount;
	public String heldItem = "";

	// Vampire
	public long lastTwinclaws;
	public boolean steakReady;

	public double hpPercent() {
		return hp >= 0 && maxHpSeen > 0 ? hp / maxHpSeen : -1;
	}

	public void resetBoss() {
		found = false;
		entityId = -1;
		hp = -1;
		maxHpSeen = -1;
		hits = -1;
		attunement = null;
		timeLeftSec = -1;
		healAt = 0;
		jumpAt = 0;
		beaconPlacedSince = 0;
		laserSince = 0;
		lastTwinclaws = 0;
		steakReady = false;
	}
}
