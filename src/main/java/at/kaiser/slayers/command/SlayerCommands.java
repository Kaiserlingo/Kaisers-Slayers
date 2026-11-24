package at.kaiser.slayers.command;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.config.ModConfig;
import at.kaiser.slayers.hud.AlertOverlay;
import at.kaiser.slayers.hud.SlayerHud;
import at.kaiser.slayers.slayer.LifetimeStats;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Alle Befehle: /kslayers (Kurzform /ks) */
public final class SlayerCommands {
	private SlayerCommands() {}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(build("kslayers"));
		dispatcher.register(build("ks"));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
		return ClientCommands.literal(name)
				.executes(ctx -> {
					KaisersSlayers.openMenu();
					return 1;
				})
				.then(ClientCommands.literal("menu").executes(ctx -> {
					KaisersSlayers.openMenu();
					return 1;
				}))
				.then(ClientCommands.literal("help").executes(SlayerCommands::help))
				.then(ClientCommands.literal("stats").executes(SlayerCommands::stats))
				.then(ClientCommands.literal("lifetime").executes(SlayerCommands::lifetime))
				.then(ClientCommands.literal("reset").executes(ctx -> {
					KaisersSlayers.tracker().resetSession();
					return ok(ctx, "§aSession-Statistik zurückgesetzt.");
				}))
				.then(ClientCommands.literal("hud")
						.executes(ctx -> toggle(ctx, "HUD", c -> c.hudEnabled, (c, v) -> c.hudEnabled = v))
						.then(ClientCommands.literal("pos")
								.then(ClientCommands.argument("x", IntegerArgumentType.integer(0, 4000))
										.then(ClientCommands.argument("y", IntegerArgumentType.integer(0, 4000))
												.executes(ctx -> {
													ModConfig c = KaisersSlayers.config();
													c.hudX = IntegerArgumentType.getInteger(ctx, "x");
													c.hudY = IntegerArgumentType.getInteger(ctx, "y");
													c.save();
													return ok(ctx, "§aHUD-Position: §f" + c.hudX + ", " + c.hudY);
												}))))
						.then(ClientCommands.literal("scale")
								.then(ClientCommands.argument("faktor", FloatArgumentType.floatArg(0.5f, 3.0f))
										.executes(ctx -> {
											ModConfig c = KaisersSlayers.config();
											c.hudScale = FloatArgumentType.getFloat(ctx, "faktor");
											c.save();
											return ok(ctx, "§aHUD-Größe: §f" + c.hudScale);
										})))
						.then(ClientCommands.literal("background")
								.executes(ctx -> toggle(ctx, "HUD-Hintergrund", c -> c.hudBackground, (c, v) -> c.hudBackground = v)))
						.then(ClientCommands.literal("onlyactive")
								.executes(ctx -> toggle(ctx, "HUD nur bei aktivem Quest", c -> c.hudOnlyWhenActive, (c, v) -> c.hudOnlyWhenActive = v))))
				.then(ClientCommands.literal("toggle")
						.then(ClientCommands.literal("profit").executes(ctx -> toggle(ctx, "Profit-Anzeige", c -> c.showProfit, (c, v) -> c.showProfit = v)))
						.then(ClientCommands.literal("drops").executes(ctx -> toggle(ctx, "Drop-Liste", c -> c.showDrops, (c, v) -> c.showDrops = v)))
						.then(ClientCommands.literal("eta").executes(ctx -> toggle(ctx, "Level-Prognose", c -> c.showLevelEta, (c, v) -> c.showLevelEta = v)))
						.then(ClientCommands.literal("spawnalert").executes(ctx -> toggle(ctx, "Boss-Spawn-Alert", c -> c.spawnAlert, (c, v) -> c.spawnAlert = v)))
						.then(ClientCommands.literal("dropalert").executes(ctx -> toggle(ctx, "Drop-Alert", c -> c.dropAlert, (c, v) -> c.dropAlert = v)))
						.then(ClientCommands.literal("failalert").executes(ctx -> toggle(ctx, "Quest-Failed-Alert", c -> c.failAlert, (c, v) -> c.failAlert = v)))
						.then(ClientCommands.literal("killmsg").executes(ctx -> toggle(ctx, "Kill-Zeit im Chat", c -> c.killMessage, (c, v) -> c.killMessage = v)))
						.then(ClientCommands.literal("sounds").executes(ctx -> toggle(ctx, "Sounds", c -> c.sounds, (c, v) -> c.sounds = v)))
						.then(ClientCommands.literal("gambling").executes(ctx -> toggle(ctx, "Gambling-Animation", c -> c.gamblingEnabled, (c, v) -> c.gamblingEnabled = v))))
				.then(ClientCommands.literal("timelimit")
						.then(ClientCommands.argument("sekunden", IntegerArgumentType.integer(30, 600))
								.executes(ctx -> {
									ModConfig c = KaisersSlayers.config();
									c.bossTimeLimitSec = IntegerArgumentType.getInteger(ctx, "sekunden");
									c.save();
									return ok(ctx, "§aBoss-Zeitlimit: §f" + c.bossTimeLimitSec + "s");
								})))
				.then(ClientCommands.literal("prices")
						.executes(ctx -> {
							KaisersSlayers.prices().refresh();
							return ok(ctx, "§aBazaar-Preise werden neu geladen...");
						})
						.then(ClientCommands.literal("mode")
								.executes(ctx -> {
									ModConfig c = KaisersSlayers.config();
									c.instantSellPrices = !c.instantSellPrices;
									c.save();
									return ok(ctx, "§aPreis-Modus: §f" + (c.instantSellPrices ? "Instant-Sell" : "Sell-Offer"));
								})))
				.then(ClientCommands.literal("test")
						.executes(ctx -> {
							boolean on = KaisersSlayers.toggleForceHud();
							AlertOverlay.show("§c§lBOSS SPAWNED!", "§7Testanzeige von Kaiser's Slayers", 2500);
							return ok(ctx, on ? "§aTestmodus an: HUD wird überall angezeigt." : "§7Testmodus aus.");
						}));
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		String[] lines = {
				"§c§lKaiser's Slayers §7– Befehle (§f/ks§7 geht auch):",
				"§e/ks §7– Menü öffnen (alle Einstellungen)",
				"§e/ks stats §7– Session-Statistik",
				"§e/ks lifetime §7– Gesamtstatistik aller Slayer",
				"§e/ks reset §7– Session zurücksetzen",
				"§e/ks hud §7– HUD ein/aus",
				"§e/ks hud pos <x> <y> §7– HUD verschieben",
				"§e/ks hud scale <0.5-3> §7– HUD-Größe",
				"§e/ks hud background §7| §eonlyactive",
				"§e/ks toggle <profit|drops|eta|spawnalert|dropalert|failalert|killmsg|sounds|gambling>",
				"§e/ks timelimit <sek> §7– Boss-Zeitlimit für Countdown",
				"§e/ks prices §7– Preise neu laden §8| §e/ks prices mode §7– Instant-Sell/Sell-Offer",
				"§e/ks test §7– HUD außerhalb von SkyBlock testen",
		};
		for (String l : lines) ctx.getSource().sendFeedback(Component.literal(l));
		return 1;
	}

	private static int stats(CommandContext<FabricClientCommandSource> ctx) {
		for (String l : SlayerHud.buildLines(KaisersSlayers.tracker(), KaisersSlayers.config(), KaisersSlayers.prices(), System.currentTimeMillis())) {
			ctx.getSource().sendFeedback(Component.literal(l));
		}
		return 1;
	}

	private static int lifetime(CommandContext<FabricClientCommandSource> ctx) {
		ctx.getSource().sendFeedback(Component.literal("§c§lKaiser's Slayers §7– Gesamtstatistik"));
		LifetimeStats stats = KaisersSlayers.tracker().lifetime();
		boolean any = false;
		for (SlayerType type : SlayerType.values()) {
			LifetimeStats.Entry e = stats.slayers.get(type.key());
			if (e == null || (e.kills == 0 && e.level < 0)) continue;
			any = true;
			StringBuilder sb = new StringBuilder();
			sb.append(type.color).append(type.levelName).append(" Slayer");
			if (e.level >= 0) sb.append(" §7LVL ").append(e.level);
			sb.append(" §8| §f").append(e.kills).append(" Bosse");
			if (e.timedKills > 0) {
				sb.append(" §8| §7Ø ").append(TextUtil.formatSeconds(e.averageKillMs()))
						.append(" §7Best ").append(TextUtil.formatSeconds(e.bestKillMs));
			}
			if (e.fails > 0) sb.append(" §8| §c").append(e.fails).append(" failed");
			ctx.getSource().sendFeedback(Component.literal(sb.toString()));
			int n = 0;
			for (Map.Entry<String, Integer> d : e.drops.entrySet()) {
				if (n++ >= 8) break;
				ctx.getSource().sendFeedback(Component.literal("§8   • §f" + d.getValue() + "x " + d.getKey()));
			}
		}
		if (!any) ctx.getSource().sendFeedback(Component.literal("§7Noch keine Slayer-Daten – geh Bosse killen!"));
		return 1;
	}

	private static int toggle(CommandContext<FabricClientCommandSource> ctx, String name,
							  Function<ModConfig, Boolean> getter, BiConsumer<ModConfig, Boolean> setter) {
		ModConfig c = KaisersSlayers.config();
		boolean v = !getter.apply(c);
		setter.accept(c, v);
		c.save();
		return ok(ctx, "§7" + name + ": " + (v ? "§aAN" : "§cAUS"));
	}

	private static int ok(CommandContext<FabricClientCommandSource> ctx, String msg) {
		ctx.getSource().sendFeedback(Component.literal("§c[KS] " + msg));
		return 1;
	}
}
