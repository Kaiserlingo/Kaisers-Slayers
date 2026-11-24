package at.kaiser.slayers;

import at.kaiser.slayers.command.SlayerCommands;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.config.SlayerFeature;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gui.KsScreen;
import at.kaiser.slayers.hud.AlertOverlay;
import at.kaiser.slayers.hud.BossPanelHud;
import at.kaiser.slayers.hud.InventoryTracker;
import at.kaiser.slayers.hud.Feedback;
import at.kaiser.slayers.hud.ProfitHud;
import at.kaiser.slayers.hud.SlayerHud;
import at.kaiser.slayers.price.PriceService;
import at.kaiser.slayers.render.WorldRender;
import at.kaiser.slayers.slayer.ProfitTracker;
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
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
	private static boolean demo;
	private static int tickCounter;
	private static boolean bossWarned;
	private static long lastMinibossAlert;

	private static KeyMapping menuKey;
	private static KeyMapping toggleHudKey;
	private static KeyMapping skipGamblingKey;

	/** Alle Vanilla-HUD-Elemente, die während der Gambling-Animation versteckt werden */
	private static final List<Identifier> HIDE_DURING_GAMBLING = List.of(
			VanillaHudElements.MISC_OVERLAYS, VanillaHudElements.CROSSHAIR, VanillaHudElements.SPECTATOR_MENU,
			VanillaHudElements.HOTBAR, VanillaHudElements.ARMOR_BAR, VanillaHudElements.HEALTH_BAR, VanillaHudElements.FOOD_BAR,
			VanillaHudElements.AIR_BAR, VanillaHudElements.MOUNT_HEALTH, VanillaHudElements.INFO_BAR,
			VanillaHudElements.EXPERIENCE_LEVEL, VanillaHudElements.HELD_ITEM_TOOLTIP, VanillaHudElements.SPECTATOR_TOOLTIP,
			VanillaHudElements.MOB_EFFECTS, VanillaHudElements.BOSS_BAR, VanillaHudElements.SCOREBOARD,
			VanillaHudElements.OVERLAY_MESSAGE, VanillaHudElements.TITLE_AND_SUBTITLE, VanillaHudElements.CHAT,
			VanillaHudElements.PLAYER_LIST, VanillaHudElements.SUBTITLES);

	@Override
	public void onInitializeClient() {
		config = ModConfig.load();
		TRACKER.setLifetime(ModConfig.loadStats());
		TRACKER.profit().setSaved(ModConfig.loadProfit());
		TRACKER.setCostMultiplier(config.costMultiplier);
		TRACKER.setListener(new Alerts());

		// --- Tastenbelegungen (Optionen > Steuerung > Kaiser's Slayers) ---
		KeyMapping.Category category = KeyMapping.Category.register(id("main"));
		menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.kaisers_slayers.menu", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		toggleHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.kaisers_slayers.toggle_hud", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		skipGamblingKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.kaisers_slayers.skip_gambling", InputConstants.Type.KEYSYM, 85 /* U */, category));

		// --- HUD ---
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("slayer_hud"), SlayerHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("profit_hud"), ProfitHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("boss_panel"), BossPanelHud::render);
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, id("alert"), AlertOverlay::render);
		HudElementRegistry.addLast(id("gambling"), GamblingAnimation::render);
		for (Identifier element : HIDE_DURING_GAMBLING) {
			try {
				HudElementRegistry.replaceElement(element, original -> (g, delta) -> {
					if (!GamblingAnimation.hidesHud()) original.extractRenderState(g, delta);
				});
			} catch (Exception e) {
				LOGGER.warn("Konnte HUD-Element {} nicht umhüllen", element, e);
			}
		}
		WorldRender.register();
		InventoryTracker.register();

		// --- Chat ---
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			long now = System.currentTimeMillis();
			String text = message.getString();
			TRACKER.onChat(text, now);
			if (config.profitCountSacks && text.contains("[Sacks]")) {
				StringBuilder hover = new StringBuilder();
				for (Component part : message.toFlatList()) {
					if (part.getStyle().getHoverEvent() instanceof HoverEvent.ShowText st) hover.append(st.value().getString()).append('\n');
				}
				Map<String, Long> items = ProfitTracker.parseSackText(hover.toString());
				if (!items.isEmpty()) TRACKER.onSackItems(items, now);
			}
		});

		// --- Wer wurde geschlagen? (Fallback für die Boss-Erkennung) ---
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide()) SCANNER.onAttack(entity);
			return InteractionResult.PASS;
		});

		ClientTickEvents.END_CLIENT_TICK.register(KaisersSlayers::onTick);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> SlayerCommands.register(dispatcher));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			saveAll();
			SCANNER.reset();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveAll());

		LOGGER.info("Kaiser's Slayers {} geladen.", version());
	}

	/** GUI erst im nächsten Tick öffnen (sonst schließt der Chat sie gleich wieder). */
	public static void openScreen(Screen screen) {
		pendingScreen = screen;
	}

	public static void openMenu() {
		openScreen(new KsScreen());
	}

	private static void onTick(Minecraft mc) {
		if (pendingScreen != null) {
			Screen s = pendingScreen;
			pendingScreen = null;
			mc.gui.setScreen(s);
		}
		while (menuKey.consumeClick()) mc.gui.setScreen(new KsScreen());
		while (toggleHudKey.consumeClick()) {
			boolean on = !config.slayerHud.enabled;
			config.slayerHud.enabled = on;
			config.profitHud.enabled = on;
			config.save();
			chat("§7HUD: " + (on ? "§aON" : "§cOFF"));
		}
		while (skipGamblingKey.consumeClick()) GamblingAnimation.skip();

		long now = System.currentTimeMillis();
		GamblingAnimation.tick(now);
		WorldRender.tickTracers(mc);
		if (mc.player == null || mc.level == null) {
			if (onSkyBlock) SCANNER.reset();
			onSkyBlock = false;
			return;
		}

		if (demo) {
			if (++tickCounter % 5 == 0) TRACKER.tick(now);
			return;
		}

		// Welt-Scanner 10x pro Sekunde (Boss, Minibosse, Slayer-Features)
		if (tickCounter % 2 == 0 && onSkyBlock) {
			try {
				SCANNER.scan(mc, TRACKER, config, now);
			} catch (Exception e) {
				LOGGER.warn("Fehler im Welt-Scanner", e);
			}
		}

		// Scoreboard 4x pro Sekunde auswerten
		if (++tickCounter % 5 != 0) return;
		onSkyBlock = ScoreboardReader.isOnSkyBlock(mc);
		List<String> lines = onSkyBlock ? ScoreboardReader.readLines(mc) : null;
		TRACKER.onScoreboard(lines, now);
		TRACKER.tick(now);
		if (onSkyBlock) PRICES.tick(now);
		checkBossTimer(now);
		checkSoonSpawn();

		if (tickCounter % 200 == 0 && TRACKER.consumeDirty()) saveAll();
	}

	/** "Boss spawnt gleich" – einmal pro Quest, wenn der Fortschritt die Schwelle erreicht. */
	private static void checkSoonSpawn() {
		if (!config.soonSpawnAlert || TRACKER.phase() != SlayerTracker.Phase.GRINDING || TRACKER.progressMax() <= 0) return;
		if (soonAlertedQuest == TRACKER.questCounter()) return;
		if (!config.feature(TRACKER.type(), SlayerFeature.TRACKING)) return;
		double pct = 100.0 * TRACKER.progress() / TRACKER.progressMax();
		if (pct >= config.soonSpawnPercent && pct < 100) {
			soonAlertedQuest = TRACKER.questCounter();
			Feedback.alert("§e§lBOSS SPAWNING SOON!", "§7" + String.format(Locale.ROOT, "%.0f%%", pct) + " quest progress", 1500,
					SoundEvents.EXPERIENCE_ORB_PICKUP, 0.7f);
		}
	}

	/** Warnung, wenn das Boss-Zeitlimit bald abläuft. */
	private static void checkBossTimer(long now) {
		if (TRACKER.phase() != SlayerTracker.Phase.BOSS_ALIVE || TRACKER.bossSpawnTime() <= 0) {
			bossWarned = false;
			return;
		}
		int standSec = SCANNER.state().found ? SCANNER.state().timeLeftSec : -1;
		long left = standSec >= 0 ? standSec * 1000L : config.bossTimeLimitSec * 1000L - (now - TRACKER.bossSpawnTime());
		if (!bossWarned && left <= config.bossTimeWarnSec * 1000L && left > 0) {
			bossWarned = true;
			if (config.bossTimeWarning) {
				Feedback.alert("§c§lTIME IS RUNNING OUT!", "§7" + (left / 1000) + " seconds left", 2000, SoundEvents.ANVIL_LAND, 1.5f);
			}
		}
	}

	/** Miniboss-Alert mit Doppel-Schutz (Chat-Nachricht und Namensschild kommen fast gleichzeitig). */
	public static void minibossAlert(String name) {
		long now = System.currentTimeMillis();
		if (now - lastMinibossAlert < 3000 || !config.minibossAlert) return;
		lastMinibossAlert = now;
		Feedback.alert("§9§lMINIBOSS!", "§b" + name, 1800, SoundEvents.PLAYER_LEVELUP, 1.2f);
	}

	public static void saveAll() {
		TRACKER.consumeDirty();
		ModConfig.saveStats(TRACKER.lifetime());
		ModConfig.saveProfit(TRACKER.profit().saved());
	}

	/** Klickbare Nachricht: öffnet per Klick den Anruf bei Maddox (/call slayer). Der Spieler muss selbst klicken. */
	public static void sendCallMaddox() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		MutableComponent button = Component.literal("§a§l[CALL MADDOX]").withStyle(st -> st
				.withClickEvent(new ClickEvent.RunCommand("/call slayer"))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("§7Click to call §5Maddox the Slayer §7with your Abiphone\n§8Runs /call slayer"))));
		mc.player.sendSystemMessage(Component.literal("§c[KS] §7Quest failed – start a new one: ").append(button));
	}

	/** Name der Taste zum Überspringen der Gambling-Animation (z. B. "U") */
	public static String skipKeyName() {
		return skipGamblingKey == null ? "U" : skipGamblingKey.getTranslatedKeyMessage().getString();
	}

	public static void chat(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§c[KS] " + msg));
	}

	/** Schreibt alle Namensschilder und besonderen Entities in der Nähe in eine Datei (zum Melden von Fehlern). */
	public static void writeDebugDump() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null) return;
		StringBuilder b = new StringBuilder();
		b.append("Kaiser's Slayers ").append(version()).append(" Debug\n");
		b.append("Phase: ").append(TRACKER.phase()).append(" Type: ").append(TRACKER.type()).append(" Tier: ").append(TRACKER.tier()).append('\n');
		b.append("Boss found: ").append(SCANNER.state().found).append(" (owner confirmed: ").append(SCANNER.state().confirmedOwner)
				.append(") Entity: ").append(SCANNER.state().entityId).append('\n');
		b.append("Scoreboard: ").append(ScoreboardReader.readLines(mc)).append("\n\nEntities within 25 blocks:\n");
		int count = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e == mc.player || e.distanceTo(mc.player) > 25) continue;
			String label = WorldScanner.labelText(e);
			String extra = "";
			if (e instanceof LivingEntity le) {
				String head = le.getItemBySlot(EquipmentSlot.HEAD).getHoverName().getString();
				String hand = le.getMainHandItem().getHoverName().getString();
				if (!le.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) extra += " head=" + head;
				if (!le.getMainHandItem().isEmpty()) extra += " hand=" + hand;
			}
			if (e instanceof EnderMan em && em.getCarriedBlock() != null) extra += " carries=" + em.getCarriedBlock();
			if (e.getVehicle() != null) extra += " rides=" + WorldScanner.typeId(e.getVehicle());
			if (label == null && extra.isEmpty() && WorldScanner.typeId(e).equals("armor_stand")) continue;
			b.append(String.format(Locale.ROOT, "%-14s #%-6d %6.1f %6.1f %6.1f  %s%s%n", WorldScanner.typeId(e), e.getId(),
					e.getX(), e.getY(), e.getZ(), label == null ? "" : "\"" + label + "\"", extra));
			count++;
		}
		try {
			Files.writeString(ModConfig.debugFile(), b.toString(), StandardCharsets.UTF_8);
			chat("§aDebug file written (" + count + " entities): §fconfig/kaisers_slayers_debug.txt");
		} catch (Exception e) {
			chat("§cCould not write the debug file: " + e.getMessage());
		}
	}

	public static String version() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
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

	/** "/ks test": HUDs mit Beispieldaten auch außerhalb von SkyBlock zeigen */
	public static boolean toggleForceHud() {
		forceHud = !forceHud;
		demo = forceHud;
		if (demo) DemoData.load();
		else {
			TRACKER.resetSession();
			SCANNER.reset();
		}
		return forceHud;
	}

	public static boolean isDemo() {
		return demo;
	}

	/** Reagiert auf Ereignisse des Trackers mit Einblendungen, Sounds und Chat-Nachrichten. */
	private static final class Alerts implements SlayerTracker.Listener {
		@Override
		public void onBossSpawned(SlayerType type, int tier, long spawnMs) {
			if (!config.feature(type, SlayerFeature.TRACKING)) return;
			if (config.spawnAlert) {
				Feedback.alert("§c§lBOSS SPAWNED!", type.color + type.bossName + " " + TextUtil.toRoman(tier), 2000, SoundEvents.PLAYER_LEVELUP, 0.6f);
			}
			if (config.questTimeMessage && spawnMs > 0) chat("§7Boss spawned in §f" + TextUtil.formatSeconds(spawnMs));
		}

		@Override
		public void onBossKilled(SlayerType type, int tier, long killMs, boolean newBest) {
			GamblingAnimation.onKill(type, tier, System.currentTimeMillis());
			if (!config.feature(type, SlayerFeature.TRACKING)) return;
			if (config.killMessage) {
				String time = killMs > 0 ? TextUtil.formatSeconds(killMs) : "?";
				chat(type.color + type.bossName + " " + TextUtil.toRoman(tier) + " §7slain in §f" + time
						+ (newBest ? " §a§lNEW PB!" : "") + " §8(Session: " + TRACKER.session().kills + ")");
			}
			if (config.questTimeMessage && TRACKER.questStartKnown()) {
				chat("§7Quest completed in §f" + TextUtil.formatDuration(System.currentTimeMillis() - TRACKER.questStart()));
			}
			if (!GamblingAnimation.isActiveOrPending()) Feedback.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f);
		}

		@Override
		public void onQuestFailed(SlayerType type, int tier) {
			if (config.failAlert) Feedback.alert("§4§lQUEST FAILED", "§7Your Slayer Quest failed", 2500, SoundEvents.ANVIL_LAND, 0.5f);
			// Maddox (Abiphone) ist im Rift nicht erreichbar
			if (config.callMaddoxMessage && type != SlayerType.VAMPIRE) sendCallMaddox();
		}

		@Override
		public void onRareDrop(String rarity, String item, int count, boolean big) {
			if (!big || GamblingAnimation.isActiveOrPending()) return; // kein Spoiler – die Animation zeigt den Drop
			if (config.dropAlert) {
				Feedback.alert("§6§l" + rarity + "!", "§f" + (count > 1 ? count + "x " : "") + item, 4000, SoundEvents.PLAYER_LEVELUP, 1.4f);
			}
		}

		@Override
		public void onMinibossMessage(String message) {
			String name = message.replaceFirst("^SLAYER MINI-?BOSS\\s*", "").replaceFirst("\\s*HAS SPAWNED!?$", "");
			minibossAlert(name.isBlank() ? "Miniboss" : name);
		}

		@Override
		public void onCocoon() {
			SCANNER.state().cocoonAt = System.currentTimeMillis();
			if (config.feature(SlayerType.SPIDER, SlayerFeature.SPIDER_COCOON_ALERT)) {
				Feedback.alert("§f§lCOCOONED!", "§7You cocooned your boss", 1500, SoundEvents.PLAYER_LEVELUP, 1.6f);
			}
		}

		@Override
		public void onLevelUp(SlayerType type, int level) {
			Feedback.alert("§a§lLEVEL UP!", type.color + type.levelName + " Slayer §fLVL " + level, 3000, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f);
		}
	}
}
