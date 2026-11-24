package at.kaiser.slayers.gui;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Inhalte aller Menü-Kategorien. Neue Einstellungen einfach hier eintragen. */
public final class Menus {
	/** Eine Kategorie in der Seitenleiste */
	public record Category(String name, ItemStack icon, int accent, SlayerType slayer, boolean dashboard, boolean profitTable) {}

	public static final List<Category> CATEGORIES = new ArrayList<>();

	static {
		CATEGORIES.add(new Category("Übersicht", new ItemStack(Items.NETHER_STAR), 0xFFE74C3C, null, true, false));
		CATEGORIES.add(new Category("Alerts", new ItemStack(Items.BELL), 0xFFF39C12, null, false, false));
		CATEGORIES.add(new Category("Welt & Highlights", new ItemStack(Items.BEACON), 0xFF55FFFF, null, false, false));
		CATEGORIES.add(new Category("Tracker", new ItemStack(Items.CLOCK), 0xFF3498DB, null, false, false));
		CATEGORIES.add(new Category("Profit-Tracker", new ItemStack(Items.GOLD_INGOT), 0xFFFFC234, null, false, true));
		CATEGORIES.add(new Category("HUD", new ItemStack(Items.PAINTING), 0xFF9B59B6, null, false, false));
		CATEGORIES.add(new Category("Gambling", new ItemStack(Items.EMERALD), 0xFF2ECC71, null, false, false));
		for (SlayerType t : SlayerType.values()) {
			CATEGORIES.add(new Category(t.bossName, Ui.slayerIcon(t), Ui.slayerAccent(t), t, false, false));
		}
	}

	private Menus() {}

	private static ModConfig c() {
		return KaisersSlayers.config();
	}

	public static List<Option> options(Category cat) {
		List<Option> o = switch (cat.name()) {
			case "Alerts" -> alerts();
			case "Welt & Highlights" -> world();
			case "Tracker" -> tracker();
			case "Profit-Tracker" -> profit();
			case "HUD" -> hud();
			case "Gambling" -> gambling();
			default -> cat.slayer() != null ? slayer(cat.slayer()) : new ArrayList<>();
		};
		for (Option x : o) x.category = cat.name();
		return o;
	}

	static List<Option> alerts() {
		List<Option> o = new ArrayList<>();
		o.add(Option.header("Boss"));
		o.add(Option.toggle("Boss-Spawn-Alert", "Große Einblendung + Sound, sobald dein Boss spawnt.", () -> c().spawnAlert, v -> c().spawnAlert = v));
		o.add(Option.toggle("Boss spawnt gleich", "Vorwarnung kurz bevor der Boss spawnt.", () -> c().soonSpawnAlert, v -> c().soonSpawnAlert = v));
		o.add(Option.slider("Vorwarnung ab", "Ab welchem Quest-Fortschritt die Vorwarnung kommt.", 50, 99, 1,
				() -> (double) c().soonSpawnPercent, v -> c().soonSpawnPercent = (int) Math.round(v), v -> (int) v + "%"));
		o.add(Option.toggle("Boss-Zeitwarnung", "Warnung, wenn das Zeitlimit des Bosses bald abläuft.", () -> c().bossTimeWarning, v -> c().bossTimeWarning = v));
		o.add(Option.slider("Warnung bei", "Sekunden vor Ablauf des Zeitlimits.", 5, 90, 5,
				() -> (double) c().bossTimeWarnSec, v -> c().bossTimeWarnSec = (int) Math.round(v), v -> (int) v + "s Rest"));
		o.add(Option.toggle("Quest-Failed-Alert", "Einblendung, wenn der Quest fehlschlägt.", () -> c().failAlert, v -> c().failAlert = v));
		o.add(Option.header("Minibosse"));
		o.add(Option.toggle("Miniboss-Alert", "Einblendung + Sound, wenn ein Miniboss neben dir spawnt.", () -> c().minibossAlert, v -> c().minibossAlert = v));
		o.add(Option.toggle("Miniboss-Waypoint", "Pfeil + Entfernung zum Miniboss oben am Bildschirm.", () -> c().minibossWaypoint, v -> c().minibossWaypoint = v));
		o.add(Option.slider("Waypoint-Dauer", "Wie lange der Pfeil zum Miniboss sichtbar bleibt.", 5, 60, 5,
				() -> (double) c().waypointSeconds, v -> c().waypointSeconds = (int) Math.round(v), v -> (int) v + "s"));
		o.add(Option.header("Allgemein"));
		o.add(Option.toggle("Drop-Alert", "Einblendung bei CRAZY RARE / INSANE / RNGesus Drops.", () -> c().dropAlert, v -> c().dropAlert = v));
		o.add(Option.toggle("Sounds", "Alle Sounds der Mod.", () -> c().sounds, v -> c().sounds = v));
		o.add(Option.slider("Alert-Größe", "Größe der großen Einblendungen.", 1.5, 5, 0.25,
				() -> (double) c().alertScale, v -> c().alertScale = v.floatValue(), v -> String.format(Locale.ROOT, "%.2fx", v)));
		o.add(Option.action("Alert testen", "Zeigt einen Test-Alert.", "▶ Test", () -> {
			Minecraft.getInstance().gui.setScreen(null);
			at.kaiser.slayers.hud.Feedback.alert("§c§lBOSS SPAWNED!", "§7So sieht ein Alert aus", 2000,
					net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6f);
		}));
		return o;
	}

	static List<Option> world() {
		List<Option> o = new ArrayList<>();
		o.add(Option.header("Lichtstrahlen"));
		o.add(Option.toggle("Strahlen aktiv", "Hauptschalter für alle Lichtstrahlen in der Welt.", () -> c().beamsEnabled, v -> c().beamsEnabled = v));
		o.add(Option.color("Miniboss-Strahl", "Farbe des Strahls über Minibossen.", () -> c().minibossBeamColor, v -> c().minibossBeamColor = v));
		o.add(Option.header("Highlights"));
		o.add(Option.color("Miniboss-Highlight", "Farbe des Leucht-Umrisses von Minibossen.", () -> c().minibossColor, v -> c().minibossColor = v));
		o.add(Option.toggle("Fremde Bosse markieren", "Bosse anderer Spieler leuchten in einer eigenen Farbe.", () -> c().otherBossGlow, v -> c().otherBossGlow = v));
		o.add(Option.color("Farbe fremder Bosse", "Leuchtfarbe für Bosse anderer Spieler.", () -> c().otherBossColor, v -> c().otherBossColor = v));
		o.add(Option.slider("Drops leuchten für", "Wie lange Drops nach dem Kill am Boden leuchten.", 5, 60, 5,
				() -> (double) c().dropGlowSeconds, v -> c().dropGlowSeconds = (int) Math.round(v), v -> (int) v + "s"));
		o.add(Option.header("Boss-Timer"));
		o.add(Option.slider("Boss-Zeitlimit", "Fallback, falls Hypixel keinen Timer am Boss zeigt.", 60, 300, 10,
				() -> (double) c().bossTimeLimitSec, v -> c().bossTimeLimitSec = (int) Math.round(v), v -> (int) v + "s"));
		return o;
	}

	static List<Option> tracker() {
		List<Option> o = new ArrayList<>();
		o.add(Option.toggle("Kill-Zeit im Chat", "Nach jedem Kill: Kill-Zeit + Rekord im Chat.", () -> c().killMessage, v -> c().killMessage = v));
		o.add(Option.toggle("Quest-Zeit im Chat", "Spawn- und Gesamtzeit des Quests im Chat.", () -> c().questTimeMessage, v -> c().questTimeMessage = v));
		o.add(Option.toggle("Level-Prognose", "XP bis zum nächsten Slayer-Level, Bosse + Zeit.", () -> c().showLevelEta, v -> c().showLevelEta = v));
		o.add(Option.action("Session zurücksetzen", "Setzt Kills, Zeiten und Session-Profit auf 0.", "Reset", () -> {
			KaisersSlayers.tracker().resetSession();
			KaisersSlayers.chat("§aSession zurückgesetzt.");
		}));
		o.add(Option.action("Debug-Info", "Schreibt alle Namensschilder in deiner Nähe in eine Datei – hilft beim Melden von Fehlern.", "Erstellen",
				KaisersSlayers::writeDebugDump));
		return o;
	}

	static List<Option> profit() {
		List<Option> o = new ArrayList<>();
		o.add(Option.toggle("Profit-Tracker", "Eigenes Profit-HUD mit Drops, Kosten und Gewinn.", () -> c().profitEnabled, v -> c().profitEnabled = v));
		o.add(Option.choice("Zeitraum", "Session = seit Spielstart/Reset, Gesamt = alles bisher.", List.of("SESSION", "TOTAL"),
				v -> v.equals("TOTAL") ? "Gesamt" : "Session", () -> c().profitMode, v -> c().profitMode = v));
		o.add(Option.toggle("Sack-Drops zählen", "Auch Items aus [Sacks]-Nachrichten zählen (z. B. Revenant Flesh).", () -> c().profitCountSacks, v -> c().profitCountSacks = v));
		o.add(Option.choice("Preis-Modus", "Instant-Sell = sofort verkaufen, Sell-Offer = mit Angebot.", List.of(true, false),
				v -> v ? "Instant-Sell" : "Sell-Offer", () -> c().instantSellPrices, v -> c().instantSellPrices = v));
		o.add(Option.choice("Quest-Kosten", "Aatrox \"Slashed Pricing\" halbiert die Quest-Kosten.", List.of(1.0, 0.5),
				v -> v == 1.0 ? "Normal (100%)" : "Aatrox (50%)", () -> c().costMultiplier, v -> {
					c().costMultiplier = v;
					KaisersSlayers.tracker().setCostMultiplier(v);
				}));
		o.add(Option.slider("Max. Zeilen", "Wie viele Items im HUD einzeln gezeigt werden.", 3, 20, 1,
				() -> (double) c().profitMaxItems, v -> c().profitMaxItems = (int) Math.round(v), v -> String.valueOf((int) v)));
		o.add(Option.choice("Mindestwert", "Items darunter werden zusammengefasst.", List.of(0L, 1_000L, 10_000L, 100_000L, 1_000_000L),
				v -> v == 0 ? "Alle" : "ab " + TextUtil.formatCoins(v), () -> c().profitMinValue, v -> c().profitMinValue = v));
		o.add(Option.action("Preise neu laden", "Holt sofort neue Bazaar-Preise.", "Laden", () -> KaisersSlayers.prices().refresh()));
		o.add(Option.header("Items – klicken zum Aus-/Einblenden"));
		return o;
	}

	static List<Option> hud() {
		List<Option> o = new ArrayList<>();
		o.add(Option.action("HUD-Editor", "HUDs mit der Maus verschieben (ziehen) und skalieren (Mausrad).", "Öffnen",
				() -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(Minecraft.getInstance().gui.screen())));
		o.add(Option.toggle("Slayer-HUD", "Quest, Fortschritt, Zeiten, Level.", () -> c().slayerHud.enabled, v -> c().slayerHud.enabled = v));
		o.add(Option.toggle("Profit-HUD", "Der Profit-Tracker als eigene Karte.", () -> c().profitHud.enabled, v -> c().profitHud.enabled = v));
		o.add(Option.toggle("Hintergrund", "Karten-Hintergrund der HUDs.", () -> c().hudBackground, v -> c().hudBackground = v));
		o.add(Option.toggle("Nur bei aktivem Quest", "HUD nur zeigen, wenn ein Slayer-Quest läuft.", () -> c().hudOnlyWhenActive, v -> c().hudOnlyWhenActive = v));
		o.add(Option.toggle("Waypoint-Pfeil", "Pfeil + Entfernung zu Minibossen und Beacons.", () -> c().waypointArrow, v -> c().waypointArrow = v));
		o.add(Option.slider("Boss-Panel Abstand", "Abstand der Boss-Infos unter dem Fadenkreuz.", 8, 80, 4,
				() -> (double) c().bossPanelOffsetY, v -> c().bossPanelOffsetY = (int) Math.round(v), v -> (int) v + "px"));
		o.add(Option.color("Menü-Akzentfarbe", "Farbe der Hervorhebungen in diesem Menü.", () -> c().accentColor, v -> c().accentColor = v));
		return o;
	}

	static List<Option> gambling() {
		List<Option> o = new ArrayList<>();
		o.add(Option.toggle("Gambling-Animation", "Hauptschalter (zusätzlich pro Slayer einstellbar).", () -> c().gamblingEnabled, v -> c().gamblingEnabled = v));
		o.add(Option.choice("Animation", "Kisten-Öffnung, Slot-Machine, Glücksrad, Karten – oder jedes Mal zufällig.",
				List.of("CASE", "SLOTS", "WHEEL", "CARDS", "RANDOM"), Menus::styleName, () -> c().gamblingStyle, v -> c().gamblingStyle = v));
		o.add(Option.slider("Dauer", "Wie lange gedreht wird.", 2, 12, 0.5,
				() -> c().gamblingDurationMs / 1000.0, v -> c().gamblingDurationMs = (int) Math.round(v * 1000), v -> String.format(Locale.ROOT, "%.1fs", v)));
		o.add(Option.choice("Auslöser", "Bei jedem Kill oder nur bei RARE DROP und besser.", List.of("ALWAYS", "RARE_ONLY"),
				v -> v.equals("ALWAYS") ? "Jeder Kill" : "Nur Rare Drops", () -> c().gamblingTrigger, v -> c().gamblingTrigger = v));
		o.add(Option.toggle("HUD + Chat ausblenden", "Ab dem Kill alles ausblenden, damit du nicht gespoilert wirst.", () -> c().gamblingHideHud, v -> c().gamblingHideHud = v));
		o.add(Option.header("Effekte"));
		o.add(Option.toggle("Partikel & Konfetti", "Funken beim Gewinn, Konfetti beim Jackpot.", () -> c().gamblingParticles, v -> c().gamblingParticles = v));
		o.add(Option.toggle("Bildschirm-Wackeln", "Kurzes Wackeln beim Gewinn.", () -> c().gamblingShake, v -> c().gamblingShake = v));
		o.add(Option.toggle("Blitz", "Heller Blitz beim Aufdecken.", () -> c().gamblingFlash, v -> c().gamblingFlash = v));
		o.add(Option.toggle("Sounds", "Tick-Geräusche und Gewinn-Sounds.", () -> c().gamblingSounds, v -> c().gamblingSounds = v));
		o.add(Option.slider("Abdunkeln", "Wie stark der Hintergrund abgedunkelt wird.", 30, 100, 5,
				() -> (double) c().gamblingDim, v -> c().gamblingDim = (int) Math.round(v), v -> (int) v + "%"));
		o.add(Option.color("Akzentfarbe", "Farbe von Rahmen, Zeiger und Lichtern.", () -> c().gamblingAccent, v -> c().gamblingAccent = v));
		o.add(Option.choice("Jackpot ab", "Ab diesem Drop-Wert gibt es den Jackpot-Effekt.",
				List.of(1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L, 100_000_000L), v -> TextUtil.formatCoins(v),
				() -> c().gamblingJackpotValue, v -> c().gamblingJackpotValue = v));
		o.add(Option.toggle("Wert anzeigen", "Coin-Wert des Drops beim Ergebnis.", () -> c().gamblingShowValue, v -> c().gamblingShowValue = v));
		o.add(Option.toggle("Eigene Drops in der Walze", "Items, die du schon gedroppt hast, erscheinen auch.", () -> c().gamblingUseOwnDrops, v -> c().gamblingUseOwnDrops = v));
		o.add(Option.header("Vorschau"));
		for (String s : List.of("CASE", "SLOTS", "WHEEL", "CARDS")) {
			o.add(Option.action(styleName(s), "Spielt die Animation mit einem zufälligen Drop ab.", "▶ Test", () -> preview(s)));
		}
		return o;
	}

	static List<Option> slayer(SlayerType t) {
		List<Option> o = new ArrayList<>();
		o.add(Option.header("Allgemein"));
		boolean special = false;
		for (SlayerFeature f : SlayerFeature.forType(t)) {
			if (f.type != null && !special) {
				o.add(Option.header(t.bossName + " – Spezial"));
				special = true;
			}
			o.add(Option.toggle(f.label, f.description, () -> c().feature(t, f), v -> c().setFeature(t, f, v)));
		}
		o.add(Option.header("Aussehen & Test"));
		o.add(Option.color("Boss-Highlight-Farbe", "Farbe des Leucht-Umrisses deines Bosses.", () -> c().glowColor(t), v -> c().glowColors.put(t.key(), v)));
		o.add(Option.action("Gambling-Vorschau", "Test-Animation mit den Drops dieses Slayers.", "▶ Test", () -> {
			Minecraft.getInstance().gui.setScreen(null);
			GamblingAnimation.preview(t, null);
		}));
		return o;
	}

	static String styleName(String s) {
		return switch (s) {
			case "CASE" -> "Kisten-Öffnung";
			case "SLOTS" -> "Slot-Machine";
			case "WHEEL" -> "Glücksrad";
			case "CARDS" -> "Karten";
			default -> "Zufällig";
		};
	}

	private static void preview(String style) {
		Minecraft.getInstance().gui.setScreen(null);
		String old = c().gamblingStyle;
		SlayerType t = KaisersSlayers.tracker().type() != null ? KaisersSlayers.tracker().type() : SlayerType.ZOMBIE;
		GamblingAnimation.preview(t, style);
		c().gamblingStyle = old;
	}
}
