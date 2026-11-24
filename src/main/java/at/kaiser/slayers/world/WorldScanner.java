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
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Schaut sich 10x pro Sekunde die Entities in deiner Nähe an:
 * findet deinen Boss, Minibosse und alle Slayer-spezifischen Dinge (Beacons, Skulls, Pups, TNT, ...).
 * Namensschilder kommen von Armor Stands und Text-Displays. Liest nur – verändert nichts an der Welt.
 */
public final class WorldScanner {
	private static final int DANGER_COLOR = 0xFF3333;
	private static final int SKULL_COLOR = 0xFF55FF;
	public static final long BEACON_EXPLODE_MS = 5000;

	/** Ein Namensschild in der Welt */
	public record Label(Entity entity, String text) {
		double y() {
			return entity.getY();
		}
	}

	private final BossState state = new BossState();
	private final Set<Integer> knownMinis = new HashSet<>();
	private final Map<BlockPos, Long> placedBeacons = new HashMap<>();
	private final Set<Integer> alertedPillars = new HashSet<>();
	private final Map<Integer, Long> dropSeen = new HashMap<>();
	private int scanCount;
	private boolean lowHpAlerted;
	private boolean pupsAlerted;
	private boolean immuneAlerted;
	private boolean maniaAlerted;
	private boolean steakAlerted;
	private boolean wasHoldingBeacon;
	private double lastBossY = Double.NaN;
	private long lastBossYTime;
	private double lastHp = -1;
	private double lastBossX, lastBossZ, lastBossFeetY;
	private int lastAttackedId = -1;

	public BossState state() {
		return state;
	}

	/** Vom AttackEntityCallback: welches Entity hat der Spieler zuletzt geschlagen (Fallback für die Boss-Erkennung). */
	public void onAttack(Entity e) {
		lastAttackedId = e.getId();
	}

	public void reset() {
		resetBossTracking();
		knownMinis.clear();
		placedBeacons.clear();
		dropSeen.clear();
		Highlights.clear();
		Waypoints.clear();
		WorldBeams.clear();
	}

	public void scan(Minecraft mc, SlayerTracker tracker, ModConfig cfg, long now) {
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null) return;
		scanCount++;

		SlayerType type = tracker.type();
		SlayerTracker.Phase phase = tracker.phase();
		boolean questActive = phase != SlayerTracker.Phase.NONE;
		boolean recentKill = tracker.lastKillAt() > 0 && now - tracker.lastKillAt() < cfg.dropGlowSeconds * 1000L;
		if (type == null || (!questActive && !recentKill) || !cfg.feature(type, SlayerFeature.TRACKING)) {
			if (state.found) resetBossTracking();
			Highlights.clear();
			WorldBeams.clear();
			return;
		}

		// ---------- Entities einsammeln ----------
		List<Label> labels = new ArrayList<>();
		List<Entity> mobs = new ArrayList<>();
		List<Entity> stands = new ArrayList<>();
		List<Entity> other = new ArrayList<>();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == player) continue;
			String label = labelText(e);
			if (label != null) labels.add(new Label(e, label));
			String id = typeId(e);
			if (id.equals("armor_stand")) stands.add(e);
			else if (e instanceof LivingEntity) mobs.add(e);
			else other.add(e);
		}

		Map<Integer, Integer> glow = new HashMap<>();
		List<WorldBeams.Beam> beams = new ArrayList<>();
		String[] myNames = {mc.getUser().getName(), player.getName().getString()};
		state.type = type;
		state.tier = tracker.tier();

		// ---------- Minibosse ----------
		if (questActive) scanMinibosses(type, cfg, labels, mobs, player, glow, beams, tracker, now);

		// ---------- Drops am Boden nach dem Kill ----------
		if (recentKill && cfg.feature(type, SlayerFeature.DROP_GLOW)) {
			for (Entity o : other) {
				if (!(o instanceof ItemEntity)) continue;
				double dx = o.getX() - lastBossX, dz = o.getZ() - lastBossZ;
				if (dx * dx + dz * dz > 12 * 12 || Math.abs(o.getY() - lastBossFeetY) > 6) continue;
				dropSeen.putIfAbsent(o.getId(), now);
				if (now - dropSeen.get(o.getId()) < cfg.dropGlowSeconds * 1000L) glow.put(o.getId(), 0xFFD700);
			}
		}

		// ---------- Andere Bosse markieren ----------
		if (cfg.otherBossGlow) {
			for (Label l : labels) {
				if (!type.matchesBossName(l.text()) || NameParser.hp(l.text()) < 0) continue;
				Entity mob = mobBelow(l.entity(), mobs, type.mobId, player);
				if (mob != null) glow.put(mob.getId(), cfg.otherBossColor);
			}
		}

		if (phase != SlayerTracker.Phase.BOSS_ALIVE) {
			if (state.found) resetBossTracking();
			placedBeacons.clear();
			Highlights.set(glow);
			WorldBeams.set(beams);
			return;
		}

		// ---------- Eigener Boss ----------
		Label owner = null;
		double ownerDist = Double.MAX_VALUE;
		for (Label l : labels) {
			if (!NameParser.isOwnerLabel(l.text(), myNames)) continue;
			double d = l.entity().distanceToSqr(player);
			if (d < ownerDist) {
				ownerDist = d;
				owner = l;
			}
		}

		Label bossLabel = null;
		if (owner != null) {
			bossLabel = closestLabel(labels, owner.entity(), 1.5, 2.5, l -> type.matchesBossName(l.text()));
		}
		state.confirmedOwner = owner != null && bossLabel != null;
		if (bossLabel == null && lastAttackedId >= 0) {
			// Fallback 1: der Boss, den ich zuletzt geschlagen habe
			Entity attacked = mc.level.getEntity(lastAttackedId);
			if (attacked != null && typeId(attacked).equals(type.mobId)) {
				bossLabel = closestLabel(labels, attacked, 1.5, 5, l -> type.matchesBossName(l.text()));
			}
		}
		if (bossLabel == null) {
			// Fallback 2: nächster Boss dieses Typs, der niemand anderem gehört
			double best = 20 * 20;
			for (Label l : labels) {
				if (!type.matchesBossName(l.text())) continue;
				Label ownerNear = closestLabel(labels, l.entity(), 1.5, 2.5, o -> o.text().contains("Spawned by"));
				if (ownerNear != null && !NameParser.isOwnerLabel(ownerNear.text(), myNames)) continue;
				double d = l.entity().distanceToSqr(player);
				if (d < best) {
					best = d;
					bossLabel = l;
				}
			}
		}

		Entity anchor = bossLabel != null ? bossLabel.entity() : (owner != null ? owner.entity() : null);
		Entity bossEntity = anchor != null ? mobBelow(anchor, mobs, type.mobId, player) : null;
		LivingEntity boss = bossEntity instanceof LivingEntity le ? le : null;
		state.found = anchor != null;
		state.entityId = boss != null ? boss.getId() : -1;
		if (boss != null) {
			state.x = boss.getX();
			state.y = boss.getY();
			state.z = boss.getZ();
			lastBossX = boss.getX();
			lastBossZ = boss.getZ();
			lastBossFeetY = boss.getY();
		}
		long tableHp = type.maxHp(tracker.tier());
		if (tableHp > 0) state.maxHp = Math.max(state.maxHp, tableHp);

		// Infos aus allen Namensschildern rund um den Boss
		boolean twinclaws = false, mania = false;
		state.timeLeftSec = -1;
		if (anchor != null) {
			for (Label l : labels) {
				if (horizontalDist(l.entity(), anchor) > 2.5 || Math.abs(l.y() - anchor.getY()) > 3.5) continue;
				String t = l.text();
				if (type.matchesBossName(t)) {
					double hp = NameParser.hp(t);
					if (hp >= 0) {
						if (lastHp > 0 && hp > lastHp * 1.05 && type == SlayerType.ZOMBIE
								&& cfg.feature(type, SlayerFeature.ZOMBIE_HEAL_TRACKER)) {
							state.healAt = now;
						}
						lastHp = hp;
						state.hp = hp;
						state.maxHp = Math.max(state.maxHp, hp);
					}
					state.hits = NameParser.hits(t);
					state.attunement = NameParser.attunement(t);
					state.steakReady = NameParser.isSteakReady(t);
				}
				int timer = NameParser.timerSeconds(t);
				if (timer >= 0) state.timeLeftSec = timer;
				String up = t.toUpperCase(Locale.ROOT);
				if (up.contains("TWINCLAW")) twinclaws = true;
				if (up.contains("MANIA")) mania = true;
				if (state.attunement == null) state.attunement = NameParser.attunement(t);
			}
		}

		// Low-HP
		double pct = state.hpPercent();
		if (cfg.feature(type, SlayerFeature.LOW_HP_ALERT) && !lowHpAlerted && pct >= 0 && pct <= 0.10) {
			lowHpAlerted = true;
			Feedback.alert("§a§lFAST TOT!", "§7Boss unter 10% HP", 1500, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f);
		}

		int bossColor = cfg.glowColor(type);
		Entity center = boss != null ? boss : (anchor != null ? anchor : player);

		switch (type) {
			case ZOMBIE -> scanZombie(cfg, other, player, glow);
			case SPIDER -> scanSpider(mobs, boss, glow, cfg, now);
			case WOLF -> scanWolf(cfg, labels, mobs, center, glow, player);
			case ENDERMAN -> {
				scanEnderman(mc, cfg, stands, mobs, other, boss, center, glow, beams, now);
				if (cfg.feature(type, SlayerFeature.ENDERMAN_PHASE_COLOR)) {
					if (state.laserStart > 0) bossColor = 0xFF5555;
					else if (state.hits > 0) bossColor = 0xAA00FF;
					else bossColor = 0x55FF55;
				}
			}
			case BLAZE -> {
				scanBlaze(cfg, labels, mobs, center, player, glow);
				if (cfg.feature(type, SlayerFeature.BLAZE_ATTUNEMENT_GLOW) && state.attunement != null) {
					bossColor = NameParser.attunementColor(state.attunement);
				}
			}
			case VAMPIRE -> scanVampire(mc, cfg, stands, labels, center, glow, twinclaws, mania, now);
		}

		if (boss != null && cfg.feature(type, SlayerFeature.BOSS_GLOW)) glow.put(boss.getId(), bossColor);
		Highlights.set(glow);
		WorldBeams.set(beams);
	}

	private void resetBossTracking() {
		state.resetBoss();
		lowHpAlerted = false;
		pupsAlerted = false;
		immuneAlerted = false;
		maniaAlerted = false;
		steakAlerted = false;
		wasHoldingBeacon = false;
		lastHp = -1;
		lastBossY = Double.NaN;
		alertedPillars.clear();
	}

	// ======================================================================= Minibosse

	private void scanMinibosses(SlayerType type, ModConfig cfg, List<Label> labels, List<Entity> mobs, LocalPlayer player,
								Map<Integer, Integer> glow, List<WorldBeams.Beam> beams, SlayerTracker tracker, long now) {
		List<String> minis = type.minibosses();
		if (minis.isEmpty()) return;
		Set<Integer> alive = new HashSet<>();
		for (Label l : labels) {
			String name = l.text();
			if (name.contains("Spawned by")) continue;
			String mini = null;
			for (String m : minis) {
				if (name.contains(m)) {
					mini = m;
					break;
				}
			}
			if (mini == null || l.entity().distanceTo(player) > 40) continue;
			Entity s = l.entity();
			alive.add(s.getId());

			Entity mob = mobBelow(s, mobs, null, player);
			if (mob != null && cfg.feature(type, SlayerFeature.MINIBOSS_HIGHLIGHT)) glow.put(mob.getId(), cfg.minibossColor);
			Entity at = mob != null ? mob : s;
			if (cfg.beamsEnabled && cfg.feature(type, SlayerFeature.MINIBOSS_BEAM)) {
				beams.add(new WorldBeams.Beam((int) Math.floor(at.getX()), (int) Math.floor(at.getY()), (int) Math.floor(at.getZ()),
						cfg.minibossBeamColor));
			}

			String key = "mini" + s.getId();
			if (knownMinis.add(s.getId())) {
				if (s.distanceTo(player) <= 15) {
					tracker.profit().addMiniboss(type);
					at.kaiser.slayers.KaisersSlayers.minibossAlert(mini);
					if (cfg.minibossWaypoint) {
						Waypoints.put(key, mini, at.getX(), at.getY(), at.getZ(), cfg.minibossColor, now + cfg.waypointSeconds * 1000L);
					}
				}
			} else {
				Waypoints.put(key, mini, at.getX(), at.getY(), at.getZ(), cfg.minibossColor, 0);
			}
		}
		knownMinis.removeIf(id -> {
			if (!alive.contains(id)) {
				Waypoints.remove("mini" + id);
				return true;
			}
			return false;
		});
	}

	// ======================================================================= Revenant

	private void scanZombie(ModConfig cfg, List<Entity> other, LocalPlayer player, Map<Integer, Integer> glow) {
		int count = 0;
		double nearest = -1;
		for (Entity m : other) {
			if (!typeId(m).equals("tnt")) continue;
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

	private void scanSpider(List<Entity> mobs, LivingEntity boss, Map<Integer, Integer> glow, ModConfig cfg, long now) {
		if (boss == null) return;
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
			String id = typeId(m);
			if (!id.equals("spider") && !id.equals("cave_spider")) continue;
			if (m.distanceTo(boss) > 8) continue;
			brood++;
			if (cfg.feature(SlayerType.SPIDER, SlayerFeature.SPIDER_BROOD_GLOW)) glow.put(m.getId(), 0xFFAA00);
		}
		state.broodCount = brood;
	}

	// ======================================================================= Sven

	private void scanWolf(ModConfig cfg, List<Label> labels, List<Entity> mobs, Entity center, Map<Integer, Integer> glow, LocalPlayer player) {
		int pups = 0;
		for (Label l : labels) {
			if (!l.text().contains("Sven Pup")) continue;
			if (l.entity().distanceTo(center) > 25) continue;
			pups++;
			Entity mob = mobBelow(l.entity(), mobs, "wolf", player);
			if (mob != null && cfg.feature(SlayerType.WOLF, SlayerFeature.WOLF_PUP_GLOW)) glow.put(mob.getId(), 0xFFFF55);
		}
		if (pups > 0 && !pupsAlerted && cfg.feature(SlayerType.WOLF, SlayerFeature.WOLF_PUP_ALERT)) {
			pupsAlerted = true;
			Feedback.alert("§f§lPUPS!", "§7Boss ist unverwundbar – töte " + pups + " Pups", 1800, SoundEvents.PLAYER_LEVELUP, 0.8f);
		}
		state.pupCount = pups;
	}

	// ======================================================================= Voidgloom

	private void scanEnderman(Minecraft mc, ModConfig cfg, List<Entity> stands, List<Entity> mobs, List<Entity> other,
							  LivingEntity boss, Entity center, Map<Integer, Integer> glow, List<WorldBeams.Beam> beams, long now) {
		boolean overlay = cfg.feature(SlayerType.ENDERMAN, SlayerFeature.ENDERMAN_BEACON_OVERLAY);
		boolean alerts = cfg.feature(SlayerType.ENDERMAN, SlayerFeature.ENDERMAN_BEACON_ALERT);

		// 1) Beacon in der Hand (der Enderman trägt den Beacon als Block)
		boolean held = false;
		if (boss instanceof EnderMan em) {
			held = isBeacon(em.getCarriedBlock());
		} else {
			for (Entity m : mobs) {
				if (m instanceof EnderMan em && m.distanceTo(center) < 20 && isBeacon(em.getCarriedBlock())) held = true;
			}
		}
		if (wasHoldingBeacon && !held) {
			state.beaconThrownAt = now; // Beacon wurde geworfen → Yang Glyph
			if (alerts) Feedback.alert("§c§lYANG GLYPH!", "§7Beacon geworfen – gleich zerstören!", 1800, SoundEvents.ANVIL_LAND, 1.2f);
		}
		wasHoldingBeacon = held;
		state.beaconHeld = held;

		// 2) Beacon im Flug: Armor Stand mit Beacon (Kopf/Hand) oder fallender Block
		boolean flying = false;
		for (Entity s : stands) {
			if (!(s instanceof LivingEntity le) || s.distanceTo(center) > 25) continue;
			if (le.getItemBySlot(EquipmentSlot.HEAD).is(Items.BEACON) || le.getMainHandItem().is(Items.BEACON)) {
				flying = true;
				if (overlay) glow.put(s.getId(), DANGER_COLOR);
			}
		}
		for (Entity o : other) {
			if (o instanceof FallingBlockEntity fb && isBeacon(fb.getBlockState()) && o.distanceTo(center) < 25) {
				flying = true;
				if (overlay) glow.put(o.getId(), DANGER_COLOR);
			}
		}
		state.beaconFlying = flying;

		// 3) Nukekubi-Skulls: Armor Stands mit Spielerkopf rund um den Boss
		int skulls = 0;
		for (Entity s : stands) {
			if (!(s instanceof LivingEntity le) || s.distanceTo(center) > 20) continue;
			if (le.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)) {
				skulls++;
				if (cfg.feature(SlayerType.ENDERMAN, SlayerFeature.ENDERMAN_SKULL_OVERLAY)) glow.put(s.getId(), SKULL_COLOR);
			}
		}
		state.skullCount = skulls;

		// 4) Laser-Phase: der Boss sitzt auf einem Fahrzeug, dauert 8 Sekunden
		if (boss != null && boss.getVehicle() != null) {
			if (state.laserStart == 0 || now - state.laserStart > 10_000) state.laserStart = now;
		} else if (state.laserStart > 0 && now - state.laserStart > 8_000) {
			state.laserStart = 0;
		}

		// 5) Platzierte Beacons (Blöcke) – ca. 3x pro Sekunde im Umkreis von 15 Blöcken
		if (scanCount % 3 == 0) {
			Set<BlockPos> found = new HashSet<>();
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			int bx = (int) Math.floor(center.getX()), by = (int) Math.floor(center.getY()), bz = (int) Math.floor(center.getZ());
			for (int x = -15; x <= 15; x++) {
				for (int y = -6; y <= 8; y++) {
					for (int z = -15; z <= 15; z++) {
						pos.set(bx + x, by + y, bz + z);
						if (isBeacon(mc.level.getBlockState(pos))) found.add(pos.immutable());
					}
				}
			}
			for (BlockPos p : found) {
				if (!placedBeacons.containsKey(p)) {
					placedBeacons.put(p, now);
					if (alerts && now - state.beaconThrownAt > 3000) {
						Feedback.alert("§c§lBEACON!", "§7Sofort zerstören!", 2000, SoundEvents.ANVIL_LAND, 1.2f);
					}
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
		state.placedBeaconTimes.clear();
		for (Map.Entry<BlockPos, Long> e : placedBeacons.entrySet()) {
			BlockPos p = e.getKey();
			state.placedBeaconTimes.add(e.getValue());
			if (overlay) {
				long left = Math.max(0, BEACON_EXPLODE_MS - (now - e.getValue()));
				Waypoints.put("beacon" + p.asLong(), "Beacon", p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, DANGER_COLOR, now + 15_000);
				Waypoints.setLabel("beacon" + p.asLong(), "Beacon §c" + String.format(Locale.ROOT, "%.1fs", left / 1000.0));
			}
			if (cfg.beamsEnabled && cfg.feature(SlayerType.ENDERMAN, SlayerFeature.ENDERMAN_BEACON_BEAM)) {
				beams.add(new WorldBeams.Beam(p.getX(), p.getY(), p.getZ(), DANGER_COLOR));
			}
		}
	}

	private static boolean isBeacon(BlockState s) {
		return s != null && s.is(Blocks.BEACON);
	}

	// ======================================================================= Blaze

	private void scanBlaze(ModConfig cfg, List<Label> labels, List<Entity> mobs, Entity center, LocalPlayer player, Map<Integer, Integer> glow) {
		state.heldItem = player.getMainHandItem().getHoverName().getString();

		int bestSec = -1, bestHits = -1, demons = 0;
		for (Label l : labels) {
			String name = l.text();
			int[] pillar = NameParser.pillar(name);
			if (pillar != null && l.entity().distanceTo(center) < 22) {
				if (bestSec < 0 || pillar[0] < bestSec) {
					bestSec = pillar[0];
					bestHits = pillar[1];
				}
				if (pillar[0] <= 3 && alertedPillars.add(l.entity().getId())
						&& cfg.feature(SlayerType.BLAZE, SlayerFeature.BLAZE_PILLAR_ALERT)) {
					Feedback.alert("§5§lPILLAR " + pillar[0] + "s!", "§7Noch " + pillar[1] + " Hits", 1500, SoundEvents.ANVIL_LAND, 1.6f);
				}
			}
			if (NameParser.isBlazeDemon(name) && l.entity().distanceTo(center) < 30) {
				demons++;
				Entity mob = mobBelow(l.entity(), mobs, null, player);
				if (mob != null && cfg.feature(SlayerType.BLAZE, SlayerFeature.BLAZE_DEMON_GLOW)) glow.put(mob.getId(), 0xFF55FF);
			}
		}
		state.pillarSec = bestSec;
		state.pillarHits = bestHits;
		state.demonCount = demons;

		boolean immune = "IMMUNE".equals(state.attunement);
		if (immune && !immuneAlerted && cfg.feature(SlayerType.BLAZE, SlayerFeature.BLAZE_IMMUNE_WARNING)) {
			Feedback.alert("§c§lIMMUNE!", "§7Boss nimmt gerade keinen Schaden", 1200, SoundEvents.VILLAGER_NO, 1.0f);
		}
		immuneAlerted = immune;
	}

	// ======================================================================= Vampire

	private void scanVampire(Minecraft mc, ModConfig cfg, List<Entity> stands, List<Label> labels, Entity center,
							 Map<Integer, Integer> glow, boolean twinclawsNow, boolean maniaNow, long now) {
		double pct = state.hpPercent();
		boolean ready = state.steakReady || (pct >= 0 && pct <= 0.20);
		if (ready && !steakAlerted && cfg.feature(SlayerType.VAMPIRE, SlayerFeature.VAMPIRE_STEAK_ALERT)) {
			Feedback.alert("§c§lSTAKE!", "§7Jetzt Steak Stake benutzen", 2000, SoundEvents.PLAYER_LEVELUP, 1.8f);
		}
		steakAlerted = ready;
		state.steakReady = ready;

		if (twinclawsNow && !state.twinclawsVisible) {
			state.lastTwinclaws = now;
			if (cfg.feature(SlayerType.VAMPIRE, SlayerFeature.VAMPIRE_TWINCLAWS_ALERT)) {
				Feedback.alert("§b§lHOLY ICE!", "§7Twinclaws kommt", 1200, SoundEvents.ANVIL_LAND, 1.4f);
			}
		}
		state.twinclawsVisible = twinclawsNow;

		state.mania = maniaNow;
		if (maniaNow && mc.player != null) {
			BlockState below = mc.level.getBlockState(mc.player.blockPosition().below());
			state.maniaSafe = below.getBlock().getDescriptionId().contains("green_terracotta");
			if (!maniaAlerted && cfg.feature(SlayerType.VAMPIRE, SlayerFeature.VAMPIRE_MANIA_ALERT)) {
				Feedback.alert("§c§lMANIA!", "§7Auf grünen Boden stellen", 1500, SoundEvents.ANVIL_LAND, 1.0f);
			}
		}
		maniaAlerted = maniaNow;

		if (cfg.feature(SlayerType.VAMPIRE, SlayerFeature.VAMPIRE_ICHOR_GLOW)) {
			for (Entity s : stands) {
				if (s.distanceTo(center) > 12 || !(s instanceof LivingEntity le)) continue;
				if (le.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD)) glow.put(s.getId(), DANGER_COLOR);
			}
			for (Label l : labels) {
				if (l.entity().distanceTo(center) > 12) continue;
				if (l.text().contains("Ichor") || l.text().contains("Spring")) glow.put(l.entity().getId(), DANGER_COLOR);
			}
		}
	}

	// ======================================================================= Helfer

	/** Text eines Namensschilds: Armor Stand mit Namen oder Text-Display. */
	public static String labelText(Entity e) {
		if (e instanceof Display.TextDisplay td) {
			String t = NameParser.clean(td.getText().getString());
			return t.isEmpty() ? null : t;
		}
		if (!typeId(e).equals("armor_stand") || !e.hasCustomName() || e.getCustomName() == null) return null;
		String n = NameParser.clean(e.getCustomName().getString());
		return n.isEmpty() ? null : n;
	}

	public static String typeId(Entity e) {
		return EntityType.getKey(e.getType()).getPath();
	}

	private static double horizontalDist(Entity a, Entity b) {
		double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static Label closestLabel(List<Label> labels, Entity to, double maxH, double maxDy, Predicate<Label> filter) {
		Label best = null;
		double bestD = Double.MAX_VALUE;
		for (Label l : labels) {
			if (l.entity() == to || !filter.test(l)) continue;
			double h = horizontalDist(l.entity(), to);
			double dy = Math.abs(l.y() - to.getY());
			if (h > maxH || dy > maxDy) continue;
			double d = h * h + dy * dy;
			if (d < bestD) {
				bestD = d;
				best = l;
			}
		}
		return best;
	}

	/** Das Lebewesen direkt unter einem Namensschild (bevorzugt ein bestimmter Mob-Typ). */
	private static Entity mobBelow(Entity label, List<Entity> mobs, String mobId, Entity player) {
		Entity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity m : mobs) {
			if (m == player || !(m instanceof LivingEntity)) continue;
			if (mobId != null && !typeId(m).equals(mobId)) continue;
			double h = horizontalDist(m, label);
			double dy = label.getY() - m.getY();
			if (h > 1.5 || dy < -1.0 || dy > 5.0) continue;
			double d = h + Math.abs(dy) * 0.1;
			if (d < bestD) {
				bestD = d;
				best = m;
			}
		}
		if (best == null && mobId != null) return mobBelow(label, mobs, null, player);
		return best;
	}
}
