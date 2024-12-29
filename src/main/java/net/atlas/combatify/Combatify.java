package net.atlas.combatify;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.atlas.atlascore.util.ArrayListExtensions;
import net.atlas.atlascore.util.PrefixLogger;
import net.atlas.combatify.component.CustomDataComponents;
import net.atlas.combatify.component.CustomEnchantmentEffectComponents;
import net.atlas.combatify.component.custom.Blocker;
import net.atlas.combatify.config.CombatifyGeneralConfig;
import net.atlas.combatify.config.ItemConfig;
import net.atlas.combatify.critereon.ItemSubPredicateInit;
import net.atlas.combatify.extensions.Tier;
import net.atlas.combatify.item.CombatifyItemTags;
import net.atlas.combatify.item.ItemRegistry;
import net.atlas.combatify.item.TieredShieldItem;
import net.atlas.combatify.item.WeaponType;
import net.atlas.combatify.networking.NetworkingHandler;
import net.atlas.combatify.util.MethodHandler;
import net.atlas.combatify.util.blocking.BlockingTypeInit;
import net.atlas.combatify.util.blocking.condition.BlockingConditions;
import net.atlas.combatify.util.blocking.effect.PostBlockEffects;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.minecraft.world.item.Items.NETHERITE_SWORD;

@SuppressWarnings("unused")
public class Combatify implements ModInitializer {
	public static final String MOD_ID = "combatify";
	public static CombatifyGeneralConfig CONFIG = new CombatifyGeneralConfig();
	public static ItemConfig ITEMS;
	public static ResourceLocation modDetectionNetworkChannel = id("networking");
	public NetworkingHandler networkingHandler;
	public static boolean isCTS = false;
	public static boolean isLoaded = false;
	public static boolean mobConfigIsDirty = true;
	public static final List<TieredShieldItem> shields = new ArrayListExtensions<>();
	public static final List<UUID> unmoddedPlayers = new ArrayListExtensions<>();
	public static final List<UUID> moddedPlayers = new ArrayListExtensions<>();
	public static final Map<Holder<Item>, ItemAttributeModifiers> originalModifiers = Util.make(new Object2ObjectOpenHashMap<>(), object2ObjectOpenHashMap -> object2ObjectOpenHashMap.defaultReturnValue(ItemAttributeModifiers.EMPTY));
	public static final Object2ObjectOpenHashMap<Holder<Item>, Tier> originalTiers = new Object2ObjectOpenHashMap<>();
	public static final Map<UUID, Boolean> isPlayerAttacking = new HashMap<>();
	public static final Map<String, WeaponType> defaultWeaponTypes = new HashMap<>();
	public static final BiMap<String, Tier> defaultTiers = HashBiMap.create();
	public static Map<String, WeaponType> registeredWeaponTypes = new HashMap<>();
	public static BiMap<String, Tier> tiers = HashBiMap.create();
	public static final PrefixLogger LOGGER = new PrefixLogger(LogManager.getLogger("Combatify"));
	public static final ResourceLocation CHARGED_REACH_ID = id("charged_reach");

	public static void markCTS(boolean isCTS) {
		Combatify.isCTS = isCTS;
	}

	@Override
	public void onInitialize() {
		isLoaded = true;
		originalTiers.defaultReturnValue(ToolMaterial.DIAMOND);
		BlockingConditions.bootstrap();
		PostBlockEffects.bootstrap();
		WeaponType.init();
		networkingHandler = new NetworkingHandler();
		AttackEntityCallback.EVENT.register(modDetectionNetworkChannel, (player, world, hand, pos, direction) -> {
			if(Combatify.unmoddedPlayers.contains(player.getUUID()))
				Combatify.isPlayerAttacking.put(player.getUUID(), false);
			return InteractionResult.PASS;
		});
		AttackBlockCallback.EVENT.register(modDetectionNetworkChannel, (player, world, hand, pos, direction) -> {
			if(Combatify.unmoddedPlayers.contains(player.getUUID())) {
				Combatify.isPlayerAttacking.put(player.getUUID(), false);
				HitResult hitResult = new BlockHitResult(Vec3.atCenterOf(pos), direction, pos, false);
				hitResult = MethodHandler.redirectResult(player, hitResult);
				if (hitResult.getType() == HitResult.Type.ENTITY && player instanceof ServerPlayer serverPlayer) {
					serverPlayer.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(((EntityHitResult) hitResult).getEntity(), player.isShiftKeyDown()));
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register(modDetectionNetworkChannel, (player, world, hand, hitResult) -> {
			if(Combatify.unmoddedPlayers.contains(player.getUUID()))
				Combatify.isPlayerAttacking.put(player.getUUID(), false);
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register(modDetectionNetworkChannel, (player, world, hand, entity, hitResult) -> {
			if(Combatify.unmoddedPlayers.contains(player.getUUID()))
				Combatify.isPlayerAttacking.put(player.getUUID(), false);
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register(modDetectionNetworkChannel, (player, world, hand) -> {
			if(Combatify.unmoddedPlayers.contains(player.getUUID()))
				Combatify.isPlayerAttacking.put(player.getUUID(), false);
			return InteractionResult.PASS;
		});

		LOGGER.info("Init started.");
		CustomDataComponents.registerDataComponents();
		CustomEnchantmentEffectComponents.registerEnchantmentEffectComponents();
		ItemSubPredicateInit.init();
		BlockingTypeInit.init();
		CombatifyItemTags.init();
		if (CONFIG.dispensableTridents())
 			DispenserBlock.registerProjectileBehavior(Items.TRIDENT);
		DefaultItemComponentEvents.MODIFY.register(modDetectionNetworkChannel, (modifyContext) -> {
			modifyContext.modify(item -> item instanceof SwordItem, (builder, item) -> builder.set(CustomDataComponents.BLOCKER, Blocker.SWORD));
			modifyContext.modify(Items.WOODEN_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 1));
			modifyContext.modify(Items.GOLDEN_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 1));
			modifyContext.modify(Items.STONE_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 2));
			modifyContext.modify(Items.IRON_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 3));
			modifyContext.modify(Items.DIAMOND_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 4));
			modifyContext.modify(Items.NETHERITE_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 5));
			modifyContext.modify(Items.SHIELD, builder -> builder.set(CustomDataComponents.BLOCKER, Blocker.SHIELD));
		});
		bindItemsToDefaultTier(ToolMaterial.WOOD, Items.WOODEN_SWORD, Items.WOODEN_SHOVEL, Items.WOODEN_AXE, Items.WOODEN_HOE, Items.WOODEN_PICKAXE);
		bindItemsToDefaultTier(ToolMaterial.GOLD, Items.GOLDEN_SWORD, Items.GOLDEN_SHOVEL, Items.GOLDEN_AXE, Items.GOLDEN_HOE, Items.GOLDEN_PICKAXE);
		bindItemsToDefaultTier(ToolMaterial.STONE, Items.STONE_SWORD, Items.STONE_SHOVEL, Items.STONE_AXE, Items.STONE_HOE, Items.STONE_PICKAXE);
		bindItemsToDefaultTier(ToolMaterial.IRON, Items.IRON_SWORD, Items.IRON_SHOVEL, Items.IRON_AXE, Items.IRON_HOE, Items.IRON_PICKAXE);
		bindItemsToDefaultTier(ToolMaterial.DIAMOND, Items.DIAMOND_SWORD, Items.DIAMOND_SHOVEL, Items.DIAMOND_AXE, Items.DIAMOND_HOE, Items.DIAMOND_PICKAXE);
		bindItemsToDefaultTier(ToolMaterial.NETHERITE, Items.NETHERITE_SWORD, Items.NETHERITE_SHOVEL, Items.NETHERITE_AXE, Items.NETHERITE_HOE, Items.NETHERITE_PICKAXE);
		if (CONFIG.configOnlyWeapons()) {
			ItemRegistry.registerWeapons();
			Event<ItemGroupEvents.ModifyEntries> event = ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT);
			event.register(entries -> entries.addAfter(NETHERITE_SWORD, ItemRegistry.WOODEN_KNIFE, ItemRegistry.STONE_KNIFE, ItemRegistry.IRON_KNIFE, ItemRegistry.GOLD_KNIFE, ItemRegistry.DIAMOND_KNIFE, ItemRegistry.NETHERITE_KNIFE, ItemRegistry.WOODEN_LONGSWORD, ItemRegistry.STONE_LONGSWORD, ItemRegistry.IRON_LONGSWORD, ItemRegistry.GOLD_LONGSWORD, ItemRegistry.DIAMOND_LONGSWORD, ItemRegistry.NETHERITE_LONGSWORD));
		}
		if (CONFIG.tieredShields()) {
			TieredShieldItem.init();
			Event<ItemGroupEvents.ModifyEntries> event = ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT);
			event.register(entries -> entries.addAfter(Items.SHIELD, TieredShieldItem.WOODEN_SHIELD, TieredShieldItem.IRON_SHIELD, TieredShieldItem.GOLD_SHIELD, TieredShieldItem.DIAMOND_SHIELD, TieredShieldItem.NETHERITE_SHIELD));
			originalTiers.put(TieredShieldItem.WOODEN_SHIELD.builtInRegistryHolder(), ToolMaterial.WOOD);
			originalTiers.put(TieredShieldItem.GOLD_SHIELD.builtInRegistryHolder(), ToolMaterial.GOLD);
			originalTiers.put(TieredShieldItem.IRON_SHIELD.builtInRegistryHolder(), ToolMaterial.IRON);
			originalTiers.put(TieredShieldItem.DIAMOND_SHIELD.builtInRegistryHolder(), ToolMaterial.DIAMOND);
			originalTiers.put(TieredShieldItem.NETHERITE_SHIELD.builtInRegistryHolder(), ToolMaterial.NETHERITE);
		}

		ResourceManagerHelper.registerBuiltinResourcePack(id("combatify_extras"), FabricLoader.getInstance().getModContainer("combatify").get(), Component.translatable("pack.combatify.combatify_extras"), CONFIG.configOnlyWeapons() || CONFIG.tieredShields() ? ResourcePackActivationType.ALWAYS_ENABLED : ResourcePackActivationType.NORMAL);
		ResourceManagerHelper.registerBuiltinResourcePack(id("wooden_shield_recipe"), FabricLoader.getInstance().getModContainer("combatify").get(), Component.translatable("pack.combatify.wooden_shield_recipe"), CONFIG.tieredShields() ? ResourcePackActivationType.ALWAYS_ENABLED : ResourcePackActivationType.NORMAL);
		if (Combatify.CONFIG.percentageDamageEffects()) {
			MobEffects.DAMAGE_BOOST.value().addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.withDefaultNamespace("effect.strength"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			MobEffects.WEAKNESS.value().addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.withDefaultNamespace("effect.weakness"), -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	public static void setDurability(DataComponentMap.Builder builder, @NotNull Item item, int value) {
		builder.set(DataComponents.DAMAGE, 0);
		builder.set(DataComponents.MAX_DAMAGE, value);
		builder.set(DataComponents.MAX_STACK_SIZE, 1);
	}
	public static void registerWeaponType(WeaponType weaponType) {
		Combatify.registeredWeaponTypes.put(weaponType.name(), weaponType);
    }
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
	public static void defineDefaultWeaponType(WeaponType type) {
		defaultWeaponTypes.put(type.name(), type);
	}
	public static void defineDefaultTier(String name, Tier tier) {
		defaultTiers.put(name, tier);
		tiers.put(name, tier);
	}
	public static void bindItemsToDefaultTier(Tier tier, Item... items) {
		for (Item item : items) {
			originalTiers.put(item.builtInRegistryHolder(), tier);
		}
	}
}
