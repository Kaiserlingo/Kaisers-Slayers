package at.kaiser.slayers.gui;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.hud.ProfitHud;
import at.kaiser.slayers.hud.SlayerHud;
import at.kaiser.slayers.slayer.SlayerType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** HUD-Editor: HUDs mit der Maus ziehen, mit dem Mausrad skalieren, Rechtsklick blendet ein/aus. */
public class HudEditorScreen extends Screen {
	private final Screen parent;
	private ModConfig.HudPos dragging;
	private double grabX, grabY;
	private final int[] slayerBox = new int[4];
	private final int[] profitBox = new int[4];
	private final int[] carryBox = new int[4];

	public HudEditorScreen(Screen parent) {
		super(Component.literal("HUD Editor"));
		this.parent = parent;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fill(0, 0, width, height, 0x40000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		ModConfig cfg = KaisersSlayers.config();
		long now = System.currentTimeMillis();

		// Raster
		for (int x = 0; x < width; x += 20) g.fill(x, 0, x + 1, height, 0x10FFFFFF);
		for (int y = 0; y < height; y += 20) g.fill(0, y, width, y + 1, 0x10FFFFFF);

		// Slayer-HUD
		drawElement(g, cfg.slayerHud, slayerBox, mx, my, SlayerHud.lastWidth, () -> SlayerHud.draw(g, font, KaisersSlayers.tracker(), cfg, now), "Slayer HUD");
		// Profit-HUD
		SlayerType type = KaisersSlayers.tracker().type() != null ? KaisersSlayers.tracker().type() : SlayerType.ZOMBIE;
		drawElement(g, cfg.profitHud, profitBox, mx, my, ProfitHud.lastWidth,
				() -> ProfitHud.draw(g, font, type, ProfitHud.bucket(type, cfg), cfg), "Profit HUD");

		// Carry-HUD (ohne Kunden mit Beispieldaten, damit man es platzieren kann)
		var clients = KaisersSlayers.carries().isEmpty() ? at.kaiser.slayers.hud.CarryHud.sample() : KaisersSlayers.carries().clients();
		drawElement(g, cfg.carryHud, carryBox, mx, my, at.kaiser.slayers.hud.CarryHud.lastWidth,
				() -> at.kaiser.slayers.hud.CarryHud.draw(g, font, cfg, clients), "Carry HUD");

		// Hilfe
		int acc = 0xFF000000 | cfg.accentColor;
		String help = "§fDrag §7= move   §fScroll §7= size   §fRight-click §7= on/off   §fR §7= reset   §fESC §7= done";
		float hs = Math.min(1f, (width - 30) / (float) font.width(help));
		int hw = (int) (font.width(help) * hs);
		Ui.panel(g, width / 2 - hw / 2 - 8, height - 24, width / 2 + hw / 2 + 8, height - 8, 0xE0111117, Ui.argb(acc, 0x80));
		Ui.text(g, font, help, width / 2f - hw / 2f, height - 19, hs, 0xFFFFFFFF, false);
		Ui.centered(g, font, "§l§nHUD EDITOR", width / 2f, 8, 1.5f, acc, true);
	}

	private void drawElement(GuiGraphicsExtractor g, ModConfig.HudPos pos, int[] box, int mx, int my, int w, java.util.function.IntSupplier draw, String name) {
		int x = Math.round(pos.x * width), y = Math.round(pos.y * height);
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(pos.scale, pos.scale);
		int h = draw.getAsInt();
		g.pose().popMatrix();
		int x2 = x + Math.round(w * pos.scale), y2 = y + Math.round(h * pos.scale);
		box[0] = x;
		box[1] = y;
		box[2] = x2;
		box[3] = y2;
		boolean hover = Ui.inside(mx, my, x, y, x2, y2) || dragging == pos;
		int c = pos.enabled ? (hover ? 0xFFFFFFFF : 0xA0FFFFFF) : 0xFFE74C3C;
		g.outline(x - 2, y - 2, x2 - x + 4, y2 - y + 4, c);
		String label = name + (pos.enabled ? "" : " §c(off)") + " §7" + Math.round(pos.scale * 100) + "%";
		Ui.round(g, x - 2, y - 13, x - 2 + font.width(label) + 6, y - 2, 0xC0000000);
		g.text(font, label, x + 1, y - 11, 0xFFFFFFFF, false);
		if (!pos.enabled) g.fill(x, y, x2, y2, 0x80300000);
	}

	private ModConfig.HudPos at(double mx, double my) {
		ModConfig cfg = KaisersSlayers.config();
		if (Ui.inside(mx, my, carryBox[0], carryBox[1], carryBox[2], carryBox[3])) return cfg.carryHud;
		if (Ui.inside(mx, my, profitBox[0], profitBox[1], profitBox[2], profitBox[3])) return cfg.profitHud;
		if (Ui.inside(mx, my, slayerBox[0], slayerBox[1], slayerBox[2], slayerBox[3])) return cfg.slayerHud;
		return null;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		ModConfig.HudPos p = at(e.x(), e.y());
		if (p == null) return true;
		if (e.button() == 1) {
			p.enabled = !p.enabled;
			KaisersSlayers.config().save();
			return true;
		}
		dragging = p;
		grabX = e.x() - p.x * width;
		grabY = e.y() - p.y * height;
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging == null) return false;
		float nx = (float) ((e.x() - grabX) / width), ny = (float) ((e.y() - grabY) / height);
		// an Rändern und Mitte einrasten
		nx = snap(nx, 0.005f, 0.5f);
		ny = snap(ny, 0.005f, 0.5f);
		dragging.x = Math.max(0, Math.min(0.98f, nx));
		dragging.y = Math.max(0, Math.min(0.98f, ny));
		return true;
	}

	private static float snap(float v, float... points) {
		for (float p : points) if (Math.abs(v - p) < 0.008f) return p;
		return v;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging != null) {
			dragging = null;
			KaisersSlayers.config().save();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double h, double v) {
		ModConfig.HudPos p = at(x, y);
		if (p != null) {
			p.scale = Math.max(0.5f, Math.min(2.5f, Math.round((p.scale + (v > 0 ? 0.05f : -0.05f)) * 100) / 100f));
			KaisersSlayers.config().save();
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent e) {
		if (e.key() == 82) { // R
			ModConfig cfg = KaisersSlayers.config();
			cfg.slayerHud = new ModConfig.HudPos(0.005f, 0.06f);
			cfg.profitHud = new ModConfig.HudPos(0.005f, 0.55f);
			cfg.carryHud = new ModConfig.HudPos(0.70f, 0.30f);
			cfg.save();
			return true;
		}
		return super.keyPressed(e);
	}

	@Override
	public void onClose() {
		KaisersSlayers.config().save();
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
