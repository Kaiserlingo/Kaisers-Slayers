package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import net.minecraft.sounds.SoundEvent;

/** Kurzer Helfer: Alert anzeigen und (wenn erlaubt) Sound spielen. */
public final class Feedback {
	private Feedback() {}

	public static void alert(String title, String subtitle, long ms, SoundEvent sound, float pitch) {
		AlertOverlay.show(title, subtitle, ms);
		sound(sound, pitch);
	}

	public static void sound(SoundEvent sound, float pitch) {
		if (sound != null && KaisersSlayers.config().sounds) AlertOverlay.playSound(sound, pitch);
	}
}
