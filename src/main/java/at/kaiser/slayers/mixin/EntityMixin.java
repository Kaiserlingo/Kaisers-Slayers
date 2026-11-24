package at.kaiser.slayers.mixin;

import at.kaiser.slayers.world.Highlights;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Setzt die Farbe des Glow-Umrisses. */
@Mixin(Entity.class)
public abstract class EntityMixin {
	@Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
	private void kaisersSlayers$glowColor(CallbackInfoReturnable<Integer> cir) {
		int color = Highlights.color(((Entity) (Object) this).getId());
		if (color != -1) cir.setReturnValue(color);
	}
}
