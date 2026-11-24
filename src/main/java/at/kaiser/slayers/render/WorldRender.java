package at.kaiser.slayers.render;

import at.kaiser.slayers.mixin.BlockEntityRenderStateAccessor;
import at.kaiser.slayers.world.WorldBeams;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

/** Zeichnet Lichtstrahlen (wie ein Beacon) für Minibosse, platzierte Beacons usw. */
public final class WorldRender {
	private static final int BEAM_HEIGHT = 319;

	private WorldRender() {}

	public static void register() {
		LevelExtractionEvents.END_EXTRACTION.register(WorldRender::extract);
	}

	private static void extract(LevelExtractionContext ctx) {
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
