package at.kaiser.slayers.mixin;

import at.kaiser.slayers.render.BossNametag;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hängt den Phasen-Timer als Namensschild direkt an deinen Boss. */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void kaisersSlayers$bossNametag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		Component text = BossNametag.textFor(entity);
		if (text != null) {
			state.nameTag = text;
			state.nameTagAttachment = new Vec3(0, state.boundingBoxHeight * 0.55, 0);
		}
	}
}
