package at.kaiser.slayers.hud;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.carry.CarryManager;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gui.Ui;
import at.kaiser.slayers.util.TextUtil;
import at.kaiser.slayers.world.CarryScanner;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carry-HUD: alle Kunden mit Spielerkopf, Fortschritt (3/10), Balken, Boss-Status und offenem Betrag.
 * Eigenes Design (Akzent-Streifen links, Kopf-Spalte), passt aber zu Profit- und Slayer-HUD.
 */
public final class CarryHud {
	public static final int WIDTH = 150;
	private static final int PAD = 5;
	/** Höhe beim letzten Zeichnen (zum Im-Bild-Halten) */
	public static int lastHeight = 0;
	public static int lastWidth = WIDTH;
	private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();

	private CarryHud() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		ModConfig cfg = KaisersSlayers.config();
		if (!cfg.carryEnabled || !cfg.carryHud.enabled || mc.player == null || GamblingAnimation.hidesHud()) return;
		if (!KaisersSlayers.onSkyBlock() && !KaisersSlayers.forceHud()) return;
		List<CarryManager.Client> clients = clients();
		if (clients.isEmpty()) return;
		int x = Math.round(cfg.carryHud.x * mc.getWindow().getGuiScaledWidth());
		int y = Math.round(cfg.carryHud.y * mc.getWindow().getGuiScaledHeight());
		// immer ganz im Bild halten (z. B. nach Auflösungs-Wechsel oder wenn die Karte breiter wird)
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		x = Math.max(0, Math.min(x, sw - Math.round(lastWidth * cfg.carryHud.scale) - 1));
		y = Math.max(0, Math.min(y, sh - Math.round(lastHeight * cfg.carryHud.scale) - 1));
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(cfg.carryHud.scale, cfg.carryHud.scale);
		lastHeight = draw(g, mc.font, cfg, clients);
		g.pose().popMatrix();
	}

	/** Echte Kunden – im Test-Modus ohne Kunden Beispieldaten (werden nicht gespeichert) */
	public static List<CarryManager.Client> clients() {
		List<CarryManager.Client> real = KaisersSlayers.carries().clients();
		if (!real.isEmpty() || !KaisersSlayers.isDemo()) return real;
		return sample();
	}

	public static List<CarryManager.Client> sample() {
		List<CarryManager.Client> out = new ArrayList<>();
		out.add(sampleClient("Technoblade", 10, 3, 2_500_000, 5_000_000, "Voidgloom Seraph IV"));
		out.add(sampleClient("Notch", 5, 5, 1_000_000, 5_000_000, "Revenant Horror V"));
		out.add(sampleClient("jeb_", 20, 12, 0, 0, "Inferno Demonlord III"));
		return out;
	}

	private static CarryManager.Client sampleClient(String n, int total, int done, long price, long paid, String boss) {
		CarryManager.Client c = new CarryManager.Client();
		c.name = n;
		c.total = total;
		c.done = done;
		c.price = price;
		c.paid = paid;
		c.lastBoss = boss;
		return c;
	}

	/** Zeichnet die Karte bei (0,0) und gibt die Höhe zurück. */
	public static int draw(GuiGraphicsExtractor g, Font font, ModConfig cfg, List<CarryManager.Client> clients) {
		int accent = 0xFF000000 | cfg.carryAccent;
		boolean compact = cfg.carryCompact;
		boolean heads = cfg.carryShowHeads;
		int rowH = compact ? 11 : 22;
		int face = compact ? 8 : 16;
		Map<String, CarryScanner.LiveBoss> live = KaisersSlayers.carryScanner().live();

		// Breite: längster Name + Fortschritt
		int nameW = 0, progW = 0;
		for (CarryManager.Client c : clients) {
			nameW = Math.max(nameW, font.width("§l" + c.name) + (live.containsKey(CarryManager.key(c.name)) ? 18 : 0));
			progW = Math.max(progW, font.width("§l" + c.done + "/" + c.total));
		}
		int textX = PAD + 3 + (heads ? face + 4 : 0);
		int w = Math.max(WIDTH, textX + nameW + 10 + progW + PAD);
		w = Math.min(230, w);
		lastWidth = w;

		int left = 0, done = 0;
		long due = 0;
		for (CarryManager.Client c : clients) {
			left += c.left();
			due += c.due();
			if (c.finished()) done++;
		}
		boolean footer = !compact;
		int h = 15 + clients.size() * rowH + (footer ? 13 : 2);

		if (cfg.hudBackground) {
			Ui.panel(g, 0, 0, w, h, 0xD8101015, Ui.argb(accent, 0x70));
		}
		g.fill(0, 2, 2, h - 2, accent); // Akzent-Streifen links

		// Titel
		g.text(font, "§l⚔ CARRIES", PAD + 3, 4, accent, true);
		String sub = left > 0 ? "§f" + left + " §7left" : "§a§lALL DONE";
		Ui.rightText(g, font, sub, w - PAD, 4, 0xFFFFFFFF);

		int y = 15;
		long now = System.currentTimeMillis();
		for (CarryManager.Client c : clients) {
			CarryScanner.LiveBoss boss = live.get(CarryManager.key(c.name));
			if (boss != null) {
				// Boss des Kunden lebt gerade: Zeile leicht hervorheben
				int pulse = (int) (0x18 + 0x10 * Math.sin(now / 250.0));
				g.fill(3, y - 1, w - 2, y + rowH - 2, Ui.argb(accent, pulse));
			}
			if (heads) face(g, c.name, PAD + 3, y + (compact ? 0 : 1), face);
			String nameCol = c.finished() ? "§a" : "§f";
			g.text(font, nameCol + "§l" + c.name, textX, y + (compact ? 0 : 1), 0xFFFFFFFF, true);
			if (boss != null) {
				int nx = textX + font.width("§l" + c.name) + 4;
				Ui.text(g, font, "§c●", nx, y + (compact ? 0 : 1), 1f, 0xFFFFFFFF, true);
			}
			String prog = c.finished() ? "§a§l✔ " + c.done + "/" + c.total : "§e§l" + c.done + "§7/§f" + c.total;
			Ui.rightText(g, font, prog, w - PAD, y + (compact ? 0 : 1), 0xFFFFFFFF);

			if (!compact) {
				// zweite Zeile: Balken oder Boss-Status + Bezahlung
				int ly = y + 11;
				int barX1 = textX, barX2 = w - PAD;
				String info = info(c, boss, cfg);
				if (cfg.carryShowBars && c.total > 0) {
					float pct = Math.min(1f, c.done / (float) c.total);
					g.fill(barX1, ly + 7, barX2, ly + 9, 0x40FFFFFF);
					g.fill(barX1, ly + 7, barX1 + Math.round((barX2 - barX1) * pct), ly + 9, c.finished() ? 0xFF55FF55 : accent);
				}
				if (!info.isEmpty()) Ui.text(g, font, info, textX, ly, 0.7f, 0xFFFFFFFF, false);
			}
			y += rowH;
		}

		if (footer) {
			g.fill(PAD + 3, y + 1, w - PAD, y + 2, 0x30FFFFFF);
			String f = "§7Done §a" + done + "§7/§f" + clients.size()
					+ (cfg.carryShowPayment && due > 0 ? " §8· §7Due §6" + TextUtil.formatCoins(due) : "")
					+ " §8· §7Session §d" + KaisersSlayers.carries().sessionCarries();
			Ui.text(g, font, f, PAD + 3, y + 4, 0.75f, 0xFFFFFFFF, false);
		}
		return h;
	}

	/** Kleine Info-Zeile unter dem Namen */
	private static String info(CarryManager.Client c, CarryScanner.LiveBoss boss, ModConfig cfg) {
		List<String> parts = new ArrayList<>();
		if (boss != null && cfg.carryShowBossInfo) {
			String hp = boss.hpPercent() >= 0 ? String.format(java.util.Locale.ROOT, "%.0f%%", boss.hpPercent() * 100) : "?";
			parts.add("§c" + (boss.bossName().isEmpty() ? "Boss" : boss.bossName()) + " §f" + hp
					+ (boss.timeLeftSec() >= 0 ? " §7" + TextUtil.formatDuration(boss.timeLeftSec() * 1000L) : ""));
		} else if (!c.lastBoss.isEmpty()) {
			parts.add("§8" + c.lastBoss);
		}
		if (cfg.carryShowPayment && c.price > 0) {
			long due = c.due();
			parts.add(due > 0 ? "§6" + TextUtil.formatCoins(due) + " due" : "§a✔ paid");
		}
		return String.join(" §8· ", parts);
	}

	/** Spielerkopf: aus der Tab-Liste (sofort) oder über das Mojang-Profil (lädt im Hintergrund) */
	public static void face(GuiGraphicsExtractor g, String name, int x, int y, int size) {
		try {
			ClientPacketListener conn = Minecraft.getInstance().getConnection();
			PlayerInfo info = conn != null ? conn.getPlayerInfo(name) : null;
			if (info != null) {
				PlayerFaceExtractor.extractRenderState(g, info.getSkin(), x, y, size);
				return;
			}
			ResolvableProfile p = PROFILES.computeIfAbsent(CarryManager.key(name), k -> ResolvableProfile.createUnresolved(name));
			PlayerFaceExtractor.extractRenderState(g, p, x, y, size);
		} catch (Throwable t) {
			// Notlösung: farbiges Quadrat mit Anfangsbuchstaben
			int col = 0xFF000000 | (name.hashCode() & 0x7F7F7F) | 0x404040;
			g.fill(x, y, x + size, y + size, col);
			Font font = Minecraft.getInstance().font;
			String s = name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
			g.text(font, s, x + size / 2 - font.width(s) / 2, y + size / 2 - 4, 0xFFFFFFFF, true);
		}
	}
}
