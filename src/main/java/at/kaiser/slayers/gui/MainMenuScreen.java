package at.kaiser.slayers.gui;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.slayer.LifetimeStats;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Hauptmenü von /ks: links allgemeine Bereiche, rechts ein Knopf pro Slayer. */
public class MainMenuScreen extends Screen {
	private static final int W = 170, H = 20, GAP = 4;

	public MainMenuScreen() {
		super(Component.literal("§c§lKaiser's Slayers"));
	}

	@Override
	protected void init() {
		int top = 56;
		int leftX = this.width / 2 - W - 10;
		int rightX = this.width / 2 + 10;

		// ---- Links: Allgemein ----
		addButton(leftX, top, "§fTracker & Profit", "Profit-Tracker, Drops, Level-Prognose, Preise",
				() -> open("§fTracker & Profit", "Allgemein", Menus::tracker));
		addButton(leftX, top + (H + GAP), "§fAlerts", "Boss-Spawn, Boss spawnt gleich, Minibosse + Waypoint, ...",
				() -> open("§fAlerts", "Allgemein", Menus::alerts));
		addButton(leftX, top + 2 * (H + GAP), "§fHUD", "Position, Größe, Boss-Panel, Waypoint-Pfeil",
				() -> open("§fHUD", "Allgemein", Menus::hud));
		addButton(leftX, top + 3 * (H + GAP), "§6Gambling-Animation", "Animation beim Boss-Kill einstellen + Vorschau",
				() -> open("§6Gambling-Animation", "Beim Kill drehen lassen", Menus::gambling));
		addButton(leftX, top + 4 * (H + GAP), "§7Session zurücksetzen", "Setzt die Session-Statistik auf 0",
				() -> {
					KaisersSlayers.tracker().resetSession();
					KaisersSlayers.chat("§aSession-Statistik zurückgesetzt.");
				});

		// ---- Rechts: Slayer ----
		SlayerType[] types = SlayerType.values();
		for (int i = 0; i < types.length; i++) {
			SlayerType t = types[i];
			LifetimeStats.Entry e = KaisersSlayers.tracker().lifetime().get(t);
			String tip = t.bossName + (e.level >= 0 ? " – " + t.levelName + " LVL " + e.level : "")
					+ " – " + e.kills + " Kills";
			addButton(rightX, top + i * (H + GAP), t.color + t.menuName(), tip,
					() -> open(t.color + "§l" + t.bossName, t.levelName + " Slayer – eigene Funktionen", () -> Menus.slayer(t)));
		}

		this.addRenderableWidget(Button.builder(Component.literal("Fertig"), b -> onClose())
				.bounds(this.width / 2 - 100, this.height - 28, 200, H).build());
	}

	private void addButton(int x, int y, String text, String tooltip, Runnable action) {
		Button.Builder b = Button.builder(Component.literal(text), btn -> action.run()).bounds(x, y, W, H);
		if (tooltip != null) b.tooltip(Tooltip.create(Component.literal(tooltip)));
		this.addRenderableWidget(b.build());
	}

	private void open(String title, String subtitle, java.util.function.Supplier<List<Option>> options) {
		this.minecraft.gui.setScreen(new OptionsScreen(this, title, subtitle, options));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		String t = this.title.getString();
		graphics.text(this.font, t, this.width / 2 - this.font.width(t) / 2, 14, 0xFFFFFFFF, true);
		String sub = "§7Slayer-Begleiter für Hypixel SkyBlock";
		graphics.text(this.font, sub, this.width / 2 - this.font.width(sub) / 2, 26, 0xFFFFFFFF, true);
		graphics.text(this.font, "§c§lAllgemein", this.width / 2 - W - 10, 44, 0xFFFFFFFF, true);
		graphics.text(this.font, "§c§lSlayer", this.width / 2 + 10, 44, 0xFFFFFFFF, true);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ======================================================================= Inhalte der Untermenüs

	static final class Menus {
		private Menus() {}

		private static ModConfig c() {
			return KaisersSlayers.config();
		}

		static List<Option> tracker() {
			List<Option> o = new ArrayList<>();
			o.add(Option.toggle("Profit-Tracker", "Profit & Profit/h mit Live-Bazaar-Preisen im HUD",
					() -> c().showProfit, v -> c().showProfit = v));
			o.add(Option.toggle("Drop-Tracker", "Liste der Drops dieser Session im HUD",
					() -> c().showDrops, v -> c().showDrops = v));
			o.add(Option.toggle("Level-Prognose", "XP bis zum nächsten Slayer-Level, Bosse + Zeit",
					() -> c().showLevelEta, v -> c().showLevelEta = v));
			o.add(Option.toggle("Kill-Zeit im Chat", "Nach jedem Kill: Kill-Zeit, Spawn-Zeit, Rekord",
					() -> c().killMessage, v -> c().killMessage = v));
			o.add(Option.cycle("Preis-Modus", "Instant-Sell = was du sofort bekommst, Sell-Offer = mit Angebot",
					List.of(true, false), v -> v ? "Instant-Sell" : "Sell-Offer",
					() -> c().instantSellPrices, v -> c().instantSellPrices = v));
			o.add(Option.action("§ePreise neu laden", "Holt sofort neue Bazaar-Preise",
					() -> KaisersSlayers.prices().refresh()));
			return o;
		}

		static List<Option> alerts() {
			List<Option> o = new ArrayList<>();
			o.add(Option.toggle("Boss-Spawn-Alert", "Große Einblendung + Sound, wenn dein Boss spawnt",
					() -> c().spawnAlert, v -> c().spawnAlert = v));
			o.add(Option.toggle("Boss spawnt gleich", "Vorwarnung, kurz bevor der Boss spawnt",
					() -> c().soonSpawnAlert, v -> c().soonSpawnAlert = v));
			o.add(Option.cycle("Vorwarnung ab", "Ab welchem Quest-Fortschritt die Vorwarnung kommt",
					List.of(75, 80, 85, 90, 95), v -> v + "%", () -> c().soonSpawnPercent, v -> c().soonSpawnPercent = v));
			o.add(Option.toggle("Miniboss-Alert", "Einblendung + Sound, wenn ein Miniboss neben dir spawnt",
					() -> c().minibossAlert, v -> c().minibossAlert = v));
			o.add(Option.toggle("Miniboss-Waypoint", "Kurzzeitiger Pfeil + Entfernung zum Miniboss",
					() -> c().minibossWaypoint, v -> c().minibossWaypoint = v));
			o.add(Option.cycle("Waypoint-Dauer", "Wie lange der Miniboss-Pfeil sichtbar bleibt",
					List.of(5, 10, 15, 20, 30, 60), v -> v + "s", () -> c().waypointSeconds, v -> c().waypointSeconds = v));
			o.add(Option.toggle("Boss-Zeitwarnung", "Warnung, wenn das Boss-Zeitlimit bald abläuft",
					() -> c().bossTimeWarning, v -> c().bossTimeWarning = v));
			o.add(Option.cycle("Warnung bei", "Sekunden vor Ablauf des Zeitlimits",
					List.of(10, 20, 30, 45, 60), v -> v + "s Rest", () -> c().bossTimeWarnSec, v -> c().bossTimeWarnSec = v));
			o.add(Option.toggle("Quest-Failed-Alert", "Einblendung, wenn der Quest fehlschlägt",
					() -> c().failAlert, v -> c().failAlert = v));
			o.add(Option.toggle("Drop-Alert", "Einblendung bei CRAZY RARE / INSANE / RNGesus Drops",
					() -> c().dropAlert, v -> c().dropAlert = v));
			o.add(Option.toggle("Sounds", "Alle Sounds der Mod",
					() -> c().sounds, v -> c().sounds = v));
			return o;
		}

		static List<Option> hud() {
			List<Option> o = new ArrayList<>();
			o.add(Option.toggle("Slayer-HUD", "Das Info-Fenster links", () -> c().hudEnabled, v -> c().hudEnabled = v));
			o.add(Option.toggle("Hintergrund", "Dunkler Hintergrund hinter dem HUD", () -> c().hudBackground, v -> c().hudBackground = v));
			o.add(Option.toggle("Nur bei aktivem Quest", "HUD nur zeigen, wenn ein Slayer-Quest läuft",
					() -> c().hudOnlyWhenActive, v -> c().hudOnlyWhenActive = v));
			o.add(Option.cycle("HUD-Größe", "Skalierung des HUDs", List.of(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f),
					v -> v + "x", () -> nearest(c().hudScale), v -> c().hudScale = v));
			o.add(Option.action("HUD ◀ links", "10 Pixel nach links", () -> c().hudX = Math.max(0, c().hudX - 10)));
			o.add(Option.action("HUD ▶ rechts", "10 Pixel nach rechts", () -> c().hudX += 10));
			o.add(Option.action("HUD ▲ hoch", "10 Pixel nach oben", () -> c().hudY = Math.max(0, c().hudY - 10)));
			o.add(Option.action("HUD ▼ runter", "10 Pixel nach unten", () -> c().hudY += 10));
			o.add(Option.action("Position zurücksetzen", "HUD wieder oben links", () -> {
				c().hudX = 6;
				c().hudY = 40;
			}));
			o.add(Option.toggle("Waypoint-Pfeil", "Pfeil + Entfernung zu Miniboss / Beacon oben in der Mitte",
					() -> c().waypointArrow, v -> c().waypointArrow = v));
			o.add(Option.cycle("Boss-Panel Abstand", "Abstand des Boss-Panels unter dem Fadenkreuz",
					List.of(12, 24, 36, 48, 64), v -> v + "px", () -> c().bossPanelOffsetY, v -> c().bossPanelOffsetY = v));
			return o;
		}

		static List<Option> gambling() {
			List<Option> o = new ArrayList<>();
			o.add(Option.toggle("Gambling-Animation", "Hauptschalter (pro Slayer zusätzlich im Slayer-Menü)",
					() -> c().gamblingEnabled, v -> c().gamblingEnabled = v));
			o.add(Option.cycle("Stil", "Walze = Kisten-Öffnung, Slots = Slot-Machine mit 3 Walzen",
					List.of("REEL", "SLOTS"), v -> v.equals("REEL") ? "Walze" : "Slots",
					() -> c().gamblingStyle, v -> c().gamblingStyle = v));
			o.add(Option.cycle("Dauer", "Wie lange gedreht wird", List.of(2500, 4000, 6000, 8000),
					v -> (v / 1000.0) + "s", () -> c().gamblingDurationMs, v -> c().gamblingDurationMs = v));
			o.add(Option.cycle("Auslöser", "Bei jedem Kill oder nur bei RARE DROP und besser",
					List.of("ALWAYS", "RARE_ONLY"), v -> v.equals("ALWAYS") ? "Jeder Kill" : "Nur Rare Drops",
					() -> c().gamblingTrigger, v -> c().gamblingTrigger = v));
			o.add(Option.cycle("Jackpot ab", "Ab diesem Drop-Wert gibt es den Jackpot-Effekt",
					List.of(1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L, 100_000_000L), v -> TextUtil.formatCoins(v),
					() -> c().gamblingJackpotValue, v -> c().gamblingJackpotValue = v));
			o.add(Option.cycle("Größe", "Skalierung der Animation", List.of(0.75f, 1.0f, 1.25f, 1.5f),
					v -> v + "x", () -> nearestG(c().gamblingScale), v -> c().gamblingScale = v));
			o.add(Option.toggle("Sounds", "Tick-Geräusche und Gewinn-Sound", () -> c().gamblingSounds, v -> c().gamblingSounds = v));
			o.add(Option.toggle("Wert anzeigen", "Coin-Wert des Drops beim Ergebnis",
					() -> c().gamblingShowValue, v -> c().gamblingShowValue = v));
			o.add(Option.toggle("Eigene Drops in Walze", "Items, die du schon gedroppt hast, erscheinen auch in der Walze",
					() -> c().gamblingUseOwnDrops, v -> c().gamblingUseOwnDrops = v));
			SlayerType current = KaisersSlayers.tracker().type() != null ? KaisersSlayers.tracker().type() : SlayerType.ZOMBIE;
			o.add(Option.action("§a▶ Vorschau", "Schließt das Menü und spielt eine Test-Animation ("
					+ current.bossName + ")", () -> previewAndClose(current)));
			return o;
		}

		static List<Option> slayer(SlayerType t) {
			List<Option> o = new ArrayList<>();
			for (SlayerFeature f : SlayerFeature.forType(t)) {
				o.add(Option.toggle((f.type != null ? "§e" : "§f") + f.label, f.description,
						() -> c().feature(t, f), v -> c().setFeature(t, f, v)));
			}
			o.add(Option.cycle("Glow-Farbe", "Farbe des Boss-Highlights", GLOW_COLORS, Menus::colorName,
					() -> nearestColor(c().glowColor(t)), v -> c().glowColors.put(t.key(), v)));
			o.add(Option.action("§a▶ Gambling-Vorschau", "Test-Animation mit den Drops dieses Slayers",
					() -> previewAndClose(t)));
			o.add(Option.action("§7Statistik im Chat", "Gesamtstatistik dieses Slayers im Chat zeigen", () -> {
				LifetimeStats.Entry e = KaisersSlayers.tracker().lifetime().get(t);
				KaisersSlayers.chat(t.color + t.bossName + " §7– §f" + e.kills + " Kills"
						+ (e.timedKills > 0 ? " §7Ø " + TextUtil.formatSeconds(e.averageKillMs()) + " Best " + TextUtil.formatSeconds(e.bestKillMs) : "")
						+ (e.level >= 0 ? " §7LVL " + e.level : ""));
			}));
			return o;
		}

		private static void previewAndClose(SlayerType t) {
			net.minecraft.client.Minecraft.getInstance().gui.setScreen(null);
			GamblingAnimation.preview(t);
		}

		static final List<Integer> GLOW_COLORS = List.of(
				0x55FF55, 0xFF5555, 0x5555FF, 0xFFFF55, 0xFF55FF, 0x55FFFF, 0xFFFFFF, 0xFFAA00, 0xAA00AA);

		static String colorName(int c) {
			return switch (c) {
				case 0x55FF55 -> "§aGrün";
				case 0xFF5555 -> "§cRot";
				case 0x5555FF -> "§9Blau";
				case 0xFFFF55 -> "§eGelb";
				case 0xFF55FF -> "§dPink";
				case 0x55FFFF -> "§bTürkis";
				case 0xFFFFFF -> "§fWeiß";
				case 0xFFAA00 -> "§6Orange";
				case 0xAA00AA -> "§5Lila";
				default -> "Eigene";
			};
		}

		private static int nearestColor(int c) {
			return GLOW_COLORS.contains(c) ? c : GLOW_COLORS.get(0);
		}

		private static float nearest(float v) {
			float[] opts = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
			float best = 1.0f;
			for (float o : opts) if (Math.abs(o - v) < Math.abs(best - v)) best = o;
			return best;
		}

		private static float nearestG(float v) {
			float[] opts = {0.75f, 1.0f, 1.25f, 1.5f};
			float best = 1.0f;
			for (float o : opts) if (Math.abs(o - v) < Math.abs(best - v)) best = o;
			return best;
		}
	}
}
