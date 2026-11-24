package at.kaiser.slayers.gambling;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Ordnet SkyBlock-Items einem passenden Minecraft-Item als Icon zu (nur Optik). */
public final class ItemIcons {
	private static final Map<String, Item> KEYWORDS = new LinkedHashMap<>();

	static {
		KEYWORDS.put("warden heart", Items.HEART_OF_THE_SEA);
		KEYWORDS.put("flesh", Items.ROTTEN_FLESH);
		KEYWORDS.put("catalyst", Items.PRISMARINE_CRYSTALS);
		KEYWORDS.put("scythe", Items.IRON_HOE);
		KEYWORDS.put("shard", Items.AMETHYST_SHARD);
		KEYWORDS.put("horror", Items.ZOMBIE_HEAD);
		KEYWORDS.put("rune", Items.MAGMA_CREAM);
		KEYWORDS.put("web", Items.COBWEB);
		KEYWORDS.put("arrow", Items.ARROW);
		KEYWORDS.put("poison", Items.SPIDER_EYE);
		KEYWORDS.put("eye", Items.ENDER_EYE);
		KEYWORDS.put("swatter", Items.WOODEN_SHOVEL);
		KEYWORDS.put("talisman", Items.SPIDER_EYE);
		KEYWORDS.put("mosquito", Items.FERMENTED_SPIDER_EYE);
		KEYWORDS.put("wasp", Items.FERMENTED_SPIDER_EYE);
		KEYWORDS.put("snail", Items.SLIME_BALL);
		KEYWORDS.put("tooth", Items.GHAST_TEAR);
		KEYWORDS.put("wheel", Items.CLOCK);
		KEYWORDS.put("bait", Items.SALMON);
		KEYWORDS.put("egg", Items.EGG);
		KEYWORDS.put("capacitor", Items.REDSTONE_BLOCK);
		KEYWORDS.put("null sphere", Items.FIREWORK_STAR);
		KEYWORDS.put("null atom", Items.ENDER_PEARL);
		KEYWORDS.put("tuner", Items.COMPASS);
		KEYWORDS.put("hazmat", Items.LEATHER_HELMET);
		KEYWORDS.put("espresso", Items.SUGAR);
		KEYWORDS.put("chalice", Items.GOLD_INGOT);
		KEYWORDS.put("dice", Items.SKELETON_SKULL);
		KEYWORDS.put("merger", Items.ENDER_EYE);
		KEYWORDS.put("judgement", Items.NETHER_STAR);
		KEYWORDS.put("upgrader", Items.END_CRYSTAL);
		KEYWORDS.put("ashe", Items.GUNPOWDER);
		KEYWORDS.put("blaze powder", Items.BLAZE_POWDER);
		KEYWORDS.put("magma", Items.MAGMA_CREAM);
		KEYWORDS.put("disintegrator", Items.LAPIS_LAZULI);
		KEYWORDS.put("scorched", Items.BOOK);
		KEYWORDS.put("inverter", Items.PACKED_ICE);
		KEYWORDS.put("distillate", Items.POTION);
		KEYWORDS.put("water", Items.POTION);
		KEYWORDS.put("burger", Items.COOKED_BEEF);
		KEYWORDS.put("seal", Items.NETHER_BRICK);
		KEYWORDS.put("vampire part", Items.REDSTONE);
		KEYWORDS.put("motes", Items.PRISMARINE_SHARD);
	}

	private ItemIcons() {}

	public static ItemStack of(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.matches(".* (i|ii|iii|iv|v|vi|vii|viii|ix|x)$")) return new ItemStack(Items.ENCHANTED_BOOK);
		for (Map.Entry<String, Item> e : KEYWORDS.entrySet()) {
			if (n.contains(e.getKey())) return new ItemStack(e.getValue());
		}
		return new ItemStack(Items.PAPER);
	}
}
