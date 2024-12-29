package net.atlas.combatify.util;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.atlas.combatify.Combatify;
import net.atlas.combatify.component.CustomDataComponents;
import net.atlas.combatify.component.custom.Blocker;
import net.atlas.combatify.component.custom.CanSweep;
import net.atlas.combatify.config.*;
import net.atlas.combatify.config.item.ArmourStats;
import net.atlas.combatify.config.item.BlockingInformation;
import net.atlas.combatify.config.item.WeaponStats;
import net.atlas.combatify.enchantment.CustomEnchantmentHelper;
import net.atlas.combatify.extensions.Tier;
import net.atlas.combatify.item.LongSwordItem;
import net.atlas.combatify.item.TieredShieldItem;
import net.atlas.combatify.item.WeaponType;
import net.atlas.combatify.mixin.LivingEntityAccessor;
import net.atlas.combatify.util.blocking.BlockingType;
import net.atlas.combatify.util.blocking.BlockingTypeInit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.UseCooldown;
import net.minecraft.world.item.enchantment.Enchantable;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static net.minecraft.world.entity.LivingEntity.getSlotForHand;

public class MethodHandler {
	public static float getAttackStrengthScale(LivingEntity entity, float baseTime) {
		if (entity instanceof Player player)
			return player.getAttackStrengthScale(baseTime);
		return 2.0f;
	}
	public static Vec3 getNearestPointTo(AABB box, Vec3 vec3) {
		double x = Mth.clamp(vec3.x, box.minX, box.maxX);
		double y = Mth.clamp(vec3.y, box.minY, box.maxY);
		double z = Mth.clamp(vec3.z, box.minZ, box.maxZ);

		return new Vec3(x, y, z);
	}

	public static double calculateValue(@Nullable AttributeInstance attributeInstance, float damageBonus) {
		if(attributeInstance == null)
			return damageBonus;
		double attributeInstanceBaseValue = attributeInstance.getBaseValue() + damageBonus;

		for(AttributeModifier attributeModifier : attributeInstance.getModifiersOrEmpty(AttributeModifier.Operation.ADD_VALUE)) {
			attributeInstanceBaseValue += attributeModifier.amount();
		}

		return calculateValueFromBase(attributeInstance, attributeInstanceBaseValue);
	}
	public static double calculateValueFromBase(@Nullable AttributeInstance attributeInstance, double attributeInstanceBaseValue) {
		if(attributeInstance == null)
			return attributeInstanceBaseValue;

		double attributeInstanceFinalValue = attributeInstanceBaseValue;

		for(AttributeModifier attributeModifier2 : attributeInstance.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_BASE)) {
			attributeInstanceFinalValue += attributeInstanceBaseValue * attributeModifier2.amount();
		}

		for(AttributeModifier attributeModifier2 : attributeInstance.getModifiersOrEmpty(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)) {
			attributeInstanceFinalValue *= 1.0 + attributeModifier2.amount();
		}

		return attributeInstance.getAttribute().value().sanitizeValue(attributeInstanceFinalValue);
	}
	public static float getFatigueForTime(int f) {
		if (f < 60 || !Combatify.CONFIG.bowFatigue())
			return 0.5F;
		else
			return f >= 200 ? 10.5F : 0.5F + 10.0F * (float)(f - 60) / 140.0F;
	}
	public static void knockback(LivingEntity entity, double strength, double x, double z) {
		if (!Combatify.CONFIG.ctsKB()) {
			entity.knockback(strength, x, z);
			return;
		}
		double knockbackRes = entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		ItemStack blockingItem = getBlockingItem(entity).stack();
		Blocker blocker = getBlocker(blockingItem);
		boolean delay = getBlockingData(blockingItem, entity.level(), BlockingType::hasDelay) && Combatify.CONFIG.shieldDelay() > 0 && blockingItem.getUseDuration(entity) - entity.getUseItemRemainingTicks() < Combatify.CONFIG.shieldDelay();
		if (!blockingItem.isEmpty() && !delay) {
			if (!getBlockingData(blockingItem, entity.level(), BlockingType::defaultKbMechanics))
				knockbackRes = Math.max(knockbackRes, blocker.getShieldKnockbackResistanceValue(blockingItem, entity.level(), entity.getRandom()));
			else
				knockbackRes = Math.min(1.0, knockbackRes + blocker.getShieldKnockbackResistanceValue(blockingItem, entity.level(), entity.getRandom()));
		}

		strength *= 1.0 - knockbackRes;
		if (!(strength <= 0.0F)) {
			entity.hasImpulse = true;
			Vec3 delta = entity.getDeltaMovement();
			Vec3 diff = (new Vec3(x, 0.0, z)).normalize().scale(strength);
			entity.setDeltaMovement(delta.x / 2.0 - diff.x, entity.onGround() ? Math.min(0.4, strength * 0.75) : Math.min(0.4, delta.y + strength * 0.5), delta.z / 2.0 - diff.z);
		}
	}
	public static void projectileKnockback(LivingEntity entity, double strength, double x, double z) {
		double knockbackRes = entity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		ItemStack blockingItem = getBlockingItem(entity).stack();
		Blocker blocker = getBlocker(blockingItem);
		boolean delay = getBlockingData(blockingItem, entity.level(), BlockingType::hasDelay) && Combatify.CONFIG.shieldDelay() > 0 && blockingItem.getUseDuration(entity) - entity.getUseItemRemainingTicks() < Combatify.CONFIG.shieldDelay();
		if (!blockingItem.isEmpty() && !delay) {
			if (!getBlockingData(blockingItem, entity.level(), BlockingType::defaultKbMechanics))
				knockbackRes = Math.max(knockbackRes, blocker.getShieldKnockbackResistanceValue(blockingItem, entity.level(), entity.getRandom()));
			else
				knockbackRes = Math.min(1.0, knockbackRes + blocker.getShieldKnockbackResistanceValue(blockingItem, entity.level(), entity.getRandom()));
		}

		strength *= 1.0 - knockbackRes;
		if (!(strength <= 0.0F)) {
			entity.hasImpulse = true;
			Vec3 delta = entity.getDeltaMovement();
			Vec3 diff = (new Vec3(x, 0.0, z)).normalize().scale(strength);
			entity.setDeltaMovement(delta.x / 2.0 - diff.x, Math.min(0.4, strength * 0.75), delta.z / 2.0 - diff.z);
		}
	}
	private static List<BlockPos> clip(Level level, ClipContext clipContext) {
		List<BlockPos> blockPosList = new ArrayList<>();
		traverseBlocks(clipContext.getFrom(), clipContext.getTo(), clipContext, (clipContextx, blockPos) -> {
			BlockState blockState = level.getBlockState(blockPos);
            if (blockState.canOcclude() && blockState.getBlock().hasCollision)
				blockPosList.add(blockPos);
		});
		return blockPosList;
	}
	private static <C> void traverseBlocks(Vec3 vec3, Vec3 vec32, C object, BiConsumer<C, BlockPos> biConsumer) {
		if (!vec3.equals(vec32)) {
			double d = Mth.lerp(-1.0E-7, vec3.x, vec32.x);
			double e = Mth.lerp(-1.0E-7, vec3.y, vec32.y);
			double f = Mth.lerp(-1.0E-7, vec3.z, vec32.z);
			int g = Mth.floor(d);
			int h = Mth.floor(e);
			int i = Mth.floor(f);
			BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos(g, h, i);
			biConsumer.accept(object, mutableBlockPos);
			double j = Mth.lerp(-1.0E-7, vec32.x, vec3.x) - d;
			double k = Mth.lerp(-1.0E-7, vec32.y, vec3.y) - e;
			double l = Mth.lerp(-1.0E-7, vec32.z, vec3.z) - f;
			int m = Mth.sign(j);
			int n = Mth.sign(k);
			int o = Mth.sign(l);
			double p = m == 0 ? Double.MAX_VALUE : (double)m / j;
			double q = n == 0 ? Double.MAX_VALUE : (double)n / k;
			double r = o == 0 ? Double.MAX_VALUE : (double)o / l;
			double s = p * (m > 0 ? 1.0 - Mth.frac(d) : Mth.frac(d));
			double t = q * (n > 0 ? 1.0 - Mth.frac(e) : Mth.frac(e));
			double u = r * (o > 0 ? 1.0 - Mth.frac(f) : Mth.frac(f));

			do {
				if (s < t) {
					if (s < u) {
						g += m;
						s += p;
					} else {
						i += o;
						u += r;
					}
				} else if (t < u) {
					h += n;
					t += q;
				} else {
					i += o;
					u += r;
				}

				biConsumer.accept(object, mutableBlockPos.set(g, h, i));
			} while(s <= 1.0 || t <= 1.0 || u <= 1.0);
		}
	}
	public static List<BlockPos> pickFromPos(Entity entity, double reach) {
		Vec3 viewVector = entity.getViewVector(1);
		Vec3 pos = entity.getEyePosition(1);
		Vec3 endPos = pos.add(viewVector.scale(reach));
		return clip(entity.level(), new ClipContext(pos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
	}
	public static EntityHitResult rayTraceEntity(Entity entity, float partialTicks, double blockReachDistance) {
		Vec3 from = entity.getEyePosition(partialTicks);
		Vec3 look = entity.getViewVector(partialTicks);
		Vec3 to = from.add(look.x * blockReachDistance, look.y * blockReachDistance, look.z * blockReachDistance);

		return ProjectileUtil.getEntityHitResult(
			entity.level(),
			entity,
			from,
			to,
			new AABB(from, to),
			EntitySelector.NO_CREATIVE_OR_SPECTATOR.and(e -> e != null
				&& e.isPickable()
				&& e instanceof LivingEntity)
		);
	}
	public static HitResult redirectResult(Player player, HitResult instance) {
		if (Combatify.CONFIG.swingThroughGrass() && instance.getType() == HitResult.Type.BLOCK) {
			BlockHitResult blockHitResult = (BlockHitResult) instance;
			BlockPos blockPos = blockHitResult.getBlockPos();
			Level level = player.level();
			boolean bl = !level.getBlockState(blockPos).canOcclude() && !level.getBlockState(blockPos).getBlock().hasCollision;
			EntityHitResult rayTraceResult = MethodHandler.rayTraceEntity(player, 1.0F, getCurrentAttackReach(player, 0.0F));
			if (rayTraceResult != null && bl) {
				double distanceTo = player.distanceTo(rayTraceResult.getEntity());
				List<BlockPos> blockPosList = pickFromPos(player, distanceTo);
				if (!blockPosList.isEmpty())
					return instance;
				return rayTraceResult;
			} else {
				return instance;
			}
		}
		return instance;
	}
	public static void hurtCurrentlyUsedShield(LivingEntity livingEntity, float f) {
		if (!(livingEntity instanceof Player player)) return;
		FakeUseItem fakeUseItem = getBlockingItem(player);
		ItemStack blockingItem = fakeUseItem.stack();
		if (!blockingItem.isEmpty()) {
			if (!player.level().isClientSide) {
				player.awardStat(Stats.ITEM_USED.get(blockingItem.getItem()));
			}

			if (f >= 3.0F) {
				int i = 1 + Mth.floor(f);
				InteractionHand interactionHand = fakeUseItem.useHand();
				blockingItem.hurtAndBreak(i, player, getSlotForHand(interactionHand));
				fakeUseItem = getBlockingItem(player);
				if (fakeUseItem.stack().isEmpty()) {
					if (interactionHand == InteractionHand.MAIN_HAND) player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
					else player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);

					if (fakeUseItem.isReal()) ((LivingEntityAccessor)player).setUseItem(ItemStack.EMPTY);
					player.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + player.level().random.nextFloat() * 0.4F);
				}
			}
		}
	}
	public static void disableShield(LivingEntity attacker, LivingEntity target, DamageSource damageSource, ItemStack blockingItem) {
		ItemStack attackingItem = attacker.getMainHandItem();
		double piercingLevel = getPiercingLevel(attackingItem);
		if (!(target.level() instanceof ServerLevel serverLevel)) piercingLevel += CustomEnchantmentHelper.getBreach(attackingItem, attacker.getRandom());
		else piercingLevel += CustomEnchantmentHelper.getArmorModifier(serverLevel, attackingItem, target, damageSource);
		if (!(Combatify.CONFIG.armorPiercingDisablesShields() || attacker.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof LongSwordItem))
			piercingLevel = 0;
		boolean canDisable = attacker.canDisableShield() || piercingLevel > 0;
		float damage = Combatify.CONFIG.shieldDisableTime().floatValue();
		ConfigurableEntityData configurableEntityData;
		if ((configurableEntityData = forEntity(target)) != null) {
			if (configurableEntityData.shieldDisableTime() != null)
				damage = configurableEntityData.shieldDisableTime().floatValue();
		}
		modifyTime: {
			if (attacker.level() instanceof ServerLevel serverLevel) {
				if (canDisable) {
					damage = CustomEnchantmentHelper.modifyShieldDisable(serverLevel, null, target, attacker, damageSource, damage);
					break modifyTime;
				}
				float newDamage = CustomEnchantmentHelper.modifyShieldDisable(serverLevel, null, target, attacker, damageSource, 0);
				if (newDamage > 0) {
					damage = newDamage;
					canDisable = true;
				}
			}
		}
		if (canDisable && damage > 0 && getBlockingData(blockingItem, attacker.level(), BlockingType::canBeDisabled)) {
			if (piercingLevel > 0)
				attacker.combatify$setPiercingNegation(piercingLevel);
			disableShield(target, damage, blockingItem);
		}
	}
	public static void arrowDisable(LivingEntity target, DamageSource damageSource, AbstractArrow abstractArrow, ItemStack blockingItem) {
		if (!getBlockingData(blockingItem, target.level(), BlockingType::canBeDisabled)) return;
		float damage = Combatify.CONFIG.shieldDisableTime().floatValue();
		ConfigurableEntityData configurableEntityData;
		if ((configurableEntityData = forEntity(target)) != null) {
			if (configurableEntityData.shieldDisableTime() != null)
				damage = configurableEntityData.shieldDisableTime().floatValue();
		}
		if (target.level() instanceof ServerLevel serverLevel) {
			damage = CustomEnchantmentHelper.modifyShieldDisable(serverLevel, abstractArrow.getPickupItemStackOrigin(), target, abstractArrow, damageSource, damage);
		}
		disableShield(target, Math.max(damage, 0), blockingItem);
	}
	public static void disableShield(LivingEntity target, float damage, ItemStack item) {
		getCooldowns(target).addCooldown(item, (int)(damage * 20.0F));
		if (item.getItem() instanceof TieredShieldItem)
			for (TieredShieldItem tieredShieldItem : Combatify.shields)
				if (item.getItem() != tieredShieldItem)
					getCooldowns(target).addCooldown(new ItemStack(tieredShieldItem), (int)(damage * 20.0F));
		target.stopUsingItem();
		target.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + target.level().random.nextFloat() * 0.4F);
		target.level().broadcastEntityEvent(target, (byte)30);
	}
	public static FakeUseItem getBlockingItem(LivingEntity entity) {
		if (entity.isUsingItem() && !entity.getUseItem().isEmpty()) {
			if (entity.getUseItem().getUseAnimation() == ItemUseAnimation.BLOCK) {
				return new FakeUseItem(entity.getUseItem(), entity.getUsedItemHand(), true);
			}
		} else if (((entity.onGround() && entity.isCrouching()) || entity.isPassenger()) && entity.combatify$hasEnabledShieldOnCrouch()) {
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack stack = entity.getItemInHand(hand);
				boolean stillRequiresCharge = Combatify.CONFIG.shieldOnlyWhenCharged() && entity instanceof Player player && player.getAttackStrengthScale(1.0F) < Combatify.CONFIG.shieldChargePercentage() / 100F && getBlockingData(stack, entity.level(), BlockingType::requireFullCharge);
				boolean canUse = !(entity instanceof Player player) || getBlocker(stack).canUse(stack, entity.level(), player, hand);
				if (!stillRequiresCharge && !stack.isEmpty() && stack.getUseAnimation() == ItemUseAnimation.BLOCK && !isItemOnCooldown(entity, stack) && getBlockingData(stack, entity.level(), BlockingType::canCrouchBlock) && canUse) {
					return new FakeUseItem(stack, hand, false);
				}
			}
		}

		return new FakeUseItem(ItemStack.EMPTY, null, true);
	}
	public static boolean isItemOnCooldown(LivingEntity entity, ItemStack var1) {
		return getCooldowns(entity).isOnCooldown(var1);
	}
	public static double getCurrentAttackReach(Player player, float baseTime) {
		@Nullable final var attackRange = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		double chargedBonus = 0;
		double baseAttackRange = Combatify.CONFIG.attackReach() ? 2.5 : 3;
		float strengthScale = player.getAttackStrengthScale(baseTime);
		float charge = Combatify.CONFIG.chargedAttacks() ? 1.95F : 0.9F;
		if (attackRange != null) {
			Item item = player.getItemInHand(InteractionHand.MAIN_HAND).getItem();
			chargedBonus = item.getChargedAttackBonus();
			AttributeModifier modifier = new AttributeModifier(Combatify.CHARGED_REACH_ID, chargedBonus, AttributeModifier.Operation.ADD_VALUE);
			if (strengthScale > charge && !player.isCrouching() && Combatify.CONFIG.chargedReach())
				attackRange.addOrUpdateTransientModifier(modifier);
			else
				attackRange.removeModifier(modifier);
		}
		if (strengthScale < charge || player.isCrouching() || !Combatify.CONFIG.chargedReach()) {
			chargedBonus = 0;
		}
		return (attackRange != null) ? attackRange.getValue() : baseAttackRange + chargedBonus;
	}
	public static void voidReturnLogic(ThrownTrident trident, EntityDataAccessor<Byte> ID_LOYALTY) {
		int j = trident.getEntityData().get(ID_LOYALTY);
		if (Combatify.CONFIG.tridentVoidReturn() && trident.getY() <= -65 && j > 0) {
			if (!trident.isAcceptibleReturnOwner()) {
				trident.discard();
			} else {
				trident.setNoPhysics(true);
				Vec3 vec3 = trident.getEyePosition().subtract(trident.position());
				trident.setPosRaw(trident.getX(), trident.getY() + vec3.y * 0.015 * j, trident.getZ());
				if (trident.level().isClientSide) {
					trident.yOld = trident.getY();
				}

				double d = 0.05 * j;
				trident.setDeltaMovement(trident.getDeltaMovement().scale(0.95).add(vec3.normalize().scale(d)));
				if (trident.clientSideReturnTridentTickCount == 0) {
					trident.playSound(SoundEvents.TRIDENT_RETURN, 10.0F, 1.0F);
				}

				++trident.clientSideReturnTridentTickCount;
			}
		}
	}
	public static Vec3 project(Vec3 originalVec, Vec3 newVec) {
	    Vec3 normalized = newVec.normalize();
	    double d = originalVec.dot(newVec);
	    return normalized.multiply(d, d, d);
	}

	public static void blockedByShield(ServerLevel serverLevel, LivingEntity target, LivingEntity attacker, DamageSource damageSource) {
		ItemStack blockingItem = MethodHandler.getBlockingItem(target).stack();
		EquipmentSlot equipmentSlot = target.getEquipmentSlotForItem(blockingItem);
		getBlocker(blockingItem).doEffect(serverLevel, equipmentSlot, blockingItem, target, attacker, damageSource);
	}
	public static ItemCooldowns createItemCooldowns() {
		return new ItemCooldowns();
	}
	public static ItemCooldowns getCooldowns(LivingEntity livingEntity) {
		if (livingEntity instanceof Player player) return player.getCooldowns();
		return livingEntity.combatify$getFallbackCooldowns();
	}

	public static boolean isMobGuarding(Mob mob) {
		return mob.combatify$isGuarding();
	}
	public static float getPinchHealth(LivingEntity livingEntity, Difficulty difficulty) {
		return switch (difficulty) {
			case PEACEFUL, EASY -> 0.0F;
			case NORMAL -> livingEntity.getMaxHealth() / 4;
			case HARD -> livingEntity.getMaxHealth() / 2;
		};
	}

	public static boolean shouldSprintToCloseInOnTarget(Difficulty difficulty, double change) {
		if (difficulty == Difficulty.PEACEFUL || difficulty == Difficulty.EASY) return false;
		return change < (difficulty == Difficulty.NORMAL ? -0.25 : 0);
	}

	public static boolean processSprintAbility(Entity entity, Operation<Boolean> base) {
		return switch (entity) {
			case AbstractPiglin ignored -> true;
			case AbstractSkeleton skeleton -> {
				ItemStack itemstack = skeleton.getItemInHand(ProjectileUtil.getWeaponHoldingHand(skeleton, Items.BOW));
				yield !itemstack.is(Items.BOW);
			}
			case Zombie zombie -> !(zombie.isEyeInFluid(FluidTags.WATER) || zombie.isEyeInFluid(FluidTags.LAVA));
			case null, default -> base.call();
		};
	}

	public static double getPiercingLevel(ItemStack itemStack) {
		return itemStack.getOrDefault(CustomDataComponents.PIERCING_LEVEL, 0F);
	}

	@SuppressWarnings("deprecation")
	public static ConfigurableItemData forItem(Item item) {
		if (Combatify.ITEMS != null && !Combatify.ITEMS.isModifying) {
			List<ConfigurableItemData> results = new ArrayList<>();
			Combatify.ITEMS.configuredItems.forEach(configDataWrapper -> {
				ConfigurableItemData result = configDataWrapper.match(item.builtInRegistryHolder());
				if (result != null) results.add(result);
			});
			Double damage = null;
			Double speed = null;
			Double reach = null;
			Double chargedReach = null;
			Integer stackSize = null;
			UseCooldown cooldown = null;
			WeaponType type = null;
			Blocker blocking = null;
			Double blockStrength = null;
			Double blockKbRes = null;
			Integer blockingLevel = null;
			Enchantable enchantable = null;
			Integer useDuration = null;
			Double piercingLevel = null;
			CanSweep canSweep = null;
			Tier tier = null;
			ArmourVariable durability = ArmourVariable.EMPTY;
			ArmourVariable defense = ArmourVariable.EMPTY;
			Double toughness = null;
			Double armourKbRes = null;
			TagKey<Item> repairItems = null;
			TagKey<Block> toolMineable = null;
			Tool tool = null;
			ItemAttributeModifiers itemAttributeModifiers = ItemAttributeModifiers.EMPTY;
			for (ConfigurableItemData configurableItemData : results) {
				damage = conditionalChange(configurableItemData.weaponStats().attackDamage(), damage);
				speed = conditionalChange(configurableItemData.weaponStats().attackSpeed(), speed);
				reach = conditionalChange(configurableItemData.weaponStats().attackReach(), reach);
				chargedReach = conditionalChange(configurableItemData.weaponStats().chargedReach(), chargedReach);
				piercingLevel = conditionalChange(configurableItemData.weaponStats().piercingLevel(), piercingLevel);
				canSweep = conditionalChange(configurableItemData.weaponStats().canSweep(), canSweep);
				type = conditionalChange(configurableItemData.weaponStats().weaponType(), type);
				blocking = conditionalChange(configurableItemData.blocker().blocking(), blocking);
				blockStrength = conditionalChange(configurableItemData.blocker().blockStrength(), blockStrength);
				blockKbRes = conditionalChange(configurableItemData.blocker().blockKbRes(), blockKbRes);
				blockingLevel = conditionalChange(configurableItemData.blocker().blockingLevel(), blockingLevel);
				durability = configurableItemData.armourStats().durability().isEmpty() ? durability : configurableItemData.armourStats().durability();
				defense = configurableItemData.armourStats().defense().isEmpty() ? defense : configurableItemData.armourStats().defense();
				toughness = conditionalChange(configurableItemData.armourStats().toughness(), toughness);
				armourKbRes = conditionalChange(configurableItemData.armourStats().armourKbRes(), armourKbRes);
				stackSize = conditionalChange(configurableItemData.stackSize(), stackSize);
				cooldown = conditionalChange(configurableItemData.cooldown(), cooldown);
				enchantable = conditionalChange(configurableItemData.enchantable(), enchantable);
				useDuration = conditionalChange(configurableItemData.useDuration(), useDuration);
				tier = conditionalChange(configurableItemData.tier(), tier);
				repairItems = conditionalChange(configurableItemData.repairItems(), repairItems);
				toolMineable = conditionalChange(configurableItemData.toolMineableTag(), toolMineable);
				tool = conditionalChange(configurableItemData.tool(), tool);
				itemAttributeModifiers = configurableItemData.itemAttributeModifiers().equals(ItemAttributeModifiers.EMPTY) ? itemAttributeModifiers : configurableItemData.itemAttributeModifiers();
			}
			WeaponStats weaponStats = new WeaponStats(damage, speed, reach, chargedReach, piercingLevel, type, canSweep);
			BlockingInformation blocker = new BlockingInformation(blocking, blockStrength, blockKbRes, blockingLevel);
			ArmourStats armourStats = new ArmourStats(durability, defense, toughness, armourKbRes);
			ConfigurableItemData configurableItemData = new ConfigurableItemData(weaponStats, stackSize, cooldown, blocker, enchantable, useDuration, tier, armourStats, repairItems, toolMineable, tool, itemAttributeModifiers);
			if (configurableItemData.equals(ConfigurableItemData.EMPTY)) return null;
			return configurableItemData;
		}
		return null;
	}

	public static ConfigurableWeaponData forWeapon(WeaponType weaponType) {
		if (Combatify.ITEMS != null && !Combatify.ITEMS.isModifying) {
			List<ConfigurableWeaponData> results = new ArrayList<>();
			Combatify.ITEMS.configuredWeapons.forEach(configDataWrapper -> {
				ConfigurableWeaponData result = configDataWrapper.match(weaponType);
				if (result != null) results.add(result);
			});
			Double damageOffset = null;
			Double speed = null;
			Double reach = null;
			Boolean tierable = null;
			Double chargedReach = null;
			Blocker blocking = null;
			Double piercingLevel = null;
			CanSweep canSweep = null;
			for (ConfigurableWeaponData configurableWeaponData : results) {
				tierable = conditionalChange(configurableWeaponData.tiered(), tierable);
				damageOffset = conditionalChange(configurableWeaponData.attackDamage(), damageOffset);
				speed = conditionalChange(configurableWeaponData.attackSpeed(), speed);
				reach = conditionalChange(configurableWeaponData.attackReach(), reach);
				chargedReach = conditionalChange(configurableWeaponData.chargedReach(), chargedReach);
				blocking = conditionalChange(configurableWeaponData.blocking(), blocking);
				piercingLevel = conditionalChange(configurableWeaponData.piercingLevel(), piercingLevel);
				canSweep = conditionalChange(configurableWeaponData.canSweep(), canSweep);
			}
			ConfigurableWeaponData configurableWeaponData = new ConfigurableWeaponData(damageOffset, speed, reach, chargedReach, piercingLevel, tierable, canSweep, blocking);
			if (configurableWeaponData.equals(ConfigurableWeaponData.EMPTY)) return null;
			return configurableWeaponData;
		}
		return null;
	}

	@SuppressWarnings("deprecation")
	public static ConfigurableEntityData forEntityType(EntityType<?> entityType) {
		if (Combatify.ITEMS != null && !Combatify.ITEMS.isModifying) {
			List<ConfigurableEntityData> results = new ArrayList<>();
			Combatify.ITEMS.configuredEntities.forEach(configDataWrapper -> {
				ConfigurableEntityData result = configDataWrapper.match(entityType.builtInRegistryHolder());
				if (result != null) results.add(result);
			});
			Integer attackInterval = null;
			Double shieldDisableTime = null;
			Boolean isMiscEntity = null;
			for (ConfigurableEntityData configurableEntityData : results) {
				attackInterval = conditionalChange(configurableEntityData.attackInterval(), attackInterval);
				shieldDisableTime = conditionalChange(configurableEntityData.shieldDisableTime(), shieldDisableTime);
				isMiscEntity = conditionalChange(configurableEntityData.isMiscEntity(), isMiscEntity);
			}
			ConfigurableEntityData configurableEntityData = new ConfigurableEntityData(attackInterval, shieldDisableTime, isMiscEntity);
			if (configurableEntityData.equals(ConfigurableEntityData.EMPTY)) return null;
			return configurableEntityData;
		}
		return null;
	}

	public static ConfigurableEntityData forEntity(Entity entity) {
		return forEntityType(entity.getType());
	}

	public static <T> T conditionalChange(T source, T destination) {
		return source == null ? destination : source;
	}

	public static Holder<BlockingType> getBlockingTypeHolder(ItemStack itemStack, Level level) {
		return getBlocker(itemStack).blockingTypeHolder(level.holderLookup(BlockingTypeInit.BLOCKING_TYPE));
	}

	public static Blocker getBlocker(ItemStack itemStack) {
		return itemStack.getOrDefault(CustomDataComponents.BLOCKER, Blocker.EMPTY);
	}

	public static boolean getBlockingData(ItemStack itemStack, Level level, Function<BlockingType, Boolean> defaultVal) {
		Holder<BlockingType> holder = getBlockingTypeHolder(itemStack, level);
		if (holder == null) return false;
		return defaultVal.apply(holder.value());
	}
}
