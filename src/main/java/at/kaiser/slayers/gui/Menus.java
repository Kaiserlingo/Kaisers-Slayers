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
	/** Index der ersten Slayer-Kategorie in der Seitenleiste */
	public static final int FIRST_SLAYER = 8;

	static {
		CATEGORIES.add(new Category("Overview", new ItemStack(Items.NETHER_STAR), 0xFFE74C3C, null, true, false));
		CATEGORIES.add(new Category("Alerts", new ItemStack(Items.BELL), 0xFFF39C12, null, false, false));
		CATEGORIES.add(new Category("World & Highlights", new ItemStack(Items.BEACON), 0xFF55FFFF, null, false, false));
		CATEGORIES.add(new Category("Tracker", new ItemStack(Items.CLOCK), 0xFF3498DB, null, false, false));
		CATEGORIES.add(new Category("Profit Tracker", new ItemStack(Items.GOLD_INGOT), 0xFFFFC234, null, false, true));
		CATEGORIES.add(new Category("HUD", new ItemStack(Items.PAINTING), 0xFF9B59B6, null, false, false));
		CATEGORIES.add(new Category("Gambling", new ItemStack(Items.EMERALD), 0xFF2ECC71, null, false, false));
		CATEGORIES.add(new Category("Carries", new ItemStack(Items.PLAYER_HEAD), 0xFFFF55FF, null, false, false));
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
			case "World & Highlights" -> world();
			case "Tracker" -> tracker();
			case "Profit Tracker" -> profit();
			case "HUD" -> hud();
			case "Gambling" -> gambling();
			case "Carries" -> carries();
			default -> cat.slayer() != null ? slayer(cat.slayer()) : new ArrayList<>();
		};
		for (Option x : o) x.category = cat.name();
		return o;
	}

	static List<Option> alerts() {
		List<Option> o = new ArrayList<>();
		o.add(Option.header("Boss"));
		o.add(Option.toggle("Boss Spawn Alert", "Big title + sound as soon as your boss spawns.", () -> c().spawnAlert, v -> c().spawnAlert = v));
		o.add(Option.toggle("Boss Spawning Soon", "Heads-up shortly before your boss spawns.", () -> c().soonSpawnAlert, v -> c().soonSpawnAlert = v));
		o.add(Option.slider("Spawning Soon At", "Quest progress (Combat XP) at which the heads-up appears.", 50, 99, 1,
				() -> (double) c().soonSpawnPercent, v -> c().soonSpawnPercent = (int) Math.round(v), v -> (int) v + "%"));
		o.add(Option.toggle("Boss Time Warning", "Warning when the boss timer is about to run out.", () -> c().bossTimeWarning, v -> c().bossTimeWarning = v));
		o.add(Option.slider("Warn At", "Seconds left on the boss timer.", 5, 90, 5,
				() -> (double) c().bossTimeWarnSec, v -> c().bossTimeWarnSec = (int) Math.round(v), v -> (int) v + "s left"));
		o.add(Option.toggle("Quest Failed Alert", "Title when your Slayer Quest fails.", () -> c().failAlert, v -> c().failAlert = v));
		o.add(Option.toggle("Call Maddox Message", "Clickable [CALL MADDOX] chat message when your quest fails (runs /call slayer – needs an Abiphone).",
				() -> c().callMaddoxMessage, v -> c().callMaddoxMessage = v));
		o.add(Option.header("Minibosses"));
		o.add(Option.toggle("Miniboss Alert", "Title + sound when a miniboss spawns next to you.", () -> c().minibossAlert, v -> c().minibossAlert = v));
		o.add(Option.toggle("Only Your Minibosses", "Highlight, beam, tracer and waypoint only for minibosses spawned by YOUR quest (matched via the SLAYER MINI-BOSS chat message).",
				() -> c().minibossOnlyOwn, v -> c().minibossOnlyOwn = v));
		o.add(Option.toggle("Miniboss Waypoint", "Arrow + distance to the miniboss at the top of the screen.", () -> c().minibossWaypoint, v -> c().minibossWaypoint = v));
		o.add(Option.slider("Waypoint Duration", "How long the miniboss arrow stays visible.", 5, 60, 5,
				() -> (double) c().waypointSeconds, v -> c().waypointSeconds = (int) Math.round(v), v -> (int) v + "s"));
		o.add(Option.header("General"));
		o.add(Option.toggle("Rare Drop Alert", "Title on CRAZY RARE / INSANE / RNGesus drops.", () -> c().dropAlert, v -> c().dropAlert = v));
		o.add(Option.toggle("Sounds", "All sounds of the mod.", () -> c().sounds, v -> c().sounds = v));
		o.add(Option.slider("Alert Size", "Size of the big titles.", 1.5, 5, 0.25,
				() -> (double) c().alertScale, v -> c().alertScale = v.floatValue(), v -> String.format(Locale.ROOT, "%.2fx", v)));
		o.add(Option.action("Test Alert", "Shows a test alert.", "▶ Test", () -> {
			Minecraft.getInstance().gui.setScreen(null);
			at.kaiser.slayers.hud.Feedback.alert("§c§lBOSS SPAWNED!", "§7This is what an alert looks like", 2000,
					net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.6f);
		}));
		return o;
	}

	static List<Option> world() {
		List<Option> o = new ArrayList<>();
		o.add(Option.header("Your Boss Fight"));
		o.add(Option.toggle("Hide Others During Your Boss", "While your boss is alive: no waypoints, beams, tracers or highlights for other players' bosses and minibosses.",
				() -> c().hideOthersDuringBoss, v -> c().hideOthersDuringBoss = v));
		o.add(Option.header("Beams"));
		o.add(Option.toggle("Beams", "Master switch for all beacon beams in the world.", () -> c().beamsEnabled, v -> c().beamsEnabled = v));
		o.add(Option.color("Miniboss Beam Color", "Color of the beam above minibosses.", () -> c().minibossBeamColor, v -> c().minibossBeamColor = v, 0x55FFFF));
		o.add(Option.header("Tracers"));
		o.add(Option.toggle("Miniboss Tracer", "Line from you to every miniboss (can also be toggled per slayer).", () -> c().minibossTracer, v -> c().minibossTracer = v));
		o.add(Option.color("Tracer Color", "Color of the miniboss tracer line.", () -> c().minibossTracerColor, v -> c().minibossTracerColor = v, 0x55FFFF));
		o.add(Option.header("Highlights"));
		o.add(Option.color("Miniboss Highlight Color", "Glow outline color of minibosses.", () -> c().minibossColor, v -> c().minibossColor = v, 0x5555FF));
		o.add(Option.toggle("Highlight Other Bosses", "Bosses of other players glow in their own color.", () -> c().otherBossGlow, v -> c().otherBossGlow = v));
		o.add(Option.color("Other Bosses Color", "Glow color for bosses of other players.", () -> c().otherBossColor, v -> c().otherBossColor = v, 0x888888));
		o.add(Option.slider("Ground Drop Highlight", "How long drops glow after the kill (only if \"Highlight Ground Drops\" is on in the slayer menu).", 5, 60, 5,
				() -> (double) c().dropGlowSeconds, v -> c().dropGlowSeconds = (int) Math.round(v), v -> (int) v + "s"));
		o.add(Option.header("Boss Timer"));
		o.add(Option.slider("Boss Time Limit", "Fallback if Hypixel shows no timer above the boss.", 60, 300, 10,
				() -> (double) c().bossTimeLimitSec, v -> c().bossTimeLimitSec = (int) Math.round(v), v -> (int) v + "s"));
		return o;
	}

	static List<Option> tracker() {
		List<Option> o = new ArrayList<>();
		o.add(Option.toggle("Kill Time in Chat", "After every kill: kill time + PB in chat.", () -> c().killMessage, v -> c().killMessage = v));
		o.add(Option.toggle("Quest Time in Chat", "Spawn time and total quest time in chat.", () -> c().questTimeMessage, v -> c().questTimeMessage = v));
		o.add(Option.toggle("Level ETA", "Slayer XP to the next level, bosses + time left.", () -> c().showLevelEta, v -> c().showLevelEta = v));
		o.add(Option.action("Reset Session", "Resets kills, times and session profit to 0.", "Reset", () -> {
			KaisersSlayers.tracker().resetSession();
			KaisersSlayers.chat("§aSession reset.");
		}));
		o.add(Option.action("Debug Info", "Writes all nametags around you to a file – helps with bug reports.", "Create",
				KaisersSlayers::writeDebugDump));
		return o;
	}

	static List<Option> profit() {
		List<Option> o = new ArrayList<>();
		o.add(Option.toggle("Profit Tracker", "Own profit HUD with drops, quest cost and profit.", () -> c().profitEnabled, v -> c().profitEnabled = v));
		o.add(Option.toggle("Edit in Inventory", "While an inventory or chest is open, the Profit HUD becomes editable: click items to hide them, toggle Session/Total and Instant Sell/Sell Offer, reset, drag to move, scroll to resize.",
				() -> c().inventoryTracker, v -> c().inventoryTracker = v));
		o.add(Option.choice("Period", "Session = since game start/reset, Total = everything so far.", List.of("SESSION", "TOTAL"),
				v -> v.equals("TOTAL") ? "Total" : "Session", () -> c().profitMode, v -> c().profitMode = v));
		o.add(Option.toggle("Count Inventory Drops", "Counts drops that go straight into your inventory without a chat message (Derelict Ashe, Null Sphere, Blaze Rod …). Menus are ignored.",
				() -> c().profitCountPickups, v -> c().profitCountPickups = v));
		o.add(Option.slider("Session Auto Reset", "Starts a new session after this long without doing slayer (0 = never).", 0, 120, 5,
				() -> (double) c().profitSessionTimeoutMin, v -> c().profitSessionTimeoutMin = (int) Math.round(v), v -> v == 0 ? "Off" : (int) v + " min"));
		o.add(Option.toggle("Count Mob Kill Coins", "Coins mobs add to your purse during the quest (sales, bank and menus are ignored).",
				() -> c().profitMobCoins, v -> c().profitMobCoins = v));
		o.add(Option.toggle("Count Sack Drops", "Also count items from [Sacks] messages (e.g. Revenant Flesh).", () -> c().profitCountSacks, v -> c().profitCountSacks = v));
		o.add(Option.choice("Price Mode", "Bazaar price: Instant Sell or Sell Offer.", List.of(true, false),
				v -> v ? "Instant Sell" : "Sell Offer", () -> c().instantSellPrices, v -> c().instantSellPrices = v));
		o.add(Option.choice("Quest Cost", "Aatrox perk \"Slashed Pricing\" halves the quest cost.", List.of(1.0, 0.5),
				v -> v == 1.0 ? "Normal (100%)" : "Aatrox (50%)", () -> c().costMultiplier, v -> {
					c().costMultiplier = v;
					KaisersSlayers.tracker().setCostMultiplier(v);
				}));
		o.add(Option.slider("Max Lines", "How many items the HUD lists individually.", 3, 20, 1,
				() -> (double) c().profitMaxItems, v -> c().profitMaxItems = (int) Math.round(v), v -> String.valueOf((int) v)));
		o.add(Option.choice("Min Value", "Items below this value are grouped together.", List.of(0L, 1_000L, 10_000L, 100_000L, 1_000_000L),
				v -> v == 0 ? "All" : "≥ " + TextUtil.formatCoins(v), () -> c().profitMinValue, v -> c().profitMinValue = v));
		o.add(Option.action("Refresh Prices", "Fetches new Bazaar / Lowest BIN prices right now.", "Refresh", () -> KaisersSlayers.prices().refresh()));
		o.add(Option.header("Items – click to hide/show"));
		return o;
	}

	static List<Option> hud() {
		List<Option> o = new ArrayList<>();
		o.add(Option.action("HUD Editor", "Drag HUDs with the mouse and scale them with the scroll wheel.", "Open",
				() -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(Minecraft.getInstance().gui.screen()))));
		o.add(Option.toggle("Slayer HUD", "Quest, progress, times, level.", () -> c().slayerHud.enabled, v -> c().slayerHud.enabled = v));
		o.add(Option.toggle("Profit HUD", "The profit tracker as its own card.", () -> c().profitHud.enabled, v -> c().profitHud.enabled = v));
		o.add(Option.toggle("Background", "Card background of the HUDs.", () -> c().hudBackground, v -> c().hudBackground = v));
		o.add(Option.toggle("Only With Active Quest", "Only show the HUD while a Slayer Quest is running.", () -> c().hudOnlyWhenActive, v -> c().hudOnlyWhenActive = v));
		o.add(Option.toggle("Waypoint Arrow", "Arrow + distance to minibosses and Yang Glyph beacons.", () -> c().waypointArrow, v -> c().waypointArrow = v));
		o.add(Option.slider("Boss Panel Offset", "Distance of the boss info below your crosshair.", 8, 80, 4,
				() -> (double) c().bossPanelOffsetY, v -> c().bossPanelOffsetY = (int) Math.round(v), v -> (int) v + "px"));
		o.add(Option.toggle("Clean Font", "Modern sans-serif font (Roboto) in menus and HUDs instead of the Minecraft pixel font.",
				() -> c().sansFont, v -> c().sansFont = v));
		o.add(Option.color("Menu Accent Color", "Highlight color of this menu and the HUDs.", () -> c().accentColor, v -> c().accentColor = v, 0xE74C3C));
		return o;
	}

	static List<Option> gambling() {
		List<Option> o = new ArrayList<>();
		o.add(Option.toggle("Gambling Animation", "Master switch (can also be toggled per slayer).", () -> c().gamblingEnabled, v -> c().gamblingEnabled = v));
		o.add(Option.choice("Animation", "Case opening, slot machine, wheel, cards – or random every time.",
				List.of("CASE", "SLOTS", "WHEEL", "CARDS", "RANDOM"), Menus::styleName, () -> c().gamblingStyle, v -> c().gamblingStyle = v));
		o.add(Option.slider("Duration", "How long it spins.", 2, 12, 0.5,
				() -> c().gamblingDurationMs / 1000.0, v -> c().gamblingDurationMs = (int) Math.round(v * 1000), v -> String.format(Locale.ROOT, "%.1fs", v)));
		o.add(Option.choice("Trigger", "On every kill or only on RARE DROP and better.", List.of("ALWAYS", "RARE_ONLY"),
				v -> v.equals("ALWAYS") ? "Every Kill" : "Rare Drops Only", () -> c().gamblingTrigger, v -> c().gamblingTrigger = v));
		o.add(Option.toggle("Hide HUD + Chat", "Hides everything from the kill on so nothing spoils your drop.", () -> c().gamblingHideHud, v -> c().gamblingHideHud = v));
		o.add(Option.header("Effects"));
		o.add(Option.toggle("Particles & Confetti", "Sparks on a win, confetti on a jackpot.", () -> c().gamblingParticles, v -> c().gamblingParticles = v));
		o.add(Option.toggle("Screen Shake", "Short shake on a win.", () -> c().gamblingShake, v -> c().gamblingShake = v));
		o.add(Option.toggle("Flash", "Bright flash on the reveal.", () -> c().gamblingFlash, v -> c().gamblingFlash = v));
		o.add(Option.toggle("Sounds", "Tick sounds and win sounds.", () -> c().gamblingSounds, v -> c().gamblingSounds = v));
		o.add(Option.slider("Dim Background", "How much the background gets darkened.", 30, 100, 5,
				() -> (double) c().gamblingDim, v -> c().gamblingDim = (int) Math.round(v), v -> (int) v + "%"));
		o.add(Option.color("Accent Color", "Color of frame, pointer and lights.", () -> c().gamblingAccent, v -> c().gamblingAccent = v, 0xFFD700));
		o.add(Option.choice("Jackpot From", "Drops worth at least this get the jackpot effect.",
				List.of(1_000_000L, 5_000_000L, 10_000_000L, 50_000_000L, 100_000_000L), v -> TextUtil.formatCoins(v),
				() -> c().gamblingJackpotValue, v -> c().gamblingJackpotValue = v));
		o.add(Option.toggle("Show Value", "Coin value of the drop on the result.", () -> c().gamblingShowValue, v -> c().gamblingShowValue = v));
		o.add(Option.toggle("Own Drops in the Reel", "Items you already dropped also show up.", () -> c().gamblingUseOwnDrops, v -> c().gamblingUseOwnDrops = v));
		o.add(Option.header("Preview"));
		for (String s : List.of("CASE", "SLOTS", "WHEEL", "CARDS")) {
			o.add(Option.action(styleName(s), "Plays the animation with a random drop.", "▶ Test", () -> preview(s)));
		}
		return o;
	}

	static List<Option> carries() {
		List<Option> o = new ArrayList<>();
		o.add(Option.header("Carry Tracker"));
		o.add(Option.toggle("Carry Tracker", "Tracks the bosses of your carry clients: /ks carry add <player> <amount> [price per carry].",
				() -> c().carryEnabled, v -> c().carryEnabled = v));
		o.add(Option.toggle("Carry HUD", "HUD with all clients, player heads and progress (move it in the HUD Editor).",
				() -> c().carryHud.enabled, v -> c().carryHud.enabled = v));
		o.add(Option.toggle("Player Heads", "Shows the skin face of every client.", () -> c().carryShowHeads, v -> c().carryShowHeads = v));
		o.add(Option.toggle("Progress Bars", "Thin progress bar under every client.", () -> c().carryShowBars, v -> c().carryShowBars = v));
		o.add(Option.toggle("Live Boss Info", "While a client's boss is alive: boss name, HP and time left in the HUD.",
				() -> c().carryShowBossInfo, v -> c().carryShowBossInfo = v));
		o.add(Option.toggle("Payment Info", "Shows how much each client still owes (set with /ks carry price + paid).",
				() -> c().carryShowPayment, v -> c().carryShowPayment = v));
		o.add(Option.toggle("Compact Mode", "One line per client – for many clients at once.", () -> c().carryCompact, v -> c().carryCompact = v));
		o.add(Option.color("HUD Accent", "Accent color of the Carry HUD.", () -> c().carryAccent, v -> c().carryAccent = v, 0xFF55FF));
		o.add(Option.header("Client Bosses"));
		o.add(Option.toggle("Highlight Client Bosses", "Bosses of your clients glow in their own color (also during your own boss).",
				() -> c().carryGlow, v -> c().carryGlow = v));
		o.add(Option.color("Client Boss Color", "Glow color of your clients' bosses.", () -> c().carryGlowColor, v -> c().carryGlowColor = v, 0xFF55FF));
		o.add(Option.toggle("Boss Spawn Alert", "Alert when a client's boss spawns near you.", () -> c().carryBossAlert, v -> c().carryBossAlert = v));
		o.add(Option.header("Messages"));
		o.add(Option.toggle("Chat Message per Carry", "After every carry: progress in chat + a [Party] button that puts \"Name: 3/10 carries done\" into your chat box (you send it yourself).",
				() -> c().carryChatMessage, v -> c().carryChatMessage = v));
		o.add(Option.toggle("Carry Done Alert", "Big title + sound when a client's carries are finished.", () -> c().carryDoneAlert, v -> c().carryDoneAlert = v));
		o.add(Option.toggle("Remove Finished Clients", "Removes a client automatically once all carries are done.",
				() -> c().carryRemoveFinished, v -> c().carryRemoveFinished = v));
		o.add(Option.header("Clients"));
		o.add(Option.action("Remove Finished", "Removes all clients whose carries are done.", "Clear", () -> {
			int n = KaisersSlayers.carries().clearFinished();
			KaisersSlayers.saveCarries();
			KaisersSlayers.chat("§7Removed §f" + n + " §7finished client(s).");
		}));
		o.add(Option.action("Remove All Clients", "Removes every carry client.", "Clear All", () -> {
			KaisersSlayers.carries().clear();
			KaisersSlayers.saveCarries();
			KaisersSlayers.chat("§7All carry clients removed.");
		}));
		return o;
	}

	static List<Option> slayer(SlayerType t) {
		List<Option> o = new ArrayList<>();
		o.add(Option.header("General"));
		boolean special = false;
		for (SlayerFeature f : SlayerFeature.forType(t)) {
			if (f.type != null && !special) {
				o.add(Option.header(t.bossName + " – Special"));
				special = true;
			}
			o.add(Option.toggle(f.label, f.description, () -> c().feature(t, f), v -> c().setFeature(t, f, v)));
		}
		o.add(Option.header("Look & Test"));
		if (t == SlayerType.ENDERMAN) {
			o.add(Option.color("Beacon & Skull Color", "One bright color for Yang Glyph beacons and Nukekubi skulls (glow, box through walls, beam).",
					() -> c().voidgloomColor, v -> c().voidgloomColor = v, 0x39FF14));
		}
		String note = switch (t) {
			case ENDERMAN -> " (\"Phase Color\" overrides it)";
			case BLAZE -> " (\"Attunement Color\" overrides it)";
			default -> "";
		};
		o.add(Option.color("Boss Highlight Color", "Glow outline color of your boss" + note + ".", () -> c().glowColor(t),
				v -> c().glowColors.put(t.key(), v), t.defaultGlow));
		o.add(Option.action("Gambling Preview", "Test animation with the drops of this slayer.", "▶ Test", () -> {
			Minecraft.getInstance().gui.setScreen(null);
			GamblingAnimation.preview(t, null);
		}));
		return o;
	}

	static String styleName(String s) {
		return switch (s) {
			case "CASE" -> "Case Opening";
			case "SLOTS" -> "Slot Machine";
			case "WHEEL" -> "Wheel of Fortune";
			case "CARDS" -> "Cards";
			default -> "Random";
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
