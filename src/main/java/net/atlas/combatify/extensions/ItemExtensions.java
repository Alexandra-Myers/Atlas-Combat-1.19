package net.atlas.combatify.extensions;

import net.atlas.combatify.Combatify;
import net.atlas.combatify.config.ConfigurableItemData;
import net.atlas.combatify.config.ConfigurableWeaponData;
import net.atlas.combatify.item.WeaponType;
import net.atlas.combatify.util.BlockingType;
import net.atlas.combatify.util.MethodHandler;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.Optional;

public interface ItemExtensions {
	default WeaponType combatify$getWeaponType() {
		ConfigurableItemData configurableItemData = MethodHandler.forItem(combatify$self());
		if (configurableItemData != null) {
			WeaponType type = configurableItemData.weaponStats().weaponType();
			if (type != null)
				return type;
		}
		return WeaponType.EMPTY;
	}

	default ItemAttributeModifiers modifyAttributeModifiers(ItemAttributeModifiers original) {
		return original;
	}

	default double getChargedAttackBonus() {
        double chargedBonus = 1.0;
		ConfigurableItemData configurableItemData = MethodHandler.forItem(combatify$self());
		if (configurableItemData != null) {
			WeaponType type;
			if ((type = configurableItemData.weaponStats().weaponType()) != null)
				chargedBonus = type.getChargedReach();
			if (configurableItemData.weaponStats().chargedReach() != null)
				chargedBonus = configurableItemData.weaponStats().chargedReach();
		}
		return chargedBonus;
	}

	default boolean canSweep() {
        boolean canSweep = false;
		ConfigurableItemData configurableItemData = MethodHandler.forItem(combatify$self());
		if (configurableItemData != null) {
			WeaponType type;
			if ((type = configurableItemData.weaponStats().weaponType()) != null)
				canSweep = type.canSweep();
			if (configurableItemData.weaponStats().canSweep() != null)
				canSweep = configurableItemData.weaponStats().canSweep();
		}
		return canSweep;
	}

	default BlockingType combatify$getBlockingType() {
		ConfigurableItemData configurableItemData = MethodHandler.forItem(combatify$self());
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
		return Combatify.EMPTY;
	}

	default Item combatify$self() {
		throw new IllegalStateException("Extension has not been applied");
	}

	default Tier getConfigTier() {
		Optional<Tier> tier = getTierFromConfig();
        return tier.orElse(Combatify.originalTiers.get(combatify$self().builtInRegistryHolder()));
    }
	default Optional<Tier> getTierFromConfig() {
		ConfigurableItemData configurableItemData = MethodHandler.forItem(combatify$self());
		if (configurableItemData != null)
            return configurableItemData.optionalTier();
		return Optional.empty();
	}
}
