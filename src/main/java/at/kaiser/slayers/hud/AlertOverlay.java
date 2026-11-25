package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gui.Ui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Große Einblendung in der Bildschirmmitte (z. B. "BOSS SPAWNED!") mit Ein-/Ausblenden. */
public final class AlertOverlay {
	private static String title;
	private static String subtitle;
	private static long start;
	private static long until;

	private AlertOverlay() {}

	public static void show(String titleText, String subtitleText, long durationMs) {
		title = titleText;
		subtitle = subtitleText;
		start = System.currentTimeMillis();
		until = start + durationMs;
	}

	public static void playSound(SoundEvent sound, float pitch) {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		long now = System.currentTimeMillis();
		if (title == null || now > until || GamblingAnimation.hidesHud()) return;
		Minecraft mc = Minecraft.getInstance();
		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();
		float scale = Math.max(1.5f, Math.min(5f, KaisersSlayers.config().alertScale));

		// Pop-in: kurz größer, dann normal; am Ende ausblenden
		float age = (now - start) / 1000f;
		float pop = age < 0.15f ? 1.25f - age / 0.15f * 0.25f : 1.0f;
		float fade = Math.min(1f, (until - now) / 300f);
		int alpha = (int) (255 * Math.max(0.1f, fade));

		int cy = sh / 3;
		int tw = Ui.width(mc.font, title);
		int stripH = (int) (mc.font.lineHeight * scale) + 18;
		g.fill(0, cy - stripH / 2 - 4, sw, cy + stripH / 2 + 8, Ui.argb(0x000000, (int) (0x70 * fade)));
		Ui.hGradient(g, sw / 2 - 160, cy - stripH / 2 - 4, sw / 2, cy - stripH / 2 - 3, 0x00E74C3C, Ui.argb(0xE74C3C, alpha));
		Ui.hGradient(g, sw / 2, cy - stripH / 2 - 4, sw / 2 + 160, cy - stripH / 2 - 3, Ui.argb(0xE74C3C, alpha), 0x00E74C3C);

		float s = Math.min(scale, sw * 0.85f / Math.max(1, tw)) * pop;
		Ui.text(g, mc.font, title, sw / 2f - tw * s / 2f, cy - mc.font.lineHeight * s / 2f - 2, s, Ui.argb(0xFFFFFF, alpha), true);
		if (subtitle != null && !subtitle.isEmpty()) {
			Ui.centered(g, mc.font, subtitle, sw / 2f, cy + mc.font.lineHeight * scale / 2f + 1, 1.0f, Ui.argb(0xFFFFFF, alpha), true);
		}
	}
}
