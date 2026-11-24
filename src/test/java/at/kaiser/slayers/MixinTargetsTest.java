package at.kaiser.slayers;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prüft, ob die Methoden, in die sich die Mixins einhängen, in dieser Minecraft-Version existieren.
 * Schlägt fehl, wenn Mojang sie umbenennt – dann muss der Mixin angepasst werden.
 */
class MixinTargetsTest {
	private static boolean hasMethod(String className, String method) throws Exception {
		Class<?> c = Class.forName(className, false, MixinTargetsTest.class.getClassLoader());
		return Arrays.stream(c.getDeclaredMethods()).map(Method::getName).anyMatch(method::equals);
	}

	@Test
	void glowTargetsExist() throws Exception {
		assertTrue(hasMethod("net.minecraft.client.Minecraft", "shouldEntityAppearGlowing"),
				"Minecraft#shouldEntityAppearGlowing fehlt – MinecraftMixin anpassen");
		assertTrue(hasMethod("net.minecraft.world.entity.Entity", "getTeamColor"),
				"Entity#getTeamColor fehlt – EntityMixin anpassen");
	}
}
