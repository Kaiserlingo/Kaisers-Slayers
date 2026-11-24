package at.kaiser.slayers.mixin;

import at.kaiser.slayers.world.Highlights;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lässt markierte Entities (Boss, Minibosse, Beacons, ...) leuchten. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
	private void kaisersSlayers$glow(Entity entity, CallbackInfoReturnable<Boolean> cir) {
		if (Highlights.color(entity.getId()) != -1) cir.setReturnValue(true);
	}
}
