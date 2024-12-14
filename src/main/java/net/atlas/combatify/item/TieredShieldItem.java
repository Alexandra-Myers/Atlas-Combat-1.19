package net.atlas.combatify.item;

import net.atlas.combatify.Combatify;
import net.atlas.combatify.config.ConfigurableItemData;
import net.atlas.combatify.config.ConfigurableWeaponData;
import net.atlas.combatify.extensions.ItemExtensions;
import net.atlas.combatify.util.BlockingType;
import net.atlas.combatify.util.MethodHandler;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.Optional;

import static net.atlas.combatify.Combatify.id;
import static net.atlas.combatify.Combatify.shields;
import static net.atlas.combatify.item.ItemRegistry.registerItem;

public class TieredShieldItem extends ShieldItem implements ItemExtensions {
	public final Tier tier;
	public static final Item WOODEN_SHIELD = registerItem(id("wooden_shield"), new TieredShieldItem(Tiers.WOOD, new Item.Properties()));
	public static final Item IRON_SHIELD = registerItem(id("iron_shield"), new TieredShieldItem(Tiers.IRON, new Item.Properties()));
	public static final Item GOLD_SHIELD = registerItem(id("golden_shield"), new TieredShieldItem(Tiers.GOLD, new Item.Properties()));
	public static final Item DIAMOND_SHIELD = registerItem(id("diamond_shield"), new TieredShieldItem(Tiers.DIAMOND, new Item.Properties()));
	public static final Item NETHERITE_SHIELD = registerItem(id("netherite_shield"), new TieredShieldItem(Tiers.NETHERITE, new Item.Properties().fireResistant()));

	public TieredShieldItem(Tier tier, Properties properties) {
		super(properties.durability(tier.getUses() * 2).component(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY));
		this.tier = tier;
		shields.add(this);
		if(FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT)
			registerModelPredicate();
	}

	private void registerModelPredicate() {
		ItemProperties.register(this, ResourceLocation.withDefaultNamespace("blocking"), (itemStack, clientWorld, livingEntity, i) ->
			livingEntity != null && livingEntity.isBlocking() && MethodHandler.getBlockingItem(livingEntity).stack() == itemStack ? 1.0F : 0.0F);
	}


	public int getEnchantmentValue() {
		return getConfigTier().getEnchantmentValue();
	}

	public boolean isValidRepairItem(ItemStack itemStack, ItemStack itemStack2) {
		return getConfigTier().getRepairIngredient().test(itemStack2) || canRepairThroughConfig(itemStack2);
	}

	@Override
	public Item combatify$self() {
		return this;
	}

	@Override
	public Tier getConfigTier() {
		Optional<Tier> tier = getTierFromConfig();
        return tier.orElse(this.tier);
    }
	public static void init() {

	}

	@Override
	public BlockingType combatify$getBlockingType() {
		ConfigurableItemData configurableItemData = MethodHandler.forItem(this);
		if (configurableItemData != null) {
			if (configurableItemData.blocker().blockingType() != null)
				return configurableItemData.blocker().blockingType();
			WeaponType type;
			ConfigurableWeaponData configurableWeaponData;
			if ((type = configurableItemData.weaponStats().weaponType()) != null && (configurableWeaponData = MethodHandler.forWeapon(type)) != null) {
				BlockingType blockingType = configurableWeaponData.blockingType();
				if (blockingType != null)
					return blockingType;
			}
		}
		return Combatify.registeredTypes.get("new_shield");
	}
}
