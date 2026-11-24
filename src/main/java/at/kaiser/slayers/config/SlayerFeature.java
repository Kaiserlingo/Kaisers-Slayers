package at.kaiser.slayers.config;

import at.kaiser.slayers.slayer.SlayerType;

import java.util.ArrayList;
import java.util.List;

/**
 * Alle Funktionen, die pro Slayer-Typ einzeln ein- und ausgeschaltet werden.
 * Die GUI baut die Slayer-Menüs automatisch aus dieser Liste.
 */
public enum SlayerFeature {
	// ---------- Für jeden Slayer ----------
	TRACKING(null, "Tracking", "Quest-Tracker, Statistik und Profit für diesen Slayer.", true),
	BOSS_GLOW(null, "Boss-Highlight", "Dein eigener Boss leuchtet (Glow-Umriss in der Slayer-Farbe).", true),
	BOSS_PANEL(null, "Boss-Info-Panel", "Infos zum Boss unter dem Fadenkreuz (HP, Timer, Spezial-Infos).", true),
	MINIBOSS_HIGHLIGHT(null, "Miniboss-Highlight", "Minibosse dieses Slayers leuchten blau.", true),
	GAMBLING(null, "Gambling-Animation", "Gambling-Animation, wenn du diesen Boss tötest.", true),
	LOW_HP_ALERT(null, "Low-HP-Alert", "Hinweis, wenn der Boss fast tot ist.", false),

	// ---------- Revenant Horror ----------
	ZOMBIE_TNT_WARNING(SlayerType.ZOMBIE, "TNT-Warnung", "Atoned Horror (T5): Warnung + Zähler, wenn TNT in deiner Nähe liegt.", true),
	ZOMBIE_TNT_GLOW(SlayerType.ZOMBIE, "TNT-Highlight", "TNT rund um den Boss leuchtet rot.", true),
	ZOMBIE_HEAL_TRACKER(SlayerType.ZOMBIE, "Heal-Erkennung", "Meldet, wenn sich der Boss wieder hochheilt (HP steigt).", true),

	// ---------- Tarantula Broodfather ----------
	SPIDER_JUMP_WARNING(SlayerType.SPIDER, "Sprung-Warnung", "Warnung, wenn der Broodfather zum Sprung ansetzt.", true),
	SPIDER_BROOD_COUNTER(SlayerType.SPIDER, "Brood-Zähler", "Zählt die Spinnen-Minions rund um den Boss.", true),
	SPIDER_BROOD_GLOW(SlayerType.SPIDER, "Brood-Highlight", "Spinnen-Minions rund um den Boss leuchten.", false),

	// ---------- Sven Packmaster ----------
	WOLF_PUP_GLOW(SlayerType.WOLF, "Pup-Highlight", "Sven Pups leuchten, damit du sie schnell findest.", true),
	WOLF_PUP_COUNTER(SlayerType.WOLF, "Pup-Zähler", "Zeigt, wie viele Pups noch leben (Boss ist solange unverwundbar).", true),
	WOLF_PUP_ALERT(SlayerType.WOLF, "Pup-Alert", "Alert, sobald die Pups gerufen werden.", true),

	// ---------- Voidgloom Seraph ----------
	ENDERMAN_BEACON_OVERLAY(SlayerType.ENDERMAN, "Beacon-Overlay", "Yang Glyph: Beacon in der Hand / im Flug / platziert wird markiert, mit Pfeil + Countdown.", true),
	ENDERMAN_BEACON_ALERT(SlayerType.ENDERMAN, "Beacon-Alert", "Großer Alert + Sound, wenn ein Beacon platziert wird.", true),
	ENDERMAN_SKULL_OVERLAY(SlayerType.ENDERMAN, "Skull-Overlay", "Nukekubi-Skulls rund um den Boss leuchten.", true),
	ENDERMAN_SKULL_COUNTER(SlayerType.ENDERMAN, "Skull-Zähler", "Anzahl der aktiven Skulls im Boss-Panel.", true),
	ENDERMAN_HITS_DISPLAY(SlayerType.ENDERMAN, "Hits-Anzeige", "Verbleibende Hits in der Hit-Phase groß anzeigen.", true),
	ENDERMAN_LASER_TIMER(SlayerType.ENDERMAN, "Laser-Timer", "Zeigt, wie lange die Laser-Phase schon läuft.", true),
	ENDERMAN_PHASE_COLOR(SlayerType.ENDERMAN, "Phasen-Farbe", "Boss-Highlight-Farbe je nach Phase (Hits / Laser / Damage).", true),

	// ---------- Inferno Demonlord ----------
	BLAZE_DAGGER_DISPLAY(SlayerType.BLAZE, "Dagger-Anzeige", "Zeigt die benötigte Attunement + den richtigen Dagger; rot, wenn du den falschen hältst.", true),
	BLAZE_ATTUNEMENT_GLOW(SlayerType.BLAZE, "Attunement-Farbe", "Boss-Highlight in der Farbe der aktuellen Attunement.", true),
	BLAZE_PILLAR_TIMER(SlayerType.BLAZE, "Fire-Pillar-Timer", "Countdown + Hits des Fire Pillars im Boss-Panel.", true),
	BLAZE_PILLAR_ALERT(SlayerType.BLAZE, "Fire-Pillar-Alert", "Warnung + Sound kurz bevor der Pillar explodiert.", true),
	BLAZE_DEMON_GLOW(SlayerType.BLAZE, "Demon-Highlight", "Typhoeus und Quazii leuchten.", true),

	// ---------- Riftstalker Bloodfiend ----------
	VAMPIRE_STEAK_ALERT(SlayerType.VAMPIRE, "Steak-Stake-Alert", "Alert, sobald der Boss gestaked werden kann (≤ 20% HP).", true),
	VAMPIRE_TWINCLAWS_ALERT(SlayerType.VAMPIRE, "Twinclaws-Alert", "Warnung, wenn Twinclaws kommt.", true),
	VAMPIRE_ICHOR_GLOW(SlayerType.VAMPIRE, "Ichor/Spring-Highlight", "Blood Ichor und Killer Springs rund um den Boss leuchten (experimentell).", true),
	VAMPIRE_TWINCLAWS_TIMER(SlayerType.VAMPIRE, "Twinclaws-Timer", "Countdown bis zum nächsten Twinclaws (ab T2 ca. alle 7 Sekunden).", true);

	/** null = gilt für jeden Slayer */
	public final SlayerType type;
	public final String label;
	public final String description;
	public final boolean defaultOn;

	SlayerFeature(SlayerType type, String label, String description, boolean defaultOn) {
		this.type = type;
		this.label = label;
		this.description = description;
		this.defaultOn = defaultOn;
	}

	public String key() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Alle Features für einen Slayer: zuerst die gemeinsamen, dann die speziellen. */
	public static List<SlayerFeature> forType(SlayerType t) {
		List<SlayerFeature> out = new ArrayList<>();
		for (SlayerFeature f : values()) if (f.type == null) out.add(f);
		for (SlayerFeature f : values()) if (f.type == t) out.add(f);
		return out;
	}
}
