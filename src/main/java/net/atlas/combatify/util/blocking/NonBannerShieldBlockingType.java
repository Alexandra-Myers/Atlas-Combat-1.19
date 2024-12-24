package net.atlas.combatify.util.blocking;

import net.atlas.combatify.Combatify;
import net.atlas.combatify.component.CustomDataComponents;
import net.atlas.combatify.config.ConfigurableItemData;
import net.atlas.combatify.enchantment.CustomEnchantmentHelper;
import net.atlas.combatify.util.MethodHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

public class NonBannerShieldBlockingType extends ShieldBlockingType {

	public NonBannerShieldBlockingType(ResourceLocation name, BlockingTypeData data) {
		super(name, data);
	}

	@Override
	public Factory<? extends BlockingType> factory() {
		return Combatify.NON_BANNER_SHIELD_BLOCKING_TYPE_FACTORY;
	}

	@Override
	public float getShieldBlockDamageValue(ItemStack stack, RandomSource random) {
		ConfigurableItemData configurableItemData = MethodHandler.forItem(stack.getItem());
		if (configurableItemData != null) {
			if (configurableItemData.blocker().blockStrength() != null) {
				return CustomEnchantmentHelper.modifyShieldEffectiveness(stack, random, configurableItemData.blocker().blockStrength().floatValue());
			}
		}
		float strengthIncrease = stack.getOrDefault(CustomDataComponents.BLOCKING_LEVEL, 0F);
        return CustomEnchantmentHelper.modifyShieldEffectiveness(stack, random, 5.0F + strengthIncrease);
	}

	@Override
	public double getShieldKnockbackResistanceValue(ItemStack stack) {
		ConfigurableItemData configurableItemData = MethodHandler.forItem(stack.getItem());
		if (configurableItemData != null) {
			if (configurableItemData.blocker().blockKbRes() != null) {
				return configurableItemData.blocker().blockKbRes();
			}
		}

		return 0.5;
	}
}
