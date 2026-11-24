package at.kaiser.slayers.gui;

import at.kaiser.slayers.slayer.SlayerType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Zeichen-Helfer für das Kaiser's-Slayers-Design (Panels, Schalter, Balken, Icons). */
public final class Ui {
	// ---------- Farben (ARGB) ----------
	public static final int BG = 0xF0101016;
	public static final int PANEL = 0xF0181820;
	public static final int CARD = 0xFF20202A;
	public static final int CARD_HOVER = 0xFF2A2A36;
	public static final int BORDER = 0xFF33333F;
	public static final int TEXT = 0xFFF2F2F7;
	public static final int MUTED = 0xFF9A9AA8;
	public static final int DIM = 0xFF60606C;
	public static final int GREEN = 0xFF2ECC71;
	public static final int RED = 0xFFE74C3C;
	public static final int GOLD = 0xFFFFC234;

	private Ui() {}

	public static int argb(int rgb, int alpha) {
		return (alpha & 0xFF) << 24 | (rgb & 0xFFFFFF);
	}

	/** Farbe zwischen a und b mischen (t = 0..1) */
	public static int mix(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
		return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
	}

	/** Rechteck mit abgerundeten Ecken (Radius 2 px) */
	public static void round(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		if (x2 - x1 < 4 || y2 - y1 < 4) {
			g.fill(x1, y1, x2, y2, color);
			return;
		}
		g.fill(x1 + 2, y1, x2 - 2, y2, color);
		g.fill(x1, y1 + 2, x1 + 2, y2 - 2, color);
		g.fill(x2 - 2, y1 + 2, x2, y2 - 2, color);
		g.fill(x1 + 1, y1 + 1, x1 + 2, y1 + 2, color);
		g.fill(x2 - 2, y1 + 1, x2 - 1, y1 + 2, color);
		g.fill(x1 + 1, y2 - 2, x1 + 2, y2 - 1, color);
		g.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, color);
	}

	/** Panel mit weichem Schatten und feinem Rand */
	public static void panel(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color, int border) {
		g.fill(x1 - 2, y1 + 3, x2 + 2, y2 + 4, 0x30000000);
		g.fill(x1 - 1, y1 + 2, x2 + 1, y2 + 2, 0x40000000);
		round(g, x1, y1, x2, y2, color);
		// Rand als 1-px-Linie (nicht flächig, sonst schimmert er durch halbtransparente Panels)
		g.fill(x1 + 2, y1 - 1, x2 - 2, y1, border);
		g.fill(x1 + 2, y2, x2 - 2, y2 + 1, border);
		g.fill(x1 - 1, y1 + 2, x1, y2 - 2, border);
		g.fill(x2, y1 + 2, x2 + 1, y2 - 2, border);
		g.fill(x1, y1, x1 + 2, y1 + 1, border);
		g.fill(x1, y1, x1 + 1, y1 + 2, border);
		g.fill(x2 - 2, y1, x2, y1 + 1, border);
		g.fill(x2 - 1, y1, x2, y1 + 2, border);
		g.fill(x1, y2 - 1, x1 + 2, y2, border);
		g.fill(x1, y2 - 2, x1 + 1, y2, border);
		g.fill(x2 - 2, y2 - 1, x2, y2, border);
		g.fill(x2 - 1, y2 - 2, x2, y2, border);
	}

	/** Horizontaler Farbverlauf (für Balken und Header) */
	public static void hGradient(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int left, int right) {
		int steps = Math.max(1, Math.min(48, (x2 - x1) / 3));
		for (int i = 0; i < steps; i++) {
			int a = x1 + (x2 - x1) * i / steps, b = x1 + (x2 - x1) * (i + 1) / steps;
			g.fill(a, y1, b, y2, mix(left, right, i / (float) Math.max(1, steps - 1)));
		}
	}

	/** Fortschrittsbalken 0..1 mit Verlauf */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double pct, int from, int to) {
		round(g, x, y, x + w, y + h, 0xFF0C0C10);
		int fw = (int) Math.round((w - 2) * Math.max(0, Math.min(1, pct)));
		if (fw > 0) hGradient(g, x + 1, y + 1, x + 1 + fw, y + h - 1, from, to);
		g.fill(x + 1, y + 1, x + 1 + fw, y + 2, 0x40FFFFFF);
	}

	/** Schalter (Toggle). anim = 0 (aus) .. 1 (an), für weiche Animation */
	public static void toggle(GuiGraphicsExtractor g, int x, int y, float anim, int accent) {
		int w = 22, h = 11;
		int track = mix(0xFF3A3A46, accent | 0xFF000000, anim);
		round(g, x, y, x + w, y + h, track);
		int knobX = (int) (x + 1 + (w - h) * anim);
		round(g, knobX, y + 1, knobX + h - 2, y + h - 1, 0xFFFFFFFF);
	}

	/** Text mit Skalierung */
	public static void text(GuiGraphicsExtractor g, Font font, String s, float x, float y, float scale, int color, boolean shadow) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.text(font, s, 0, 0, color, shadow);
		g.pose().popMatrix();
	}

	public static void centered(GuiGraphicsExtractor g, Font font, String s, float cx, float y, float scale, int color, boolean shadow) {
		text(g, font, s, cx - font.width(s) * scale / 2f, y, scale, color, shadow);
	}

	public static void rightText(GuiGraphicsExtractor g, Font font, String s, int rightX, int y, int color) {
		g.text(font, s, rightX - font.width(s), y, color, true);
	}

	/** Item-Icon mit Skalierung */
	public static void icon(GuiGraphicsExtractor g, ItemStack stack, float x, float y, float scale) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		g.item(stack, 0, 0);
		g.pose().popMatrix();
	}

	public static boolean inside(double mx, double my, int x1, int y1, int x2, int y2) {
		return mx >= x1 && mx < x2 && my >= y1 && my < y2;
	}

	/** Icon-Item für jeden Slayer */
	public static ItemStack slayerIcon(SlayerType t) {
		if (t == null) return new ItemStack(Items.DIAMOND_SWORD);
		Item i = switch (t) {
			case ZOMBIE -> Items.ROTTEN_FLESH;
			case SPIDER -> Items.SPIDER_EYE;
			case WOLF -> Items.BONE;
			case ENDERMAN -> Items.ENDER_PEARL;
			case BLAZE -> Items.BLAZE_ROD;
			case VAMPIRE -> Items.REDSTONE;
		};
		return new ItemStack(i);
	}

	/** Akzentfarbe je Slayer (ARGB) */
	public static int slayerAccent(SlayerType t) {
		return t == null ? RED : 0xFF000000 | t.defaultGlow;
	}
}
