package dev.enderterminal;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Builds a plain-text snapshot of the player's situation to attach to messages. Call on the render thread. */
public final class GameContext {
	public static final String SYSTEM_NOTE = "Messages may start with a [Game state] block that the mod attaches automatically. "
			+ "It describes the player's current situation in Minecraft. Use it when it helps answer, but don't repeat it back unprompted.";

	private static final int NEARBY_RADIUS = 24;

	private GameContext() {
	}

	/** Returns null when the player isn't in a world. */
	public static String snapshot(boolean includeMods) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		Level level = mc.level;
		if (p == null || level == null) return null;

		StringBuilder sb = new StringBuilder("[Game state]\n");
		sb.append("Player name: ").append(p.getName().getString()).append('\n');
		BlockPos pos = p.blockPosition();
		sb.append("Position: x=").append(pos.getX()).append(" y=").append(pos.getY()).append(" z=").append(pos.getZ())
				.append(", facing ").append(p.getDirection().getName()).append('\n');
		sb.append("Dimension: ").append(level.dimension().identifier()).append('\n');
		level.getBiome(pos).unwrapKey().ifPresent(k -> sb.append("Biome: ").append(k.identifier()).append('\n'));

		long clock = level.getOverworldClockTime();
		long dayTicks = Math.floorMod(clock, 24000L);
		int hour = (int) ((dayTicks / 1000 + 6) % 24);
		int minute = (int) (dayTicks % 1000 * 60 / 1000);
		sb.append(String.format(Locale.ROOT, "Time: day %d, %02d:%02d (%s)", clock / 24000 + 1, hour, minute,
				level.isDarkOutside() ? "night" : "day"));
		sb.append(level.isThundering() ? ", thunderstorm" : level.isRaining() ? ", raining" : ", clear").append('\n');

		if (mc.gameMode != null) sb.append("Game mode: ").append(mc.gameMode.getPlayerMode().getSerializedName());
		sb.append(", difficulty: ").append(level.getDifficulty().getSerializedName()).append('\n');
		sb.append(String.format(Locale.ROOT, "Health: %.0f/%.0f, food: %d/20, armor: %d, XP level: %d%n",
				p.getHealth(), p.getMaxHealth(), p.getFoodData().getFoodLevel(), p.getArmorValue(), p.experienceLevel));

		sb.append("Main hand: ").append(describe(p.getMainHandItem())).append('\n');
		if (!p.getOffhandItem().isEmpty()) sb.append("Off hand: ").append(describe(p.getOffhandItem())).append('\n');
		List<String> armor = new ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack s = p.getItemBySlot(slot);
			if (!s.isEmpty()) armor.add(describe(s));
		}
		sb.append("Armor: ").append(armor.isEmpty() ? "none" : String.join("; ", armor)).append('\n');

		sb.append("Inventory: ").append(inventory(p.getInventory())).append('\n');
		sb.append("Looking at: ").append(lookingAt(mc, level)).append('\n');
		sb.append("Nearby (").append(NEARBY_RADIUS).append(" blocks): ").append(nearby(p, level)).append('\n');
		if (includeMods) sb.append("Installed mods: ").append(mods()).append('\n');
		sb.append("[End game state]");
		return sb.toString();
	}

	private static String describe(ItemStack stack) {
		if (stack.isEmpty()) return "empty";
		StringBuilder sb = new StringBuilder();
		if (stack.getCount() > 1) sb.append(stack.getCount()).append("x ");
		sb.append(stack.getHoverName().getString());
		List<String> enchants = new ArrayList<>();
		for (Object2IntMap.Entry<Holder<Enchantment>> e : stack.getEnchantments().entrySet()) {
			enchants.add(Enchantment.getFullname(e.getKey(), e.getIntValue()).getString());
		}
		if (!enchants.isEmpty()) sb.append(" [").append(String.join(", ", enchants)).append(']');
		if (stack.isDamageableItem()) {
			sb.append(" (durability ").append(stack.getMaxDamage() - stack.getDamageValue()).append('/').append(stack.getMaxDamage()).append(')');
		}
		return sb.toString();
	}

	private static String inventory(Inventory inv) {
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			String name = s.getHoverName().getString();
			if (!s.getEnchantments().isEmpty()) name += " (enchanted)";
			counts.merge(name, s.getCount(), Integer::sum);
		}
		if (counts.isEmpty()) return "empty";
		List<String> parts = new ArrayList<>();
		counts.forEach((name, n) -> parts.add(n + "x " + name));
		return String.join(", ", parts);
	}

	private static String lookingAt(Minecraft mc, Level level) {
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
			BlockPos bp = bh.getBlockPos();
			return level.getBlockState(bp).getBlock().getName().getString()
					+ " at " + bp.getX() + " " + bp.getY() + " " + bp.getZ();
		}
		if (hit instanceof EntityHitResult eh) {
			Entity e = eh.getEntity();
			String s = e.getName().getString();
			if (e instanceof LivingEntity le) s += String.format(Locale.ROOT, " (health %.0f/%.0f)", le.getHealth(), le.getMaxHealth());
			return s;
		}
		return "nothing in reach";
	}

	private static String nearby(LocalPlayer p, Level level) {
		Map<String, Integer> counts = new TreeMap<>();
		for (Entity e : level.getEntities(p, p.getBoundingBox().inflate(NEARBY_RADIUS))) {
			if (!(e instanceof LivingEntity)) continue;
			counts.merge(e.getName().getString(), 1, Integer::sum);
		}
		if (counts.isEmpty()) return "no mobs";
		List<String> parts = new ArrayList<>();
		counts.forEach((name, n) -> parts.add(n > 1 ? n + "x " + name : name));
		return String.join(", ", parts);
	}

	private static String mods() {
		List<String> names = new ArrayList<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			ModMetadata m = mod.getMetadata();
			String id = m.getId();
			if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals(EnderTerminalClient.MOD_ID)
					|| id.startsWith("fabric-") || mod.getContainingMod().isPresent()) continue;
			names.add(m.getName());
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names.isEmpty() ? "none" : String.join(", ", names);
	}
}
