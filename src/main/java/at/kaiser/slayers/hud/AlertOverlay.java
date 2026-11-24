package at.kaiser.slayers.hud;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Große Einblendung in der Bildschirmmitte (z. B. "BOSS SPAWNED!"). */
public final class AlertOverlay {
	private static String title;
	private static String subtitle;
	private static long until;

	private AlertOverlay() {}

	public static void show(String titleText, String subtitleText, long durationMs) {
		title = titleText;
		subtitle = subtitleText;
		until = System.currentTimeMillis() + durationMs;
	}

	public static void playSound(SoundEvent sound, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		if (title == null || System.currentTimeMillis() > until) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;

		int screenW = mc.getWindow().getGuiScaledWidth();
		int screenH = mc.getWindow().getGuiScaledHeight();
		float scale = 3.0f;
		int titleW = mc.font.width(title);

		graphics.pose().pushMatrix();
		graphics.pose().translate(screenW / 2f, screenH / 3f);
		graphics.pose().scale(scale, scale);
		graphics.text(mc.font, title, -titleW / 2, -mc.font.lineHeight, 0xFFFFFFFF, true);
		graphics.pose().popMatrix();

		if (subtitle != null && !subtitle.isEmpty()) {
			int subW = mc.font.width(subtitle);
			graphics.text(mc.font, subtitle, screenW / 2 - subW / 2, (int) (screenH / 3f) + 6, 0xFFFFFFFF, true);
		}
	}
}
