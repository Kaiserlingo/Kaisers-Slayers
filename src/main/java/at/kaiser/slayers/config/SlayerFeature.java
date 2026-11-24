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
	TRACKING(null, "Tracking", "Quest tracker, stats and profit for this slayer.", true),
	BOSS_GLOW(null, "Boss Highlight", "Your own boss glows (outline in the color set below).", true),
	BOSS_PANEL(null, "Boss Info Panel", "Boss info under your crosshair (HP, timer, phase info).", true),
	BOSS_NAMETAG(null, "Boss Timer Nametag", "Shows time left, HP and phase as a nametag right above your boss.", true),
	BOSS_BAR(null, "Boss HP Bar", "HP bar of your boss at the top of the screen.", true),
	MINIBOSS_HIGHLIGHT(null, "Miniboss Highlight", "Minibosses of this slayer glow (color under World & Highlights).", true),
	MINIBOSS_BEAM(null, "Miniboss Beam", "Beacon beam into the sky above every miniboss (custom color).", true),
	MINIBOSS_TRACER(null, "Miniboss Tracer", "Line from you to every miniboss so you find it instantly (custom color).", true),
	DROP_GLOW(null, "Highlight Ground Drops", "Highlights boss drops on the ground after the kill. Off by default to avoid spoilers.", false),
	GAMBLING(null, "Gambling Animation", "Plays the gambling animation when you slay this boss.", true),
	LOW_HP_ALERT(null, "Low HP Alert", "Alert when the boss is almost dead.", false),

	// ---------- Revenant Horror ----------
	ZOMBIE_TNT_WARNING(SlayerType.ZOMBIE, "TNT Warning", "Atoned Horror (T5): warning + counter when TNT is near you.", true),
	ZOMBIE_TNT_GLOW(SlayerType.ZOMBIE, "TNT Highlight", "TNT around the boss glows red.", true),
	ZOMBIE_HEAL_TRACKER(SlayerType.ZOMBIE, "Heal Detection", "Tells you when the boss heals back up (HP rises).", true),

	// ---------- Tarantula Broodfather ----------
	SPIDER_JUMP_WARNING(SlayerType.SPIDER, "Jump Warning", "Warning when the Broodfather leaps.", true),
	SPIDER_BROOD_COUNTER(SlayerType.SPIDER, "Brood Counter", "Counts the spider minions around the boss.", true),
	SPIDER_BROOD_GLOW(SlayerType.SPIDER, "Brood Highlight", "Spider minions around the boss glow.", false),
	SPIDER_COCOON_ALERT(SlayerType.SPIDER, "Cocoon Alert", "Conjoined Brood (T5): alert + timer when you cocooned your boss.", true),

	// ---------- Sven Packmaster ----------
	WOLF_PUP_GLOW(SlayerType.WOLF, "Pup Highlight", "Sven Pups glow so you find them quickly.", true),
	WOLF_PUP_COUNTER(SlayerType.WOLF, "Pup Counter", "Shows how many pups are alive (boss is invulnerable meanwhile).", true),
	WOLF_PUP_ALERT(SlayerType.WOLF, "Pup Alert", "Alert as soon as the pups are called.", true),

	// ---------- Voidgloom Seraph ----------
	ENDERMAN_BEACON_OVERLAY(SlayerType.ENDERMAN, "Beacon Overlay", "Yang Glyph: held / thrown / placed beacon gets highlighted, with arrow + countdown.", true),
	ENDERMAN_BEACON_BEAM(SlayerType.ENDERMAN, "Beacon Beam", "Red beam above every placed Yang Glyph beacon.", true),
	ENDERMAN_BEACON_ALERT(SlayerType.ENDERMAN, "Beacon Alert", "Yang Glyph: big alert + sound when the boss throws/places the beacon.", true),
	ENDERMAN_SKULL_OVERLAY(SlayerType.ENDERMAN, "Nukekubi Skull Overlay", "Nukekubi Fixations (skulls) around the boss glow.", true),
	ENDERMAN_SKULL_COUNTER(SlayerType.ENDERMAN, "Skull Counter", "Number of active Nukekubi skulls in the boss panel.", true),
	ENDERMAN_HITS_DISPLAY(SlayerType.ENDERMAN, "Hits Shield Display", "Shows the remaining hits of the hits phase in big.", true),
	ENDERMAN_LASER_TIMER(SlayerType.ENDERMAN, "Laser Timer", "Shows how long the laser phase is still running.", true),
	ENDERMAN_PHASE_COLOR(SlayerType.ENDERMAN, "Phase Color", "Boss glow changes color per phase (hits / laser / damage). Overrides your Boss Highlight color!", false),

	// ---------- Inferno Demonlord ----------
	BLAZE_DAGGER_DISPLAY(SlayerType.BLAZE, "Dagger Display", "Shows the required attunement + the right dagger; red if you hold the wrong one.", true),
	BLAZE_ATTUNEMENT_GLOW(SlayerType.BLAZE, "Attunement Color", "Boss glows in the color of the current attunement. Overrides your Boss Highlight color!", true),
	BLAZE_PILLAR_TIMER(SlayerType.BLAZE, "Fire Pillar Timer", "Fire Pillar countdown + hits in the boss panel.", true),
	BLAZE_PILLAR_ALERT(SlayerType.BLAZE, "Fire Pillar Alert", "Warning + sound right before the pillar explodes.", true),
	BLAZE_DEMON_GLOW(SlayerType.BLAZE, "Demon Highlight", "Typhoeus and Quazii glow.", true),
	BLAZE_IMMUNE_WARNING(SlayerType.BLAZE, "IMMUNE Warning", "Warning while the boss is IMMUNE (no damage possible).", true),

	// ---------- Riftstalker Bloodfiend ----------
	VAMPIRE_STEAK_ALERT(SlayerType.VAMPIRE, "Steak Stake Alert", "Alert as soon as the boss can be staked (҉ symbol or ≤ 20% HP).", true),
	VAMPIRE_TWINCLAWS_ALERT(SlayerType.VAMPIRE, "Twinclaws Alert", "Warning when Twinclaws is coming.", true),
	VAMPIRE_MANIA_ALERT(SlayerType.VAMPIRE, "Mania Alert", "Warning on MANIA – shows whether you stand on green terracotta.", true),
	VAMPIRE_ICHOR_GLOW(SlayerType.VAMPIRE, "Ichor/Spring Highlight", "Blood Ichors and Killer Springs around the boss glow (experimental).", true),
	VAMPIRE_TWINCLAWS_TIMER(SlayerType.VAMPIRE, "Holy Ice Timer", "Countdown to the next Twinclaws (T2+ every 7 seconds) – Holy Ice in time.", true);

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
