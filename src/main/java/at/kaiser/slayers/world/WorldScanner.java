package at.kaiser.slayers.world;

import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.hud.Feedback;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Schaut sich regelmäßig die Entities in deiner Nähe an:
 * findet deinen Boss, Minibosse und alle Slayer-spezifischen Dinge (Beacons, Skulls, Pups, TNT, ...).
 * Liest nur – verändert nichts an der Welt.
 */
public final class WorldScanner {
	private static final int MINI_COLOR = 0x5555FF;
	private static final int DANGER_COLOR = 0xFF3333;
	private static final int SKULL_COLOR = 0xFF55FF;

	private final BossState state = new BossState();
	private final Set<Integer> knownMinis = new HashSet<>();
	private final Map<BlockPos, Long> placedBeacons = new HashMap<>();
	private final Set<Integer> alertedPillars = new HashSet<>();
	private int scanCount;
	private boolean lowHpAlerted;
	private boolean pupsAlerted;
	private boolean twinclawsVisible;
	private double lastBossY = Double.NaN;
	private long lastBossYTime;
	private double lastHp = -1;

	public BossState state() {
		return state;
	}

	public void reset() {
		state.resetBoss();
		knownMinis.clear();
		placedBeacons.clear();
		alertedPillars.clear();
		Highlights.clear();
		Waypoints.clear();
	}

	public void scan(Minecraft mc, SlayerTracker tracker, ModConfig cfg, long now) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;
		scanCount++;

		SlayerType type = tracker.type();
		SlayerTracker.Phase phase = tracker.phase();
		boolean questActive = phase != SlayerTracker.Phase.NONE;
		if (type == null || !questActive || !cfg.feature(type, SlayerFeature.TRACKING)) {
			if (state.found) state.resetBoss();
			Highlights.clear();
			return;
		}

		// ---------- Entities einsammeln ----------
		List<Entity> stands = new ArrayList<>();
		List<Entity> mobs = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == player) continue;
			if (is(e, "armor_stand")) stands.add(e);
			else mobs.add(e);
		}

		Map<Integer, Integer> glow = new HashMap<>();
		String me = player.getName().getString();
		state.type = type;

		// ---------- Minibosse ----------
		scanMinibosses(type, cfg, stands, mobs, player, glow, now);

		// ---------- Eigener Boss ----------
		Entity owner = null;
		double ownerDist = Double.MAX_VALUE;
		for (Entity s : stands) {
			String name = nameOf(s);
			if (name == null) continue;
			String by = NameParser.spawnedBy(name);
			if (by != null && by.equalsIgnoreCase(me)) {
				double d = s.distanceToSqr(player);
				if (d < ownerDist) {
					ownerDist = d;
					owner = s;
				}
			}
		}

		if (owner == null || phase != SlayerTracker.Phase.BOSS_ALIVE) {
			if (state.found) {
				state.resetBoss();
				lowHpAlerted = false;
				pupsAlerted = false;
				lastHp = -1;
				lastBossY = Double.NaN;
			}
			placedBeacons.clear();
			Highlights.set(glow);
			return;
		}

		// Namensschilder rund um den "Spawned by"-Stand
		state.found = true;
		boolean twinclawsNow = false;
		for (Entity s : stands) {
			if (horizontalDist(s, owner) > 1.5 || Math.abs(s.getY() - owner.getY()) > 2.5) continue;
			String name = nameOf(s);
			if (name == null) continue;
			if (name.contains(type.bossName)) {
				double hp = NameParser.hp(name);
				if (hp >= 0) {
					if (lastHp > 0 && hp > lastHp * 1.05 && cfg.feature(type, SlayerFeature.ZOMBIE_HEAL_TRACKER)
							&& type == SlayerType.ZOMBIE) {
						state.healAt = now;
					}
					lastHp = hp;
					state.hp = hp;
					state.maxHpSeen = Math.max(state.maxHpSeen, hp);
				}
				state.hits = NameParser.hits(name);
				String att = NameParser.attunement(name);
				if (att != null) state.attunement = att;
			}
			int timer = NameParser.timerSeconds(name);
			if (timer >= 0) state.timeLeftSec = timer;
			if (name.toUpperCase(java.util.Locale.ROOT).contains("TWINCLAW")) twinclawsNow = true;
		}

		// Boss-Entity: das nächste Lebewesen unter dem Namensschild
		LivingEntity boss = null;
		double best = Double.MAX_VALUE;
		for (Entity m : mobs) {
			if (!(m instanceof LivingEntity le)) continue;
			double h = horizontalDist(m, owner);
			double dy = owner.getY() - m.getY();
			if (h > 1.5 || dy < -0.5 || dy > 4.5) continue;
			if (h < best) {
				best = h;
				boss = le;
			}
		}
		state.entityId = boss != null ? boss.getId() : -1;

		// Low-HP
		if (cfg.feature(type, SlayerFeature.LOW_HP_ALERT) && !lowHpAlerted && state.hpPercent() >= 0 && state.hpPercent() <= 0.10) {
			lowHpAlerted = true;
			Feedback.alert("§a§lFAST TOT!", "§7Boss unter 10% HP", 1500, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f);
		}

		int bossColor = cfg.glowColor(type);

		switch (type) {
			case ZOMBIE -> scanZombie(cfg, mobs, player, glow);
			case SPIDER -> scanSpider(cfg, mobs, boss, glow, now);
			case WOLF -> scanWolf(cfg, stands, mobs, boss, glow);
			case ENDERMAN -> {
				scanEnderman(mc, cfg, stands, mobs, boss, glow, now);
				if (cfg.feature(type, SlayerFeature.ENDERMAN_PHASE_COLOR)) {
					if (state.laserSince > 0) bossColor = 0xFF5555;
					else if (state.hits > 0) bossColor = 0xAA00FF;
					else bossColor = 0x55FF55;
				}
			}
			case BLAZE -> {
				scanBlaze(cfg, stands, mobs, player, glow);
				if (cfg.feature(type, SlayerFeature.BLAZE_ATTUNEMENT_GLOW) && state.attunement != null) {
					bossColor = NameParser.attunementColor(state.attunement);
				}
			}
			case VAMPIRE -> scanVampire(cfg, stands, boss, glow, twinclawsNow, now);
		}

		if (boss != null && cfg.feature(type, SlayerFeature.BOSS_GLOW)) glow.put(boss.getId(), bossColor);
		Highlights.set(glow);
	}

	// ======================================================================= Minibosse

	private void scanMinibosses(SlayerType type, ModConfig cfg, List<Entity> stands, List<Entity> mobs,
								LocalPlayer player, Map<Integer, Integer> glow, long now) {
		if (type.minibosses.isEmpty()) return;
		Set<Integer> alive = new HashSet<>();
		for (Entity s : stands) {
			String name = nameOf(s);
			if (name == null || name.contains("Spawned by")) continue;
			String mini = null;
			for (String m : type.minibosses) {
				if (name.contains(m)) {
					mini = m;
					break;
				}
			}
			if (mini == null) continue;
			alive.add(s.getId());

			Entity mob = mobBelow(s, mobs);
			if (mob != null && cfg.feature(type, SlayerFeature.MINIBOSS_HIGHLIGHT)) glow.put(mob.getId(), MINI_COLOR);

			String key = "mini" + s.getId();
			if (knownMinis.add(s.getId())) {
				// nur frisch gespawnte Minibosse in deiner Nähe melden
				if (s.distanceTo(player) <= 12) {
					if (cfg.minibossAlert) {
						Feedback.alert("§9§lMINIBOSS!", "§b" + mini, 1800, SoundEvents.PLAYER_LEVELUP, 1.2f);
					}
					if (cfg.minibossWaypoint) {
						Waypoints.put(key, mini, s.getX(), s.getY(), s.getZ(), MINI_COLOR, now + cfg.waypointSeconds * 1000L);
					}
				}
			} else {
				Waypoints.put(key, mini, s.getX(), s.getY(), s.getZ(), MINI_COLOR, 0); // nur Position aktualisieren
			}
		}
		// tote Minibosse: Waypoint entfernen
		knownMinis.removeIf(id -> {
			if (!alive.contains(id)) {
				Waypoints.remove("mini" + id);
				return true;
			}
			return false;
		});
	}

	// ======================================================================= Revenant

	private void scanZombie(ModConfig cfg, List<Entity> mobs, LocalPlayer player, Map<Integer, Integer> glow) {
		int count = 0;
		double nearest = -1;
		for (Entity m : mobs) {
			if (!is(m, "tnt")) continue;
			double d = m.distanceTo(player);
			if (d > 12) continue;
			count++;
			if (nearest < 0 || d < nearest) nearest = d;
			if (cfg.feature(SlayerType.ZOMBIE, SlayerFeature.ZOMBIE_TNT_GLOW)) glow.put(m.getId(), DANGER_COLOR);
		}
		if (count > 0 && state.tntCount == 0 && cfg.feature(SlayerType.ZOMBIE, SlayerFeature.ZOMBIE_TNT_WARNING)) {
			Feedback.alert("§c§lTNT!", "§7Weg vom TNT!", 1200, SoundEvents.ANVIL_LAND, 1.8f);
		}
		state.tntCount = count;
		state.nearestTnt = nearest;
	}

	// ======================================================================= Tarantula

	private void scanSpider(ModConfig cfg, List<Entity> mobs, LivingEntity boss, Map<Integer, Integer> glow, long now) {
		if (boss == null) return;
		// Sprung: vertikale Geschwindigkeit aus Positionsänderung
		if (!Double.isNaN(lastBossY) && now > lastBossYTime) {
			double speed = (boss.getY() - lastBossY) / ((now - lastBossYTime) / 1000.0);
			if (speed > 4.0 && now - state.jumpAt > 2000) {
				state.jumpAt = now;
				if (cfg.feature(SlayerType.SPIDER, SlayerFeature.SPIDER_JUMP_WARNING)) {
					Feedback.alert("§4§lSPRUNG!", "§7Broodfather springt", 900, SoundEvents.ANVIL_LAND, 2.0f);
				}
			}
		}
		lastBossY = boss.getY();
		lastBossYTime = now;

		int brood = 0;
		for (Entity m : mobs) {
			if (m == boss) continue;
			if (!is(m, "spider") && !is(m, "cave_spider")) continue;
			if (m.distanceTo(boss) > 8) continue;
			brood++;
			if (cfg.feature(SlayerType.SPIDER, SlayerFeature.SPIDER_BROOD_GLOW)) glow.put(m.getId(), 0xFFAA00);
		}
		state.broodCount = brood;
	}

	// ======================================================================= Sven

	private void scanWolf(ModConfig cfg, List<Entity> stands, List<Entity> mobs, LivingEntity boss, Map<Integer, Integer> glow) {
		int pups = 0;
		for (Entity s : stands) {
			String name = nameOf(s);
			if (name == null || !name.contains("Sven Pup")) continue;
			if (boss != null && s.distanceTo(boss) > 25) continue;
			pups++;
			Entity mob = mobBelow(s, mobs);
			if (mob != null && cfg.feature(SlayerType.WOLF, SlayerFeature.WOLF_PUP_GLOW)) glow.put(mob.getId(), 0xFFFF55);
		}
		if (pups > 0 && !pupsAlerted && cfg.feature(SlayerType.WOLF, SlayerFeature.WOLF_PUP_ALERT)) {
			pupsAlerted = true;
			Feedback.alert("§f§lPUPS!", "§7Boss ist unverwundbar – töte " + pups + " Pups", 1800, SoundEvents.PLAYER_LEVELUP, 0.8f);
		}
		state.pupCount = pups;
	}

	// ======================================================================= Voidgloom

	private void scanEnderman(Minecraft mc, ModConfig cfg, List<Entity> stands, List<Entity> mobs,
							  LivingEntity boss, Map<Integer, Integer> glow, long now) {
		boolean beaconOverlay = cfg.feature(SlayerType.ENDERMAN, SlayerFeature.ENDERMAN_BEACON_OVERLAY);

		// Beacon in der Hand
		state.beaconHeld = false;
		if (boss instanceof EnderMan em) {
			BlockState carried = em.getCarriedBlock();
			state.beaconHeld = carried != null && carried.is(Blocks.BEACON);
		}

		// Beacon im Flug (fallender Block oder Armor Stand mit Beacon)
		state.beaconFlying = false;
		Entity center = boss != null ? boss : mc.player;
		for (Entity m : mobs) {
			if (m instanceof FallingBlockEntity fb && fb.getBlockState().is(Blocks.BEACON) && m.distanceTo(center) < 25) {
				state.beaconFlying = true;
				if (beaconOverlay) glow.put(m.getId(), DANGER_COLOR);
			}
		}

		// Skulls + Beacon-Stands
		int skulls = 0;
		for (Entity s : stands) {
			if (!(s instanceof LivingEntity le) || s.distanceTo(center) > 15) continue;
			ItemStack head = le.getItemBySlot(EquipmentSlot.HEAD);
			if (head.is(Items.BEACON)) {
				state.beaconFlying = true;
				if (beaconOverlay) glow.put(s.getId(), DANGER_COLOR);
			} else if (head.is(Items.PLAYER_HEAD) && nameOf(s) == null) {
				skulls++;
				if (cfg.feature(SlayerType.ENDERMAN, SlayerFeature.ENDERMAN_SKULL_OVERLAY)) glow.put(s.getId(), SKULL_COLOR);
			}
		}
		state.skullCount = skulls;

		// Laser-Phase: Boss sitzt auf einem Armor Stand
		if (boss != null && boss.isPassenger()) {
			if (state.laserSince == 0) state.laserSince = now;
		} else {
			state.laserSince = 0;
		}

		// Platzierte Beacons (Blöcke) – alle 4 Scans, Radius 10 um den Boss
		if (boss != null && scanCount % 4 == 0) {
			Set<BlockPos> found = new HashSet<>();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int bx = (int) Math.floor(boss.getX()), by = (int) Math.floor(boss.getY()), bz = (int) Math.floor(boss.getZ());
			for (int x = -10; x <= 10; x++) {
				for (int y = -4; y <= 4; y++) {
					for (int z = -10; z <= 10; z++) {
						pos.set(bx + x, by + y, bz + z);
						if (mc.level.getBlockState(pos).is(Blocks.BEACON)) found.add(pos.immutable());
					}
				}
			}
			for (BlockPos p : found) {
				if (!placedBeacons.containsKey(p)) {
					placedBeacons.put(p, now);
					if (cfg.feature(SlayerType.ENDERMAN, SlayerFeature.ENDERMAN_BEACON_ALERT)) {
						Feedback.alert("§c§lBEACON!", "§7Sofort zerstören!", 2000, SoundEvents.ANVIL_LAND, 1.2f);
					}
				}
				if (beaconOverlay) {
					Waypoints.put("beacon" + p.asLong(), "Beacon", p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5,
							DANGER_COLOR, now + 10_000);
				}
			}
			placedBeacons.keySet().removeIf(p -> {
				if (!found.contains(p)) {
					Waypoints.remove("beacon" + p.asLong());
					return true;
				}
				return false;
			});
		}
		long since = 0;
		for (long t : placedBeacons.values()) since = since == 0 ? t : Math.min(since, t);
		state.beaconPlacedSince = since;
	}

	// ======================================================================= Blaze

	private void scanBlaze(ModConfig cfg, List<Entity> stands, List<Entity> mobs, LocalPlayer player, Map<Integer, Integer> glow) {
		state.heldItem = player.getMainHandItem().getHoverName().getString();

		int bestSec = -1, bestHits = -1;
		int demons = 0;
		for (Entity s : stands) {
			String name = nameOf(s);
			if (name == null) continue;
			int[] pillar = NameParser.pillar(name);
			if (pillar != null && s.distanceTo(player) < 25) {
				if (bestSec < 0 || pillar[0] < bestSec) {
					bestSec = pillar[0];
					bestHits = pillar[1];
				}
				if (pillar[0] <= 2 && alertedPillars.add(s.getId())
						&& cfg.feature(SlayerType.BLAZE, SlayerFeature.BLAZE_PILLAR_ALERT)) {
					Feedback.alert("§c§lPILLAR!", "§7Explodiert in " + pillar[0] + "s – " + pillar[1] + " Hits", 1500,
							SoundEvents.ANVIL_LAND, 1.6f);
				}
			}
			if (NameParser.isBlazeDemon(name) && s.distanceTo(player) < 30) {
				demons++;
				Entity mob = mobBelow(s, mobs);
				if (mob != null && cfg.feature(SlayerType.BLAZE, SlayerFeature.BLAZE_DEMON_GLOW)) glow.put(mob.getId(), 0xFF55FF);
			}
		}
		state.pillarSec = bestSec;
		state.pillarHits = bestHits;
		state.demonCount = demons;
	}

	// ======================================================================= Vampire

	private void scanVampire(ModConfig cfg, List<Entity> stands, LivingEntity boss, Map<Integer, Integer> glow,
							 boolean twinclawsNow, long now) {
		double pct = state.hpPercent();
		boolean ready = pct >= 0 && pct <= 0.20;
		if (ready && !state.steakReady && cfg.feature(SlayerType.VAMPIRE, SlayerFeature.VAMPIRE_STEAK_ALERT)) {
			Feedback.alert("§c§lSTAKE!", "§7Jetzt Steak Stake benutzen", 2000, SoundEvents.PLAYER_LEVELUP, 1.8f);
		}
		state.steakReady = ready;

		if (twinclawsNow && !twinclawsVisible) {
			state.lastTwinclaws = now;
			if (cfg.feature(SlayerType.VAMPIRE, SlayerFeature.VAMPIRE_TWINCLAWS_ALERT)) {
				Feedback.alert("§4§lTWINCLAWS!", "§7Abstand halten / heilen", 1200, SoundEvents.ANVIL_LAND, 1.4f);
			}
		}
		twinclawsVisible = twinclawsNow;

		if (boss != null && cfg.feature(SlayerType.VAMPIRE, SlayerFeature.VAMPIRE_ICHOR_GLOW)) {
			for (Entity s : stands) {
				if (s.distanceTo(boss) > 12) continue;
				String name = nameOf(s);
				boolean named = name != null && (name.contains("Ichor") || name.contains("Spring"));
				boolean head = s instanceof LivingEntity le && le.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD) && name == null;
				if (named || head) glow.put(s.getId(), DANGER_COLOR);
			}
		}
	}

	// ======================================================================= Helfer

	/** Entity-Typ über die Registry-ID vergleichen, z. B. "armor_stand" */
	private static boolean is(Entity e, String id) {
		return EntityType.getKey(e.getType()).getPath().equals(id);
	}

	private static String nameOf(Entity e) {
		if (!e.hasCustomName() || e.getCustomName() == null) return null;
		String n = NameParser.clean(e.getCustomName().getString());
		return n.isEmpty() ? null : n;
	}

	private static double horizontalDist(Entity a, Entity b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Das Lebewesen direkt unter einem Namensschild. */
	private static Entity mobBelow(Entity stand, List<Entity> mobs) {
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity m : mobs) {
			if (!(m instanceof LivingEntity)) continue;
			double h = horizontalDist(m, stand);
			double dy = stand.getY() - m.getY();
			if (h > 1.2 || dy < -0.5 || dy > 4.0) continue;
			if (h < bestD) {
				bestD = h;
				best = m;
			}
		}
		return best;
	}
}
