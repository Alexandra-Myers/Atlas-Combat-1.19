package net.atlas.combatify.util.blocking;

import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.atlas.combatify.Combatify;
import net.atlas.combatify.component.CustomDataComponents;
import net.atlas.combatify.config.ConfigurableItemData;
import net.atlas.combatify.critereon.CustomLootContextParamSets;
import net.atlas.combatify.enchantment.CustomEnchantmentHelper;
import net.atlas.combatify.util.MethodHandler;
import net.atlas.combatify.util.blocking.damage_parsers.DamageParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SpectralArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.apache.commons.lang3.mutable.MutableFloat;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static net.atlas.combatify.util.MethodHandler.arrowDisable;

public record BlockingType(BlockingTypeHandler handler, BlockingTypeData data) {
	public static final Codec<BlockingType> CODEC = RecordCodecBuilder.create(instance ->
		instance.group(BlockingTypeHandler.MAP_CODEC.forGetter(BlockingType::handler),
				BlockingTypeData.CODEC.fieldOf("data").forGetter(BlockingType::data))
			.apply(instance, BlockingType::new));
	public BlockingTypeData data() {
		return data;
	}
	public boolean canCrouchBlock() {
		return data.canCrouchBlock;
	}
	public boolean canBlockHit() {
		return data.canBlockHit;
	}
	public boolean canBeDisabled() {
		return data.canBeDisabled;
	}
	public boolean requireFullCharge() {
		return data.requireFullCharge;
	}
	public boolean defaultKbMechanics() {
		return data.defaultKbMechanics;
	}

	public boolean hasDelay() {
		return data.hasDelay;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof BlockingType that)) return false;
		return Objects.equals(data(), that.data()) && Objects.equals(handler(), that.handler());
	}

	@Override
	public int hashCode() {
		return Objects.hash(handler(), data());
	}

	public void block(ServerLevel serverLevel, LivingEntity instance, ItemStack blockingItem, DamageSource source, LocalFloatRef amount, LocalFloatRef protectedDamage, LocalBooleanRef blocked) {
		handler.block(serverLevel, instance, blockingItem, source, amount, protectedDamage, blocked);
	}

	public record BlockingTypeData(boolean canBeDisabled, boolean canCrouchBlock, boolean canBlockHit,
								   boolean requireFullCharge, boolean defaultKbMechanics, boolean hasDelay) {
		public static final StreamCodec<FriendlyByteBuf, BlockingTypeData> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, BlockingTypeData::canBeDisabled,
			ByteBufCodecs.BOOL, BlockingTypeData::canCrouchBlock,
			ByteBufCodecs.BOOL, BlockingTypeData::canBlockHit,
			ByteBufCodecs.BOOL, BlockingTypeData::requireFullCharge,
			ByteBufCodecs.BOOL, BlockingTypeData::defaultKbMechanics,
			ByteBufCodecs.BOOL, BlockingTypeData::hasDelay,
			BlockingTypeData::new);
		public static final Codec<BlockingTypeData> CODEC = RecordCodecBuilder.create(instance ->
			instance.group(Codec.BOOL.optionalFieldOf("can_be_disabled", true).forGetter(BlockingTypeData::canBeDisabled),
				Codec.BOOL.optionalFieldOf("can_crouch_block", true).forGetter(BlockingTypeData::canCrouchBlock),
				Codec.BOOL.optionalFieldOf("can_block_hit", false).forGetter(BlockingTypeData::canBlockHit),
				Codec.BOOL.optionalFieldOf("require_full_charge", true).forGetter(BlockingTypeData::requireFullCharge),
				Codec.BOOL.optionalFieldOf("default_kb_mechanics", true).forGetter(BlockingTypeData::defaultKbMechanics),
				Codec.BOOL.optionalFieldOf("has_shield_delay", true).forGetter(BlockingTypeData::hasDelay)).apply(instance, BlockingTypeData::new));

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof BlockingTypeData that)) return false;
            return canBeDisabled == that.canBeDisabled && canCrouchBlock == that.canCrouchBlock && canBlockHit == that.canBlockHit && requireFullCharge == that.requireFullCharge && defaultKbMechanics == that.defaultKbMechanics && hasDelay == that.hasDelay;
		}

		@Override
		public int hashCode() {
			return Objects.hash(canBeDisabled, canCrouchBlock, canBlockHit, requireFullCharge, defaultKbMechanics, hasDelay);
		}
	}
	public record BlockingTypeHandler(List<DamageParser> damageParsers, Optional<LootItemCondition> triggerPostBlockEffects, List<ConditionalEffect<ComponentModifier>> protectionModifiers, List<ComponentModifier> knockbackModifiers, boolean markBlocked) {
		public static final MapCodec<BlockingTypeHandler> MAP_CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(DamageParser.CODEC.listOf().fieldOf("damage_parsers").forGetter(BlockingTypeHandler::damageParsers),
					ConditionalEffect.conditionCodec(CustomLootContextParamSets.BLOCKED_DAMAGE).optionalFieldOf("trigger_post_block_effects").forGetter(BlockingTypeHandler::triggerPostBlockEffects),
					ConditionalEffect.codec(ComponentModifier.CODEC, CustomLootContextParamSets.BLOCKED_DAMAGE).listOf().fieldOf("protection_modifiers").forGetter(BlockingTypeHandler::protectionModifiers),
					ComponentModifier.CODEC.listOf().optionalFieldOf("knockback_modifiers", Collections.emptyList()).forGetter(BlockingTypeHandler::knockbackModifiers),
					Codec.BOOL.optionalFieldOf("mark_blocked", true).forGetter(BlockingTypeHandler::markBlocked))
				.apply(instance, BlockingTypeHandler::new));
		public void block(ServerLevel serverLevel, LivingEntity instance, ItemStack blockingItem, DamageSource source, LocalFloatRef amount, LocalFloatRef protectedDamage, LocalBooleanRef wasBlocked) {
			int blockingLevel = blockingItem.getOrDefault(CustomDataComponents.BLOCKING_LEVEL, 1);
			LootParams lootParams = new LootParams.Builder(serverLevel)
				.withParameter(LootContextParams.THIS_ENTITY, instance)
				.withParameter(LootContextParams.ENCHANTMENT_LEVEL, blockingLevel)
				.withParameter(LootContextParams.TOOL, blockingItem)
				.withParameter(LootContextParams.ORIGIN, instance.position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
				.create(CustomLootContextParamSets.BLOCKED_DAMAGE);
			LootContext context = new LootContext.Builder(lootParams).create(Optional.empty());
			MutableFloat protection = new MutableFloat(0);
			matchingConditionalEffects(protectionModifiers, context).forEach(componentModifier -> protection.setValue(componentModifier.modifyValue(protection.getValue(), blockingLevel, instance.getRandom())));
			ConfigurableItemData configurableItemData = MethodHandler.forItem(blockingItem.getItem());
			if (configurableItemData != null) {
				if (configurableItemData.blocker().blockStrength() != null) protection.setValue(configurableItemData.blocker().blockStrength().floatValue());
			}
			protection.setValue(CustomEnchantmentHelper.modifyShieldEffectiveness(blockingItem, instance.getRandom(), protection.getValue()));
			damageParsers.forEach(damageParserConditionalEffect -> {
				protectedDamage.set(damageParserConditionalEffect.parse(amount.get(), protection.getValue(), context));
				amount.set(Math.max(amount.get() - protectedDamage.get(), 0));
			});
			MethodHandler.hurtCurrentlyUsedShield(instance, protectedDamage.get());
			AtomicBoolean canTriggerPostBlockEffects = new AtomicBoolean(true);
			triggerPostBlockEffects.ifPresent(lootItemCondition -> canTriggerPostBlockEffects.set(lootItemCondition.test(context)));
			if (source.getDirectEntity() instanceof LivingEntity livingEntity && canTriggerPostBlockEffects.get())
				MethodHandler.blockedByShield(serverLevel, instance, livingEntity, source);
			switch (source.getDirectEntity()) {
				case Arrow arrow when Combatify.CONFIG.arrowDisableMode().satisfiesConditions(arrow) ->
					arrowDisable(instance, source, arrow, blockingItem);
				case SpectralArrow arrow when Combatify.CONFIG.arrowDisableMode().satisfiesConditions(arrow) ->
					arrowDisable(instance, source, arrow, blockingItem);
				case null, default -> {
					// Do nothing
				}
			}
			wasBlocked.set(markBlocked);
		}
		public static <T> List<T> matchingConditionalEffects(List<ConditionalEffect<T>> effects, LootContext lootContext) {
			return effects.stream().filter(componentModifierConditionalEffect -> componentModifierConditionalEffect.matches(lootContext)).map(ConditionalEffect::effect).toList();
		}
		public void appendTooltipInfo(Consumer<Component> writer, Player player, ItemStack stack) {
			List<Component> protection = Collections.emptyList();
			List<Component> knockback = Collections.emptyList();
			int blockingLevel = stack.getOrDefault(CustomDataComponents.BLOCKING_LEVEL, 1);
			List<ComponentModifier> intermediaryProtection = protectionModifiers.stream().map(ConditionalEffect::effect).filter(componentModifier -> componentModifier.matches(stack)).toList();
			if (!intermediaryProtection.isEmpty()) protection = intermediaryProtection.getFirst().tryCombine(new ArrayList<>(intermediaryProtection), blockingLevel, player.getRandom());
			List<ComponentModifier> intermediaryKnockback = knockbackModifiers.stream().filter(componentModifier -> componentModifier.matches(stack)).toList();
			if (!intermediaryKnockback.isEmpty()) knockback = intermediaryKnockback.getFirst().tryCombine(new ArrayList<>(intermediaryKnockback), blockingLevel, player.getRandom());
			ConfigurableItemData configurableItemData = MethodHandler.forItem(stack.getItem());
			if (configurableItemData != null) {
				if (configurableItemData.blocker().blockStrength() != null)
					protection = List.of(ComponentModifier.buildComponent(protectionModifiers.stream().map(ConditionalEffect::effect).toList().getFirst().tooltipComponent(), configurableItemData.blocker().blockStrength().floatValue()));
				if (configurableItemData.blocker().blockKbRes() != null)
					knockback = List.of(CommonComponents.space().append(
						Component.translatable("attribute.modifier.equals." + AttributeModifier.Operation.ADD_VALUE.id(),
							ItemAttributeModifiers.ATTRIBUTE_MODIFIER_FORMAT.format(configurableItemData.blocker().blockKbRes() * 10.0),
							Component.translatable("attribute.name.knockback_resistance"))).withStyle(ChatFormatting.DARK_GREEN));
			}
			if (protection.isEmpty() && knockback.isEmpty()) return;
			writer.accept(CommonComponents.EMPTY);
			writer.accept(Component.translatable("item.modifiers.use").withStyle(ChatFormatting.GRAY));
			protection.forEach(component -> writer.accept(CommonComponents.space().append(component).withStyle(ChatFormatting.DARK_GREEN)));
			knockback.forEach(component -> writer.accept(CommonComponents.space().append(component).withStyle(ChatFormatting.DARK_GREEN)));
		}
		public float getShieldKnockbackResistanceValue(ItemStack itemStack, RandomSource randomSource) {
			ConfigurableItemData configurableItemData = MethodHandler.forItem(itemStack.getItem());
			if (configurableItemData != null) {
				if (configurableItemData.blocker().blockKbRes() != null)
					return configurableItemData.blocker().blockKbRes().floatValue();
			}
			int blockingLevel = itemStack.getOrDefault(CustomDataComponents.BLOCKING_LEVEL, 1);
			MutableFloat knockbackResistance = new MutableFloat(0);
			knockbackModifiers.stream().filter(componentModifier -> componentModifier.matches(itemStack)).forEach(componentModifier -> knockbackResistance.setValue(componentModifier.modifyValue(knockbackResistance.getValue(), blockingLevel, randomSource)));
			return knockbackResistance.getValue();
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof BlockingTypeHandler that)) return false;
            return markBlocked == that.markBlocked && Objects.equals(damageParsers, that.damageParsers) && Objects.equals(triggerPostBlockEffects, that.triggerPostBlockEffects) && Objects.equals(protectionModifiers, that.protectionModifiers) && Objects.equals(knockbackModifiers, that.knockbackModifiers);
		}

		@Override
		public int hashCode() {
			return Objects.hash(damageParsers, triggerPostBlockEffects, protectionModifiers, knockbackModifiers, markBlocked);
		}
	}
}
