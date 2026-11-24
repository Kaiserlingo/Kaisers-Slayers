package at.kaiser.slayers;

import at.kaiser.slayers.command.SlayerCommands;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gui.MainMenuScreen;
import at.kaiser.slayers.hud.BossPanelHud;
import at.kaiser.slayers.hud.AlertOverlay;
import at.kaiser.slayers.hud.SlayerHud;
import at.kaiser.slayers.price.PriceService;
import at.kaiser.slayers.slayer.SlayerTracker;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.ScoreboardReader;
import at.kaiser.slayers.util.TextUtil;
import at.kaiser.slayers.world.WorldScanner;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class KaisersSlayers implements ClientModInitializer {
	public static final String MOD_ID = "kaisers_slayers";
	public static final Logger LOGGER = LoggerFactory.getLogger("Kaiser's Slayers");

	private static ModConfig config;
	private static final SlayerTracker TRACKER = new SlayerTracker();
	private static final PriceService PRICES = new PriceService();
	private static final WorldScanner SCANNER = new WorldScanner();
	private static Screen pendingScreen;
	private static int soonAlertedQuest = -1;
	private static boolean onSkyBlock;
	private static boolean forceHud;
	private static int tickCounter;
	private static boolean bossWarned;

	private static KeyMapping toggleHudKey;
	private static KeyMapping postStatsKey;

	@Override
	public void onInitializeClient() {
		config = ModConfig.load();
		TRACKER.setLifetime(ModConfig.loadStats());
		TRACKER.setListener(new Alerts());

		// --- Tastenbelegungen (standardmäßig frei, unter Optionen > Steuerung festlegen) ---
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.kaisers_slayers.toggle_hud", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		postStatsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.kaisers_slayers.post_stats", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));

		// --- HUD ---
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("slayer_hud"), SlayerHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("boss_panel"), BossPanelHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("gambling"), GamblingAnimation::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("alert"), AlertOverlay::render);

		// --- Chat ---
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			TRACKER.onChat(message.getString(), System.currentTimeMillis());
		});

		// --- Tick ---
		ClientTickEvents.END_CLIENT_TICK.register(KaisersSlayers::onTick);

		// --- Befehle ---
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> SlayerCommands.register(dispatcher));

		// --- Speichern ---
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> saveStats());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveStats());

		LOGGER.info("Kaiser's Slayers geladen.");
	}

	/** GUI erst im nächsten Tick öffnen (sonst schließt der Chat sie gleich wieder). */
	public static void openScreen(Screen screen) {
		pendingScreen = screen;
	}

	public static void openMenu() {
		openScreen(new MainMenuScreen());
	}

	private static void onTick(Minecraft mc) {
		if (pendingScreen != null) {
			Screen s = pendingScreen;
			pendingScreen = null;
			mc.gui.setScreen(s);
		}
		while (toggleHudKey.consumeClick()) {
			config.hudEnabled = !config.hudEnabled;
			config.save();
			chat("§7HUD: " + (config.hudEnabled ? "§aAN" : "§cAUS"));
		}
		while (postStatsKey.consumeClick()) {
			for (String l : SlayerHud.buildLines(TRACKER, config, PRICES, System.currentTimeMillis())) {
				if (mc.player != null) mc.player.sendSystemMessage(Component.literal(l));
			}
		}

		if (mc.player == null || mc.level == null) {
			if (onSkyBlock) SCANNER.reset();
			onSkyBlock = false;
			return;
		}
		GamblingAnimation.tick(System.currentTimeMillis());

		// Welt-Scanner 10x pro Sekunde (Boss, Minibosse, Slayer-Features)
		if (tickCounter % 2 == 0 && onSkyBlock) {
			try {
				SCANNER.scan(mc, TRACKER, config, System.currentTimeMillis());
			} catch (Exception e) {
				LOGGER.warn("Fehler im Welt-Scanner", e);
			}
		}

		// Scoreboard 4x pro Sekunde auswerten
		if (++tickCounter % 5 != 0) return;
		long now = System.currentTimeMillis();
		onSkyBlock = ScoreboardReader.isOnSkyBlock(mc);
		List<String> lines = onSkyBlock ? ScoreboardReader.readLines(mc) : null;
		TRACKER.onScoreboard(lines, now);
		TRACKER.tick(now);
		if (onSkyBlock) PRICES.tick(now);
		checkBossTimer(now);
		checkSoonSpawn();

		if (tickCounter % 200 == 0 && TRACKER.consumeDirty()) saveStats();
	}

	/** "Boss spawnt gleich" – einmal pro Quest, wenn der Fortschritt die Schwelle erreicht. */
	private static void checkSoonSpawn() {
		if (!config.soonSpawnAlert || TRACKER.phase() != SlayerTracker.Phase.GRINDING || TRACKER.progressMax() <= 0) return;
		if (soonAlertedQuest == TRACKER.questCounter()) return;
		if (!config.feature(TRACKER.type(), SlayerFeature.TRACKING)) return;
		double pct = 100.0 * TRACKER.progress() / TRACKER.progressMax();
		if (pct >= config.soonSpawnPercent && pct < 100) {
			soonAlertedQuest = TRACKER.questCounter();
			AlertOverlay.show("§e§lBOSS SPAWNT GLEICH!", "§7" + String.format(java.util.Locale.ROOT, "%.0f%%", pct) + " Fortschritt", 1500);
			if (config.sounds) AlertOverlay.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f);
		}
	}

	/** Warnung, wenn das Boss-Zeitlimit bald abläuft. */
	private static void checkBossTimer(long now) {
		if (TRACKER.phase() != SlayerTracker.Phase.BOSS_ALIVE || TRACKER.bossSpawnTime() <= 0) {
			bossWarned = false;
			return;
		}
		int standSec = SCANNER.state().found ? SCANNER.state().timeLeftSec : -1;
		long left = standSec >= 0 ? standSec * 1000L
				: config.bossTimeLimitSec * 1000L - (now - TRACKER.bossSpawnTime());
		if (!bossWarned && left <= config.bossTimeWarnSec * 1000L && left > 0) {
			bossWarned = true;
			if (config.bossTimeWarning) AlertOverlay.show("§c§lZEIT LÄUFT AB!", "§7Noch " + (left / 1000) + " Sekunden", 2000);
			if (config.bossTimeWarning && config.sounds) AlertOverlay.playSound(SoundEvents.ANVIL_LAND, 1.5f);
		}
	}

	private static void saveStats() {
		TRACKER.consumeDirty();
		ModConfig.saveStats(TRACKER.lifetime());
	}

	public static void chat(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[KS] " + msg));
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	public static ModConfig config() { return config; }
	public static SlayerTracker tracker() { return TRACKER; }
	public static PriceService prices() { return PRICES; }
	public static WorldScanner scanner() { return SCANNER; }
	public static boolean onSkyBlock() { return onSkyBlock; }
	public static boolean forceHud() { return forceHud; }

	public static boolean toggleForceHud() {
		forceHud = !forceHud;
		return forceHud;
	}

	/** Reagiert auf Ereignisse des Trackers mit Einblendungen, Sounds und Chat-Nachrichten. */
	private static final class Alerts implements SlayerTracker.Listener {
		@Override
		public void onBossSpawned(SlayerType type, int tier, long spawnMs) {
			if (!config.feature(type, SlayerFeature.TRACKING)) return;
			if (config.spawnAlert) {
				AlertOverlay.show("§c§lBOSS SPAWNED!", type.color + type.bossName + " " + TextUtil.toRoman(tier), 2000);
			}
			if (config.sounds) AlertOverlay.playSound(SoundEvents.PLAYER_LEVELUP, 0.6f);
			if (config.killMessage && spawnMs > 0) chat("§7Boss gespawnt nach §f" + TextUtil.formatSeconds(spawnMs));
		}

		@Override
		public void onBossKilled(SlayerType type, int tier, long killMs, boolean newBest) {
			GamblingAnimation.onKill(type, System.currentTimeMillis());
			if (!config.feature(type, SlayerFeature.TRACKING)) return;
			if (config.killMessage) {
				String time = killMs > 0 ? TextUtil.formatSeconds(killMs) : "?";
				chat(type.color + type.bossName + " " + TextUtil.toRoman(tier) + " §7getötet in §f" + time
						+ (newBest ? " §a§lNEUER REKORD!" : "")
						+ " §8(Session: " + TRACKER.session().kills + ")");
			}
			if (config.sounds) AlertOverlay.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f);
		}

		@Override
		public void onQuestFailed(SlayerType type, int tier) {
			if (config.failAlert) AlertOverlay.show("§4§lQUEST FAILED", "§7Slayer-Quest fehlgeschlagen", 2500);
			if (config.sounds) AlertOverlay.playSound(SoundEvents.ANVIL_LAND, 0.5f);
		}

		@Override
		public void onRareDrop(String rarity, String item, int count, boolean big) {
			if (!big) return;
			if (GamblingAnimation.isActiveOrPending()) return; // kein Spoiler – die Animation zeigt den Drop
			if (config.dropAlert) AlertOverlay.show("§6§l" + rarity + "!", "§f" + (count > 1 ? count + "x " : "") + item, 4000);
			if (config.sounds) AlertOverlay.playSound(SoundEvents.PLAYER_LEVELUP, 1.4f);
		}
	}
}
