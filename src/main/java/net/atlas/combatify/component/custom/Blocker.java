package net.atlas.combatify.component.custom;

import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.atlas.combatify.Combatify;
import net.atlas.combatify.enchantment.CustomEnchantmentHelper;
import net.atlas.combatify.util.MethodHandler;
import net.atlas.combatify.util.blocking.BlockingType;
import net.atlas.combatify.util.blocking.condition.*;
import net.atlas.combatify.util.blocking.effect.DoNothing;
import net.atlas.combatify.util.blocking.effect.KnockbackAttacker;
import net.atlas.combatify.util.blocking.effect.PostBlockEffect;
import net.atlas.combatify.util.blocking.effect.PostBlockEffects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.List;

import static net.atlas.combatify.util.MethodHandler.getBlockingType;

public record Blocker(BlockingType blockingType, float useSeconds, PostBlockEffect postBlockEffect, BlockingCondition blockingCondition) {
	public static final Blocker EMPTY = new Blocker(Combatify.EMPTY, 0, new DoNothing(), new AnyOf(Collections.emptyList()));
	public static final Blocker SHIELD = new Blocker(Combatify.SHIELD, 3600, new KnockbackAttacker(), new Unconditional());
	public static final Blocker NEW_SHIELD = new Blocker(Combatify.NEW_SHIELD, 3600, new KnockbackAttacker(), new Unconditional());
	public static final Blocker SWORD = new Blocker(Combatify.SWORD, 3600, new DoNothing(), new AllOf(List.of(new RequiresSwordBlocking(), new RequiresEmptyHand(InteractionHand.OFF_HAND))));
	public static final Codec<Blocker> SIMPLE_CODEC = BlockingType.SIMPLE_CODEC.xmap(blockingType1 -> new Blocker(blockingType1, 3600, new KnockbackAttacker(), new Unconditional()), Blocker::blockingType);
	public static final Codec<Blocker> FULL_CODEC = RecordCodecBuilder.create(instance ->
		instance.group(BlockingType.SIMPLE_CODEC.fieldOf("type").forGetter(Blocker::blockingType),
				ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("seconds", 3600F).forGetter(Blocker::useSeconds),
				PostBlockEffects.MAP_CODEC.forGetter(Blocker::postBlockEffect),
				BlockingConditions.MAP_CODEC.forGetter(Blocker::blockingCondition))
			.apply(instance, Blocker::new));
	public static final Codec<Blocker> CODEC = Codec.withAlternative(FULL_CODEC, SIMPLE_CODEC);

	public static final StreamCodec<RegistryFriendlyByteBuf, Blocker> STREAM_CODEC = StreamCodec.composite(
		BlockingType.IDENTITY_STREAM_CODEC,
		Blocker::blockingType,
		ByteBufCodecs.FLOAT,
		Blocker::useSeconds,
		PostBlockEffect.STREAM_CODEC,
		Blocker::postBlockEffect,
		BlockingCondition.STREAM_CODEC,
		Blocker::blockingCondition,
		Blocker::new
	);

	public void doEffect(ServerLevel serverLevel, ItemStack blockingItem, LivingEntity target, LivingEntity attacker, DamageSource damageSource) {
		postBlockEffect.doEffect(serverLevel, blockingItem, target, attacker, damageSource);
		CustomEnchantmentHelper.applyPostBlockedEffects(serverLevel, blockingItem, target, attacker, damageSource);
		MethodHandler.disableShield(attacker, target, damageSource, blockingItem);
	}

	public int useTicks() {
		return (int) (useSeconds * 20.0F);
	}

	public void block(ServerLevel serverLevel, LivingEntity instance, DamageSource source, ItemStack itemStack, LocalFloatRef amount, LocalFloatRef f, LocalFloatRef g, LocalBooleanRef bl) {
		if (blockingCondition.canBlock(serverLevel, instance, null, itemStack, source, amount, f, g, bl)) {
			if (getBlockingType(itemStack).hasDelay() && Combatify.CONFIG.shieldDelay() > 0 && itemStack.getUseDuration(instance) - instance.getUseItemRemainingTicks() < Combatify.CONFIG.shieldDelay()) {
				if (Combatify.CONFIG.disableDuringShieldDelay())
					if (source.getDirectEntity() instanceof LivingEntity attacker)
						MethodHandler.disableShield(attacker, instance, source, itemStack);
				return;
			}
			getBlockingType(itemStack).block(serverLevel, instance, null, itemStack, source, amount, f, g, bl);
		}
	}

	public InteractionResult use(ItemStack itemStack, Level level, Player user, InteractionHand hand, InteractionResult original) {
		if (original != InteractionResult.PASS) return null;
		if (!canUse(itemStack, level, user, hand)) return null;
		return blockingType.use(itemStack, level, user, hand);
	}

	@SuppressWarnings("BooleanMethodIsAlwaysInverted")
	public boolean canUse(ItemStack itemStack, Level level, Player user, InteractionHand hand) {
		return blockingCondition.canUse(itemStack, level, user, hand);
	}

	public boolean canShowInTooltip(ItemStack itemStack, Player player) {
		return blockingCondition.canShowInToolTip(itemStack, player);
	}

	public boolean canOverrideUseDurationAndAnimation(ItemStack itemStack) {
		return blockingCondition.overridesUseDurationAndAnimation(itemStack);
	}
}
