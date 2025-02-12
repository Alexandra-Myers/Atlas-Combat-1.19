package net.atlas.combatify;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import eu.pb4.polymer.core.api.item.PolymerItemUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.atlas.atlascore.util.ArrayListExtensions;
import net.atlas.atlascore.util.PrefixLogger;
import net.atlas.combatify.component.CustomDataComponents;
import net.atlas.combatify.component.CustomEnchantmentEffectComponents;
import net.atlas.combatify.component.custom.Blocker;
import net.atlas.combatify.component.generators.WeaponStatsGenerator;
import net.atlas.combatify.config.CombatifyGeneralConfig;
import net.atlas.combatify.config.ItemConfig;
import net.atlas.combatify.critereon.ItemSubPredicateInit;
import net.atlas.combatify.item.CombatifyItemTags;
import net.atlas.combatify.item.ItemRegistry;
import net.atlas.combatify.item.TieredShieldItem;
import net.atlas.combatify.item.WeaponType;
import net.atlas.combatify.networking.NetworkingHandler;
import net.atlas.combatify.util.MethodHandler;
import net.atlas.combatify.util.blocking.BlockingType;
import net.atlas.combatify.util.blocking.BlockingTypeInit;
import net.atlas.combatify.util.blocking.condition.BlockingConditions;
import net.atlas.combatify.util.blocking.effect.PostBlockEffects;
import net.atlas.defaulted.fabric.component.DefaultedRegistries;
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
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
	public static final List<Item> shields = new ArrayListExtensions<>();
	public static final List<UUID> unmoddedPlayers = new ArrayListExtensions<>();
	public static final List<UUID> moddedPlayers = new ArrayListExtensions<>();
	public static final Map<Holder<Item>, ItemAttributeModifiers> originalModifiers = Util.make(new Object2ObjectOpenHashMap<>(), object2ObjectOpenHashMap -> object2ObjectOpenHashMap.defaultReturnValue(ItemAttributeModifiers.EMPTY));
	public static final Map<UUID, Boolean> isPlayerAttacking = new HashMap<>();
	public static final Map<String, WeaponType> defaultWeaponTypes = new HashMap<>();
	public static final Map<ResourceLocation, BlockingType> defaultTypes = new HashMap<>();
	public static Map<ResourceLocation, BlockingType> registeredTypes = new HashMap<>();
	public static final PrefixLogger LOGGER = new PrefixLogger(LogManager.getLogger("Combatify"));
	public static final ResourceLocation CHARGED_REACH_ID = id("charged_reach");

	public static void markCTS(boolean isCTS) {
		Combatify.isCTS = isCTS;
	}

	@Override
	public void onInitialize() {
		isLoaded = true;
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
		if (FabricLoader.getInstance().isModLoaded("polymer-core")) {
			PolymerItemUtils.ITEM_CHECK.register(itemStack -> MethodHandler.forItem(itemStack.getItem()) != null || itemStack.has(CustomDataComponents.BLOCKER) || itemStack.has(CustomDataComponents.CAN_SWEEP) || itemStack.has(CustomDataComponents.PIERCING_LEVEL));
			PolymerItemUtils.ITEM_MODIFICATION_EVENT.register((itemStack, itemStack1, packetContext) -> {
				ServerPlayer player = packetContext.getPlayer();
				if (player == null || moddedPlayers.contains(player.getUUID())) return itemStack;
				if (itemStack.hasNonDefault(CustomDataComponents.BLOCKER)) {
					Blocker blocker = itemStack.get(CustomDataComponents.BLOCKER);
					assert blocker != null;
					itemStack1.set(DataComponents.CONSUMABLE, new Consumable(blocker.useSeconds(), ItemUseAnimation.BLOCK, BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SHIELD_BREAK), false, Collections.emptyList()));
				}
				return itemStack1;
			});
		}
		CombatifyItemTags.init();
		if (CONFIG.dispensableTridents())
 			DispenserBlock.registerProjectileBehavior(Items.TRIDENT);
		DefaultItemComponentEvents.MODIFY.register(modDetectionNetworkChannel, (modifyContext) -> {
			modifyContext.modify(Items.WOODEN_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 1));
			modifyContext.modify(Items.GOLDEN_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 1));
			modifyContext.modify(Items.STONE_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 2));
			modifyContext.modify(Items.IRON_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 3));
			modifyContext.modify(Items.DIAMOND_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 4));
			modifyContext.modify(Items.NETHERITE_SWORD, builder -> builder.set(CustomDataComponents.BLOCKING_LEVEL, 5));
			modifyContext.modify(Items.SHIELD, builder -> builder.set(CustomDataComponents.BLOCKER, Blocker.SHIELD));
		});
		if (CONFIG.configOnlyWeapons()) {
			ItemRegistry.registerWeapons();
			Event<ItemGroupEvents.ModifyEntries> event = ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT);
			event.register(entries -> entries.addAfter(NETHERITE_SWORD, ItemRegistry.WOODEN_KNIFE, ItemRegistry.STONE_KNIFE, ItemRegistry.IRON_KNIFE, ItemRegistry.GOLD_KNIFE, ItemRegistry.DIAMOND_KNIFE, ItemRegistry.NETHERITE_KNIFE, ItemRegistry.WOODEN_LONGSWORD, ItemRegistry.STONE_LONGSWORD, ItemRegistry.IRON_LONGSWORD, ItemRegistry.GOLD_LONGSWORD, ItemRegistry.DIAMOND_LONGSWORD, ItemRegistry.NETHERITE_LONGSWORD));
		}
		if (CONFIG.tieredShields()) {
			TieredShieldItem.init();
			shields.add(Items.SHIELD);
			Event<ItemGroupEvents.ModifyEntries> event = ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT);
			event.register(entries -> entries.addAfter(Items.SHIELD, TieredShieldItem.IRON_SHIELD, TieredShieldItem.GOLD_SHIELD, TieredShieldItem.DIAMOND_SHIELD, TieredShieldItem.NETHERITE_SHIELD));
		}

		DefaultedRegistries.registerPatchGenerator("combat_test_weapon_stats", WeaponStatsGenerator.CODEC);
		ResourceManagerHelper.registerBuiltinResourcePack(id("combatify_extras"), FabricLoader.getInstance().getModContainer("combatify").get(), Component.translatable("pack.combatify.combatify_extras"), CONFIG.configOnlyWeapons() || CONFIG.tieredShields() ? ResourcePackActivationType.ALWAYS_ENABLED : ResourcePackActivationType.NORMAL);
		ResourceManagerHelper.registerBuiltinResourcePack(id("sword_blocking"), FabricLoader.getInstance().getModContainer("combatify").get(), Component.translatable("pack.combatify.sword_blocking"), ResourcePackActivationType.NORMAL);
		ResourceManagerHelper.registerBuiltinResourcePack(id("vanilla_attack_balancing"), FabricLoader.getInstance().getModContainer("combatify").get(), Component.translatable("pack.combatify.vanilla_attack_balancing"), ResourcePackActivationType.NORMAL);
		ResourceManagerHelper.registerBuiltinResourcePack(id("weapon_types"), FabricLoader.getInstance().getModContainer("combatify").get(), Component.translatable("pack.combatify.weapon_types"), ResourcePackActivationType.DEFAULT_ENABLED);
		ResourceManagerHelper.registerBuiltinResourcePack(id("wooden_shield_recipe"), FabricLoader.getInstance().getModContainer("combatify").get(), Component.translatable("pack.combatify.wooden_shield_recipe"), CONFIG.tieredShields() ? ResourcePackActivationType.ALWAYS_ENABLED : ResourcePackActivationType.NORMAL);
		if (Combatify.CONFIG.percentageDamageEffects()) {
			MobEffects.DAMAGE_BOOST.value().addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.withDefaultNamespace("effect.strength"), 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			MobEffects.WEAKNESS.value().addAttributeModifier(Attributes.ATTACK_DAMAGE, ResourceLocation.withDefaultNamespace("effect.weakness"), -0.2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	public static void setDurability(DataComponentPatch.Builder builder, @NotNull Item item, int value) {
		builder.set(DataComponents.DAMAGE, 0);
		builder.set(DataComponents.MAX_DAMAGE, value);
		builder.set(DataComponents.MAX_STACK_SIZE, 1);
	}
	public static BlockingType registerBlockingType(BlockingType blockingType) {
		Combatify.registeredTypes.put(blockingType.name(), blockingType);
		return blockingType;
	}
	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}
	public static void defineDefaultWeaponType(WeaponType type) {
		defaultWeaponTypes.put(type.name(), type);
	}
	public static BlockingType defineDefaultBlockingType(BlockingType blockingType) {
		defaultTypes.put(blockingType.name(), blockingType);
		return registerBlockingType(blockingType);
	}
}
