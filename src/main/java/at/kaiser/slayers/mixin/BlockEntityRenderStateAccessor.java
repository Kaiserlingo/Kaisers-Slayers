package at.kaiser.slayers.mixin;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Erlaubt es, den (privaten) Block-Zustand eines Render-States zu setzen – nötig für eigene Beacon-Strahlen. */
@Mixin(BlockEntityRenderState.class)
public interface BlockEntityRenderStateAccessor {
	@Accessor("blockState")
	void kaisersSlayers$setBlockState(BlockState state);
}
