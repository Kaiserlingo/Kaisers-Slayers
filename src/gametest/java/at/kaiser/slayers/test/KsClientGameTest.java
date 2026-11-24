package at.kaiser.slayers.test;

import at.kaiser.slayers.KaisersSlayers;
import at.kaiser.slayers.gambling.GamblingAnimation;
import at.kaiser.slayers.gui.HudEditorScreen;
import at.kaiser.slayers.gui.KsScreen;
import at.kaiser.slayers.hud.AlertOverlay;
import at.kaiser.slayers.slayer.SlayerType;
import at.kaiser.slayers.world.BossState;
import at.kaiser.slayers.world.Highlights;
import at.kaiser.slayers.world.WorldBeams;
import at.kaiser.slayers.world.WorldScanner;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Startet das Spiel, lädt eine Welt und macht Screenshots von HUD, Menü, Strahlen, Nametag und Gambling. */
@SuppressWarnings("UnstableApiUsage")
public class KsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext ctx) {
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientLevel().waitForChunksRender();
			sp.getServer().runCommand("time set day");
			sp.getServer().runCommand("gamerule doDaylightCycle false");
			sp.getServer().runCommand("execute as @p at @s run summon zombie ^ ^ ^5 {NoAI:1b,PersistenceRequired:1b}");
			ctx.waitTicks(20);

			// Demo-Daten, Strahl, Glow und Nametag am Zombie
			ctx.runOnClient(mc -> {
				KaisersSlayers.toggleForceHud();
				Entity zombie = null;
				for (Entity e : mc.level.entitiesForRendering()) {
					if (WorldScanner.typeId(e).equals("zombie")) zombie = e;
				}
				if (zombie != null) {
					BossState st = KaisersSlayers.scanner().state();
					st.entityId = zombie.getId();
					Highlights.set(Map.of(zombie.getId(), 0xAA00FF));
					WorldBeams.set(List.of(new WorldBeams.Beam((int) zombie.getX() + 3, (int) zombie.getY(), (int) zombie.getZ() + 2, 0x55FFFF)));
				}
				AlertOverlay.show("§c§lBOSS SPAWNED!", "§5Voidgloom Seraph IV", 60_000);
			});
			ctx.waitTicks(10);
			ctx.takeScreenshot("01_hud_world");
			ctx.runOnClient(mc -> AlertOverlay.show("", "", 1));

			// Menü
			ctx.setScreen(KsScreen::new);
			ctx.waitTicks(10);
			ctx.takeScreenshot("02_menu_overview");
			int[][] pages = {{1, 3}, {4, 4}, {6, 6}, {10, 10}, {11, 11}};
			String[] names = {"03_menu_alerts", "04_menu_profit", "05_menu_gambling", "06_menu_voidgloom", "07_menu_blaze"};
			for (int i = 0; i < pages.length; i++) {
				int cat = pages[i][0];
				ctx.runOnClient(mc -> ((KsScreen) mc.gui.screen()).selectCategory(cat));
				ctx.waitTicks(5);
				ctx.takeScreenshot(names[i]);
			}
			ctx.setScreen(() -> new HudEditorScreen(null));
			ctx.waitTicks(5);
			ctx.takeScreenshot("08_hud_editor");
			ctx.setScreen(() -> null);
			ctx.waitTicks(5);

			// Gambling mit kontrollierter Uhr
			AtomicLong clock = new AtomicLong(System.currentTimeMillis());
			GamblingAnimation.clock = clock::get;
			String[] styles = {"CASE", "SLOTS", "WHEEL", "CARDS"};
			for (String style : styles) {
				ctx.runOnClient(mc -> GamblingAnimation.preview(SlayerType.ENDERMAN, style));
				long start = clock.get();
				clock.set(start + 2400);
				ctx.waitTicks(3);
				ctx.takeScreenshot("09_gamble_" + style.toLowerCase() + "_spin");
				clock.set(start + KaisersSlayers.config().gamblingDurationMs + 1200);
				ctx.waitTicks(3);
				ctx.takeScreenshot("10_gamble_" + style.toLowerCase() + "_reveal");
				ctx.runOnClient(mc -> GamblingAnimation.skip());
				clock.set(start + 60_000);
				ctx.waitTicks(2);
			}
			GamblingAnimation.clock = System::currentTimeMillis;
		}
	}
}
