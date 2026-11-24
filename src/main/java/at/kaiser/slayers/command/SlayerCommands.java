package at.kaiser.slayers.command;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gui.HudEditorScreen;
import at.kaiser.slayers.slayer.LifetimeStats;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.util.TextUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** Befehle: /ks (Menü) und ein paar Abkürzungen. /kslayers geht auch. */
public final class SlayerCommands {
	private SlayerCommands() {}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(build("ks"));
		dispatcher.register(build("kslayers"));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
		LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(name)
				.executes(ctx -> {
					KaisersSlayers.openMenu();
					return 1;
				})
				.then(ClientCommands.literal("help").executes(SlayerCommands::help))
				.then(ClientCommands.literal("hud").executes(ctx -> {
					KaisersSlayers.openScreen(new HudEditorScreen(null));
					return 1;
				}))
				.then(ClientCommands.literal("lifetime").executes(SlayerCommands::lifetime))
				.then(ClientCommands.literal("reset").executes(ctx -> {
					KaisersSlayers.tracker().resetSession();
					return ok(ctx, "§aSession zurückgesetzt.");
				}))
				.then(ClientCommands.literal("profit").executes(ctx -> {
					var cfg = KaisersSlayers.config();
					cfg.profitMode = "TOTAL".equals(cfg.profitMode) ? "SESSION" : "TOTAL";
					cfg.save();
					return ok(ctx, "§7Profit-Anzeige: §f" + ("TOTAL".equals(cfg.profitMode) ? "Gesamt" : "Session"));
				}))
				.then(ClientCommands.literal("prices").executes(ctx -> {
					KaisersSlayers.prices().refresh();
					return ok(ctx, "§aBazaar-Preise werden neu geladen...");
				}))
				.then(ClientCommands.literal("debug").executes(ctx -> {
					KaisersSlayers.writeDebugDump();
					return 1;
				}))
				.then(ClientCommands.literal("test").executes(ctx -> {
					boolean on = KaisersSlayers.toggleForceHud();
					return ok(ctx, on ? "§aTestmodus an: HUDs werden überall angezeigt." : "§7Testmodus aus.");
				}));
		LiteralArgumentBuilder<FabricClientCommandSource> gamble = ClientCommands.literal("gamble");
		for (String style : new String[]{"case", "slots", "wheel", "cards"}) {
			gamble.then(ClientCommands.literal(style).executes(ctx -> {
				SlayerType t = KaisersSlayers.tracker().type() != null ? KaisersSlayers.tracker().type() : SlayerType.ZOMBIE;
				String old = KaisersSlayers.config().gamblingStyle;
				GamblingAnimation.preview(t, style.toUpperCase(java.util.Locale.ROOT));
				KaisersSlayers.config().gamblingStyle = old;
				return 1;
			}));
		}
		return root.then(gamble);
	}

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		String[] lines = {
				"§c§lKaiser's Slayers §7– Befehle",
				"§e/ks §7– Menü öffnen (alle Einstellungen)",
				"§e/ks hud §7– HUD-Editor (verschieben/skalieren)",
				"§e/ks profit §7– Profit: Session ↔ Gesamt",
				"§e/ks lifetime §7– Gesamtstatistik aller Slayer",
				"§e/ks reset §7– Session zurücksetzen",
				"§e/ks gamble <case|slots|wheel|cards> §7– Animation testen",
				"§e/ks prices §7– Bazaar-Preise neu laden",
				"§e/ks debug §7– Debug-Datei für Fehlerberichte",
				"§e/ks test §7– HUDs außerhalb von SkyBlock zeigen",
		};
		for (String l : lines) ctx.getSource().sendFeedback(Component.literal(l));
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
				sb.append(" §8| §7Ø ").append(TextUtil.formatSeconds(e.averageKillMs())).append(" §7Best ").append(TextUtil.formatSeconds(e.bestKillMs));
			}
			if (e.fails > 0) sb.append(" §8| §c").append(e.fails).append(" failed");
			ctx.getSource().sendFeedback(Component.literal(sb.toString()));
		}
		if (!any) ctx.getSource().sendFeedback(Component.literal("§7Noch keine Slayer-Daten – geh Bosse killen!"));
		return 1;
	}

	private static int ok(CommandContext<FabricClientCommandSource> ctx, String msg) {
		ctx.getSource().sendFeedback(Component.literal("§c[KS] " + msg));
		return 1;
	}
}
