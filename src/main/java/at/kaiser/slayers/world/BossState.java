package at.kaiser.slayers.world;

import at.kaiser.slayers.slayer.SlayerType;

import java.util.ArrayList;
import java.util.List;

/** Was der WorldScanner gerade über deinen Boss weiß (für Boss-Panel, Nametag, HP-Leiste). */
public class BossState {
	public boolean found;
	/** true, wenn der Boss über "Spawned by: <dein Name>" eindeutig dir gehört */
	public boolean confirmedOwner;
	public SlayerType type;
	public int tier;
	public int entityId = -1;
	public double x, y, z;
	public double hp = -1;
	public double maxHp = -1;
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
	public long cocoonAt;

	// Sven
	public int pupCount;

	// Voidgloom
	public boolean beaconHeld;
	public boolean beaconFlying;
	public long beaconThrownAt;
	public final List<Long> placedBeaconTimes = new ArrayList<>();
	public int skullCount;
	public long laserStart;

	// Blaze
	public int pillarSec = -1;
	public int pillarHits = -1;
	public int demonCount;
	public String heldItem = "";

	// Vampire
	public long lastTwinclaws;
	public boolean twinclawsVisible;
	public boolean steakReady;
	public boolean mania;
	public boolean maniaSafe;

	public double hpPercent() {
		if (hp < 0) return -1;
		double max = maxHp > 0 ? maxHp : -1;
		return max > 0 ? Math.min(1.0, hp / max) : -1;
	}

	/** Restzeit der Laser-Phase (8 Sekunden), oder -1 */
	public long laserLeftMs(long now) {
		if (laserStart <= 0) return -1;
		return Math.max(0, 8000 - (now - laserStart));
	}

	public void resetBoss() {
		found = false;
		confirmedOwner = false;
		entityId = -1;
		hp = -1;
		maxHp = -1;
		hits = -1;
		attunement = null;
		timeLeftSec = -1;
		healAt = 0;
		jumpAt = 0;
		laserStart = 0;
		lastTwinclaws = 0;
		twinclawsVisible = false;
		steakReady = false;
		mania = false;
		beaconHeld = false;
		beaconFlying = false;
		beaconThrownAt = 0;
		placedBeaconTimes.clear();
		skullCount = 0;
		tntCount = 0;
		pupCount = 0;
		broodCount = 0;
		demonCount = 0;
		pillarSec = -1;
	}
}
