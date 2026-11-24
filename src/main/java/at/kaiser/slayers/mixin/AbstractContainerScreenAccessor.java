package at.kaiser.slayers.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Position des Inventar-Fensters (für das Profit-Panel daneben). */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int kaisersSlayers$getLeftPos();

	@Accessor("topPos")
	int kaisersSlayers$getTopPos();

	@Accessor("imageWidth")
	int kaisersSlayers$getImageWidth();
}
