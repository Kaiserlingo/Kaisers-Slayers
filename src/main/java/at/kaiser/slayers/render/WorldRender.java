package at.kaiser.slayers.render;

import at.kaiser.slayers.mixin.BlockEntityRenderStateAccessor;
import at.kaiser.slayers.world.Boxes;
import at.kaiser.slayers.world.Tracers;
import at.kaiser.slayers.world.WorldBeams;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/** Zeichnet Lichtstrahlen (wie ein Beacon) für Minibosse, platzierte Beacons usw. und Tracer-Linien zu Minibossen. */
public final class WorldRender {
	private static final int BEAM_HEIGHT = 319;

	private WorldRender() {}

	public static void register() {
		LevelExtractionEvents.END_EXTRACTION.register(WorldRender::extract);
	}

	/** false, falls Gizmos hier nicht erlaubt sind – dann zeichnet {@link #tickTracers} pro Tick */
	private static boolean frameGizmos = true;

	private static void extract(LevelExtractionContext ctx) {
		if (frameGizmos) {
			try {
				drawTracers(Minecraft.getInstance(), Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), 0);
			} catch (IllegalStateException e) {
				frameGizmos = false; // kein Gizmo-Sammler aktiv → Fallback über den Tick
			} catch (Throwable ignored) {
				// Linien sind nur Deko
			}
		}
		extractBeams(ctx);
	}

	/** Fallback: Linien pro Tick in den Tick-Sammler von Minecraft schreiben (kurz sichtbar bis zum nächsten Tick). */
	public static void tickTracers(Minecraft mc) {
		if (frameGizmos || (Tracers.get().isEmpty() && Boxes.get().isEmpty())) return;
		try (var c = mc.collectPerTickGizmos()) {
			drawTracers(mc, 1.0f, 60);
		} catch (Throwable ignored) {
		}
	}

	private static void drawBoxes(Minecraft mc, float pt, int persistMs) {
		var boxes = Boxes.get();
		if (boxes.isEmpty() || mc.level == null) return;
		// pulsierend, damit es sofort ins Auge springt
		float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 140.0);
		for (Boxes.Box b : boxes) {
			AABB box;
			if (b.entityId() >= 0) {
				Entity e = mc.level.getEntity(b.entityId());
				if (e == null) continue;
				Vec3 p = e.getPosition(pt);
				double headY = p.y + (e instanceof net.minecraft.world.entity.LivingEntity le ? le.getEyeHeight() : e.getBbHeight());
				box = new AABB(p.x - 0.35, headY - 0.4, p.z - 0.35, p.x + 0.35, headY + 0.3, p.z + 0.35);
			} else {
				box = new AABB(b.x() - 0.02, b.y() - 0.02, b.z() - 0.02, b.x() + 1.02, b.y() + 1.02, b.z() + 1.02);
			}
			int fill = ((int) (0x50 + 0x50 * pulse) << 24) | (b.rgb() & 0xFFFFFF);
			var props = Gizmos.cuboid(box, GizmoStyle.strokeAndFill(0xFF000000 | b.rgb(), 3.0f, fill)).setAlwaysOnTop();
			if (persistMs > 0) props.persistForMillis(persistMs);
		}
	}

	private static void drawTracers(Minecraft mc, float pt, int persistMs) {
		drawBoxes(mc, pt, persistMs);
		var tracers = Tracers.get();
		if (tracers.isEmpty() || mc.level == null || mc.player == null) return;
		Vec3 eye = mc.player.getEyePosition(pt);
		Vec3 start = eye.add(mc.player.getViewVector(pt).scale(0.5));
		for (Tracers.Tracer t : tracers) {
			Entity e = mc.level.getEntity(t.entityId());
			if (e == null) continue;
			Vec3 end = e.getPosition(pt).add(0, e.getBbHeight() * 0.6, 0);
			var p = Gizmos.line(start, end, 0xFF000000 | t.rgb(), 2.0f).setAlwaysOnTop();
			if (persistMs > 0) p.persistForMillis(persistMs);
		}
	}

	private static void extractBeams(LevelExtractionContext ctx) {
		var beams = WorldBeams.get();
		if (beams.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		float anim = Math.floorMod(mc.level.getGameTime(), 40) + mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		for (WorldBeams.Beam b : beams) {
			try {
				BeaconRenderState st = new BeaconRenderState();
				st.blockPos = new BlockPos(b.x(), b.y(), b.z());
				((BlockEntityRenderStateAccessor) st).kaisersSlayers$setBlockState(Blocks.BEACON.defaultBlockState());
				st.blockEntityType = BlockEntityTypes.BEACON;
				st.lightCoords = LightCoordsUtil.FULL_BRIGHT;
				st.breakProgress = null;
				st.animationTime = anim;
				st.sections.add(new BeaconRenderState.Section(0xFF000000 | b.rgb(), BEAM_HEIGHT));
				double dx = mc.player.getX() - (b.x() + 0.5), dz = mc.player.getZ() - (b.z() + 0.5);
				st.beamRadiusScale = (float) Math.max(1.0, Math.sqrt(dx * dx + dz * dz) / 96.0);
				ctx.levelState().blockEntityRenderStates.add(st);
			} catch (Throwable t) {
				// Strahl ist nur Deko – nie das Spiel stören
			}
		}
	}
}
