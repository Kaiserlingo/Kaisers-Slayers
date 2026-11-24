package at.kaiser.slayers.command;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.carry.CarryManager;
import at.kaiser.slayers.util.TextUtil;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * /ks carry … – Carry-Kunden verwalten.
 * add &lt;Spieler&gt; &lt;Anzahl&gt; [Preis pro Carry], remove, set, +, -, price, paid, list, clearfinished, clear
 */
public final class CarryCommands {
	private CarryCommands() {}

	/** Vorschläge: Spieler aus der Tab-Liste + bestehende Kunden */
	private static final SuggestionProvider<FabricClientCommandSource> PLAYERS = (ctx, b) -> {
		String rem = b.getRemaining().toLowerCase(Locale.ROOT);
		for (CarryManager.Client c : KaisersSlayers.carries().clients()) {
			if (c.name.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(c.name);
		}
		var conn = Minecraft.getInstance().getConnection();
		if (conn != null) {
			for (PlayerInfo p : conn.getOnlinePlayers()) {
				String n = p.getProfile().name();
				if (CarryManager.validName(n) && n.length() >= 3 && n.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(n);
			}
		}
		return b.buildFuture();
	};

	private static final SuggestionProvider<FabricClientCommandSource> CLIENTS = (ctx, b) -> {
		String rem = b.getRemaining().toLowerCase(Locale.ROOT);
		for (CarryManager.Client c : KaisersSlayers.carries().clients()) {
			if (c.name.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(c.name);
		}
		return b.buildFuture();
	};

	private static RequiredArgumentBuilder<FabricClientCommandSource, String> player(SuggestionProvider<FabricClientCommandSource> s) {
		return ClientCommands.argument("player", StringArgumentType.word()).suggests(s);
	}

	public static LiteralArgumentBuilder<FabricClientCommandSource> build() {
		return ClientCommands.literal("carry")
				.executes(CarryCommands::list)
				.then(ClientCommands.literal("list").executes(CarryCommands::list))
				.then(ClientCommands.literal("add").then(player(PLAYERS)
						.then(ClientCommands.argument("amount", IntegerArgumentType.integer(1, 1000))
								.executes(ctx -> add(ctx, null))
								.then(ClientCommands.argument("price", StringArgumentType.word())
										.executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "price")))))))
				.then(ClientCommands.literal("remove").then(player(CLIENTS).executes(ctx -> {
					String n = name(ctx);
					boolean ok = KaisersSlayers.carries().remove(n);
					KaisersSlayers.saveCarries();
					return msg(ctx, ok ? "§7Removed §f" + n + "§7." : "§c" + n + " is not a client.");
				})))
				.then(ClientCommands.literal("set").then(player(CLIENTS)
						.then(ClientCommands.argument("done", IntegerArgumentType.integer(0, 1000)).executes(ctx -> {
							CarryManager.Client c = KaisersSlayers.carries().setDone(name(ctx), IntegerArgumentType.getInteger(ctx, "done"));
							return progress(ctx, c);
						}))))
				.then(ClientCommands.literal("+").then(player(CLIENTS).executes(ctx -> progress(ctx, KaisersSlayers.carries().adjust(name(ctx), 1)))))
				.then(ClientCommands.literal("-").then(player(CLIENTS).executes(ctx -> progress(ctx, KaisersSlayers.carries().adjust(name(ctx), -1)))))
				.then(ClientCommands.literal("price").then(player(CLIENTS)
						.then(ClientCommands.argument("coins", StringArgumentType.word()).executes(ctx -> {
							long coins = coins(StringArgumentType.getString(ctx, "coins"));
							CarryManager.Client c = KaisersSlayers.carries().setPrice(name(ctx), coins);
							KaisersSlayers.saveCarries();
							if (c == null) return msg(ctx, "§c" + name(ctx) + " is not a client.");
							return msg(ctx, "§7Price for §f" + c.name + "§7: §6" + TextUtil.formatCoins(c.price) + " §7per carry §8(total "
									+ TextUtil.formatCoins(c.price * c.total) + ")");
						}))))
				.then(ClientCommands.literal("paid").then(player(CLIENTS)
						.then(ClientCommands.argument("coins", StringArgumentType.word()).executes(ctx -> {
							long coins = coins(StringArgumentType.getString(ctx, "coins"));
							CarryManager.Client c = KaisersSlayers.carries().addPaid(name(ctx), coins);
							KaisersSlayers.saveCarries();
							if (c == null) return msg(ctx, "§c" + name(ctx) + " is not a client.");
							return msg(ctx, "§f" + c.name + " §7paid §6" + TextUtil.formatCoins(coins)
									+ (c.price > 0 ? (c.due() > 0 ? " §8· §7still due §6" + TextUtil.formatCoins(c.due()) : " §8· §a✔ fully paid") : ""));
						}))))
				.then(ClientCommands.literal("clearfinished").executes(ctx -> {
					int n = KaisersSlayers.carries().clearFinished();
					KaisersSlayers.saveCarries();
					return msg(ctx, "§7Removed §f" + n + " §7finished client(s).");
				}))
				.then(ClientCommands.literal("clear").executes(ctx -> {
					KaisersSlayers.carries().clear();
					KaisersSlayers.saveCarries();
					return msg(ctx, "§7All carry clients removed.");
				}));
	}

	private static int add(CommandContext<FabricClientCommandSource> ctx, String price) {
		String n = name(ctx);
		if (!CarryManager.validName(n)) return msg(ctx, "§cInvalid player name: " + n);
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		long p = price != null ? coins(price) : 0;
		CarryManager.Client c = KaisersSlayers.carries().add(n, amount, p, System.currentTimeMillis());
		KaisersSlayers.saveCarries();
		return msg(ctx, "§d[Carry] §7Added §f" + c.name + " §7– §e" + c.done + "/" + c.total
				+ (c.price > 0 ? " §8· §6" + TextUtil.formatCoins(c.price) + "§7/carry" : ""));
	}

	private static int list(CommandContext<FabricClientCommandSource> ctx) {
		var mgr = KaisersSlayers.carries();
		if (mgr.isEmpty()) {
			msg(ctx, "§7No carry clients. Add one with §e/ks carry add <player> <amount> [price]");
			return 1;
		}
		msg(ctx, "§d§lCarries §7– " + mgr.totalLeft() + " left" + (mgr.totalDue() > 0 ? " · §6" + TextUtil.formatCoins(mgr.totalDue()) + " §7due" : ""));
		for (CarryManager.Client c : mgr.clients()) {
			String line = (c.finished() ? " §a✔ " : " §7• ") + "§f" + c.name + " §e" + c.done + "/" + c.total
					+ (c.averageKillMs() > 0 ? " §8· Ø " + TextUtil.formatSeconds(c.averageKillMs()) : "")
					+ (c.price > 0 ? (c.due() > 0 ? " §8· §6" + TextUtil.formatCoins(c.due()) + " due" : " §8· §apaid") : "");
			ctx.getSource().sendFeedback(Component.literal(line));
		}
		return 1;
	}

	private static int progress(CommandContext<FabricClientCommandSource> ctx, CarryManager.Client c) {
		KaisersSlayers.saveCarries();
		if (c == null) return msg(ctx, "§c" + name(ctx) + " is not a client.");
		return msg(ctx, "§d[Carry] §f" + c.name + " §7– §e" + c.done + "/" + c.total);
	}

	private static String name(CommandContext<FabricClientCommandSource> ctx) {
		return StringArgumentType.getString(ctx, "player");
	}

	/** "5m", "2.5M", "500k", "1,000,000" → Coins */
	static long coins(String s) {
		try {
			return Math.max(0, Math.round(TextUtil.parseNumber(s)));
		} catch (Exception e) {
			return 0;
		}
	}

	private static int msg(CommandContext<FabricClientCommandSource> ctx, String m) {
		ctx.getSource().sendFeedback(Component.literal("§c[KS] " + m));
		return 1;
	}
}
