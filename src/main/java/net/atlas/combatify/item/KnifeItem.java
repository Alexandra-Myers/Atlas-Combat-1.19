package net.atlas.combatify.item;

import net.atlas.combatify.config.ConfigurableItemData;
import net.atlas.combatify.extensions.Tier;
import net.atlas.combatify.extensions.WeaponWithType;
import net.atlas.combatify.util.MethodHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public class KnifeItem extends Item implements WeaponWithType {
	public final ToolMaterial toolMaterial;
	public KnifeItem(ToolMaterial toolMaterial, Properties properties) {
		super(toolMaterial.applySwordProperties(properties, 0, 0).attributes(baseAttributeModifiers(toolMaterial)));
		this.toolMaterial = toolMaterial;
	}

	public static ItemAttributeModifiers baseAttributeModifiers(Tier tier) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		WeaponType.KNIFE.addCombatAttributes(tier, builder);
		return builder.build();
	}

	@Override
	public ItemAttributeModifiers modifyAttributeModifiers(ItemAttributeModifiers original) {
		ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
		combatify$getWeaponType().addCombatAttributes(getConfigTier(), builder);
		return builder.build();
	}

	@Override
	public boolean canAttackBlock(BlockState state, Level world, BlockPos pos, Player miner) {
		return !miner.isCreative();
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
		return true;
	}

	@Override
	public Tier getConfigTier() {
		Optional<Tier> optionalTier = getTierFromConfig();
		return optionalTier.orElse(toolMaterial);
	}

	@Override
	public WeaponType combatify$getWeaponType() {
		ConfigurableItemData configurableItemData = MethodHandler.forItem(this);
		if (configurableItemData != null) {
			WeaponType type = configurableItemData.weaponStats().weaponType();
			if (type != null)
				return type;
		}
		return WeaponType.KNIFE;
	}
}
