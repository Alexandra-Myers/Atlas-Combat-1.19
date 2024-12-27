package net.atlas.combatify.util.blocking;

import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class EmptyBlockingType extends BlockingType {
	public EmptyBlockingType(ResourceLocation name) {
		super(name, new BlockingTypeData(false, false, false, false, false, false));
	}

	@Override
	public Factory<? extends BlockingType> factory() {
		return (name, blockingTypeData) -> new EmptyBlockingType(name);
	}

	@Override
	public void block(ServerLevel serverLevel, LivingEntity instance, ItemStack blockingItem, DamageSource source, LocalFloatRef amount, LocalFloatRef protectedDamage, LocalBooleanRef blocked) {

	}

	@Override
	public float getShieldBlockDamageValue(ItemStack stack, RandomSource random) {
		return 0;
	}

	@Override
	public double getShieldKnockbackResistanceValue(ItemStack stack) {
		return 0;
	}

	@Override
	public @NotNull InteractionResult use(ItemStack itemStack, Level world, Player user, InteractionHand hand) {
		return InteractionResult.PASS;
	}

	@Override
	public void appendTooltipInfo(Consumer<Component> consumer, Player player, ItemStack stack) {

	}
}
