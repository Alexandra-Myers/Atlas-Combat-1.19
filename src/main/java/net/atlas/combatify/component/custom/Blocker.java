package net.atlas.combatify.component.custom;

import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.atlas.combatify.Combatify;
import net.atlas.combatify.enchantment.CustomEnchantmentHelper;
import net.atlas.combatify.util.MethodHandler;
import net.atlas.combatify.util.blocking.BlockingType;
import net.atlas.combatify.util.blocking.BlockingTypeInit;
import net.atlas.combatify.util.blocking.condition.*;
import net.atlas.combatify.util.blocking.effect.PostBlockEffectWrapper;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static net.atlas.combatify.util.MethodHandler.getBlockingData;

public record Blocker(ResourceKey<BlockingType> blockingTypeResourceKey, float useSeconds, PostBlockEffectWrapper postBlockEffect, BlockingCondition blockingCondition) {
	public Blocker(ResourceKey<BlockingType> blockingTypeResourceKey, float useSeconds, BlockingCondition blockingCondition) {
		this(blockingTypeResourceKey, useSeconds, PostBlockEffectWrapper.DEFAULT, blockingCondition);
	}
	public static final Blocker EMPTY = new Blocker(ResourceKey.create(BlockingTypeInit.BLOCKING_TYPE, ResourceLocation.withDefaultNamespace("empty")), 0, PostBlockEffectWrapper.DEFAULT, new AnyOf(Collections.emptyList()));
	public static final Blocker SHIELD = new Blocker(ResourceKey.create(BlockingTypeInit.BLOCKING_TYPE, ResourceLocation.withDefaultNamespace("shield")), 3600, PostBlockEffectWrapper.KNOCKBACK, Unconditional.INSTANCE);
	public static final Blocker NEW_SHIELD = new Blocker(ResourceKey.create(BlockingTypeInit.BLOCKING_TYPE, ResourceLocation.withDefaultNamespace("new_shield")), 3600, PostBlockEffectWrapper.KNOCKBACK, Unconditional.INSTANCE);
	public static final Blocker SWORD = new Blocker(ResourceKey.create(BlockingTypeInit.BLOCKING_TYPE, ResourceLocation.withDefaultNamespace("sword")), 3600, PostBlockEffectWrapper.DEFAULT, new AllOf(List.of(new RequiresSwordBlocking(), new RequiresEmptyHand(InteractionHand.OFF_HAND))));
	public static final Codec<Blocker> SIMPLE_CODEC = ResourceKey.codec(BlockingTypeInit.BLOCKING_TYPE).xmap(blockingType1 -> new Blocker(blockingType1, 3600, PostBlockEffectWrapper.KNOCKBACK, Unconditional.INSTANCE), Blocker::blockingTypeResourceKey);
	public static final Codec<Blocker> FULL_CODEC = RecordCodecBuilder.create(instance ->
		instance.group(ResourceKey.codec(BlockingTypeInit.BLOCKING_TYPE).fieldOf("type").forGetter(Blocker::blockingTypeResourceKey),
				ExtraCodecs.NON_NEGATIVE_FLOAT.optionalFieldOf("seconds", 3600F).forGetter(Blocker::useSeconds),
				PostBlockEffectWrapper.MAP_CODEC.orElse(PostBlockEffectWrapper.KNOCKBACK).forGetter(Blocker::postBlockEffect),
				BlockingConditions.MAP_CODEC.orElse(Unconditional.INSTANCE).forGetter(Blocker::blockingCondition))
			.apply(instance, Blocker::new));
	public static final Codec<Blocker> CODEC = Codec.withAlternative(FULL_CODEC, SIMPLE_CODEC);

	public static final StreamCodec<RegistryFriendlyByteBuf, Blocker> STREAM_CODEC = StreamCodec.composite(
		ResourceKey.streamCodec(BlockingTypeInit.BLOCKING_TYPE),
		Blocker::blockingTypeResourceKey,
		ByteBufCodecs.FLOAT,
		Blocker::useSeconds,
		BlockingCondition.STREAM_CODEC,
		Blocker::blockingCondition,
		Blocker::new
	);

	public boolean isEmpty() {
		return this.equals(EMPTY);
	}

	public Holder<BlockingType> blockingTypeHolder(HolderLookup<BlockingType> holderLookup) {
		return holderLookup.get(blockingTypeResourceKey).orElse(null);
	}

	public void doEffect(ServerLevel serverLevel, EquipmentSlot equipmentSlot, ItemStack blockingItem, LivingEntity target, LivingEntity attacker, DamageSource damageSource) {
		if (postBlockEffect.matches(Enchantment.damageContext(serverLevel, 1, target, damageSource))) {
			LivingEntity applicable = switch (postBlockEffect.affected()) {
				case ATTACKER, DAMAGING_ENTITY -> attacker;
                case VICTIM -> target;
			};
			postBlockEffect.effect().doEffect(serverLevel, new EnchantedItemInUse(blockingItem, equipmentSlot, target), attacker, damageSource, 1, applicable, applicable.position());
		}
		CustomEnchantmentHelper.applyPostBlockedEffects(serverLevel, target, attacker, damageSource);
		MethodHandler.disableShield(attacker, target, damageSource, blockingItem);
	}

	public int useTicks() {
		return (int) (useSeconds * 20.0F);
	}

	public void block(ServerLevel serverLevel, LivingEntity instance, DamageSource source, ItemStack itemStack, LocalFloatRef amount, LocalFloatRef protectedDamage, LocalBooleanRef blocked) {
		Holder<BlockingType> blockingTypeHolder;
		if ((blockingTypeHolder = blockingTypeHolder(serverLevel.holderLookup(BlockingTypeInit.BLOCKING_TYPE))) != null && blockingCondition.canBlock(serverLevel, instance, itemStack, source, amount.get())) {
			if (getBlockingData(itemStack, serverLevel, BlockingType::hasDelay) && Combatify.CONFIG.shieldDelay() > 0 && itemStack.getUseDuration(instance) - instance.getUseItemRemainingTicks() < Combatify.CONFIG.shieldDelay()) {
				if (Combatify.CONFIG.disableDuringShieldDelay())
					if (source.getDirectEntity() instanceof LivingEntity attacker)
						MethodHandler.disableShield(attacker, instance, source, itemStack);
				return;
			}
			blockingTypeHolder.value().block(serverLevel, instance, itemStack, source, amount, protectedDamage, blocked);
		}
	}

	public InteractionResult use(ItemStack itemStack, Level level, Player user, InteractionHand hand, InteractionResult original) {
		if (isEmpty()) return null;
		if (original != InteractionResult.PASS) return null;
		if (!canUse(itemStack, level, user, hand)) return null;
		user.startUsingItem(hand);
		return InteractionResult.CONSUME;
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

	public void appendTooltipInfo(Consumer<Component> writer, Player player, ItemStack stack) {
		Holder<BlockingType> blockingTypeHolder;
		if ((blockingTypeHolder = blockingTypeHolder(player.level().holderLookup(BlockingTypeInit.BLOCKING_TYPE))) != null) {
			blockingTypeHolder.value().handler().appendTooltipInfo(writer, player, stack);
		}
	}
	public float getShieldKnockbackResistanceValue(ItemStack itemStack, Level level, RandomSource randomSource) {
		Holder<BlockingType> blockingTypeHolder;
		if ((blockingTypeHolder = blockingTypeHolder(level.holderLookup(BlockingTypeInit.BLOCKING_TYPE))) != null) {
			return blockingTypeHolder.value().handler().getShieldKnockbackResistanceValue(itemStack, randomSource) / 10;
		}
		return 0;
	}
}
