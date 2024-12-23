package net.atlas.combatify.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.atlas.combatify.Combatify;
import net.atlas.combatify.config.EatingInterruptionMode;
import net.atlas.combatify.extensions.*;
import net.atlas.combatify.networking.NetworkingHandler;
import net.atlas.combatify.util.MethodHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;
import java.util.UUID;

@Mixin(value = LivingEntity.class, priority = 1400)
public abstract class LivingEntityMixin extends Entity implements LivingEntityExtensions {
	@Unique
	private double piercingNegation;
	@Unique
	private ItemCooldowns fallbackCooldowns = MethodHandler.createItemCooldowns();

	public LivingEntityMixin(EntityType<?> entityType, Level level) {
		super(entityType, level);
	}

	@Unique
	LivingEntity thisEntity = LivingEntity.class.cast(this);

	@Shadow
	protected int useItemRemaining;

	@Shadow
	public abstract ItemStack getUseItem();

	@Shadow
	public abstract void indicateDamage(double d, double e);

	@Override
	public ItemCooldowns combatify$getFallbackCooldowns() {
		return fallbackCooldowns;
	}
	@Inject(method = "tick", at = @At(value = "RETURN"))
	public void tickCooldowns(CallbackInfo ci) {
		fallbackCooldowns.tick();
	}

	@SuppressWarnings("unused")
	@ModifyReturnValue(method = "isBlocking", at = @At(value="RETURN"))
	public boolean isBlocking(boolean original) {
		return !MethodHandler.getBlockingItem(thisEntity).stack().isEmpty();
	}

	@Inject(method = "blockedByShield", at = @At(value="HEAD"), cancellable = true)
	public void blockedByShield(LivingEntity target, CallbackInfo ci) {
		ci.cancel();
	}
	@Override
	public void combatify$setPiercingNegation(double negation) {
		piercingNegation = negation;
	}
	@Override
	public double combatify$getPiercingNegation() {
		return piercingNegation;
	}
	@ModifyConstant(method = "handleDamageEvent", constant = @Constant(intValue = 20, ordinal = 0))
	private int syncInvulnerability(int x) {
		return 10;
	}

	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isDamageSourceBlocked(Lnet/minecraft/world/damagesource/DamageSource;)Z"))
	public boolean shield(LivingEntity instance, DamageSource source, Operation<Boolean> original, @Local(ordinal = 0, argsOnly = true) LocalFloatRef amount, @Local(ordinal = 1) LocalFloatRef f, @Local(ordinal = 2) LocalFloatRef g, @Local(ordinal = 0) LocalBooleanRef bl, @Share("blocked") LocalBooleanRef blocked) {
		ItemStack itemStack = MethodHandler.getBlockingItem(thisEntity).stack();
		Item shieldItem = itemStack.getItem();
		if (amount.get() > 0.0F && original.call(instance, source)) {
			if (shieldItem.combatify$getBlockingType().hasDelay() && Combatify.CONFIG.shieldDelay() > 0 && itemStack.getUseDuration(thisEntity) - useItemRemaining < Combatify.CONFIG.shieldDelay()) {
				if (Combatify.CONFIG.disableDuringShieldDelay())
					if (source.getDirectEntity() instanceof LivingEntity attacker)
						MethodHandler.disableShield(attacker, instance, source, itemStack);
				return false;
			}
			shieldItem.combatify$getBlockingType().block(instance, null, itemStack, source, amount, f, g, bl);
		}
		blocked.set(bl.get());
		return false;
	}
	@ModifyExpressionValue(method = "hurtServer", at = @At(value = "CONSTANT", args = "intValue=20", ordinal = 0))
	public int changeIFrames(int original, @Local(ordinal = 0, argsOnly = true) final DamageSource source, @Local(ordinal = 0, argsOnly = true) final float amount) {
		Entity entity2 = source.getEntity();
		int invulnerableTime = original - 10;
		if (!Combatify.CONFIG.instaAttack() && Combatify.CONFIG.iFramesBasedOnWeapon() && entity2 instanceof Player player && !(player.getAttributeValue(Attributes.ATTACK_SPEED) - 1.5 >= 20 && !Combatify.CONFIG.attackSpeed())) {
			int base = (int) Math.min(player.getCurrentItemAttackStrengthDelay(), invulnerableTime);
			invulnerableTime = base >= 4 && !Combatify.CONFIG.canAttackEarly() ? base - 2 : base;
		}

		if (source.is(DamageTypeTags.IS_PROJECTILE) && !Combatify.CONFIG.projectilesHaveIFrames())
			invulnerableTime = 0;
		if (source.is(DamageTypes.MAGIC) && !Combatify.CONFIG.magicHasIFrames())
			invulnerableTime = 0;
		return invulnerableTime;
	}
	@Inject(method = "hurtServer", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I", ordinal = 0, shift = At.Shift.AFTER))
	public void injectEatingInterruption(ServerLevel serverLevel, DamageSource source, float f, CallbackInfoReturnable<Boolean> cir) {
		Entity entity = source.getEntity();
		boolean canInterrupt = thisEntity.isUsingItem() && (getUseItem().getUseAnimation() == ItemUseAnimation.EAT || getUseItem().getUseAnimation() == ItemUseAnimation.DRINK);
		if (entity instanceof LivingEntity && canInterrupt) {
			useItemRemaining = switch (Combatify.CONFIG.eatingInterruptionMode()) {
                case FULL_RESET -> thisEntity.getUseItem().getUseDuration(thisEntity);
				case DELAY -> useItemRemaining + invulnerableTime;
				case null, default -> useItemRemaining;
			};
			if (Combatify.CONFIG.eatingInterruptionMode() != EatingInterruptionMode.OFF) {
				for (UUID playerUUID : Combatify.moddedPlayers)
					if (serverLevel.getPlayerByUUID(playerUUID) instanceof ServerPlayer serverPlayer)
						ServerPlayNetworking.send(serverPlayer, new NetworkingHandler.RemainingUseSyncPacket(getId(), useItemRemaining));
			}
		}
	}
	@ModifyExpressionValue(method = "hurtServer", at = @At(value = "CONSTANT", args = "floatValue=10.0F", ordinal = 0))
	public float changeIFrames(float constant) {
		return constant - 10;
	}
	@WrapOperation(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
	public void modifyKB(LivingEntity instance, double d, double e, double f, Operation<Void> original, @Local(ordinal = 0, argsOnly = true) final DamageSource source, @Local(argsOnly = true) float amount, @Share("blocked") LocalBooleanRef bl) {
		if (bl.get() && amount > 0)
			indicateDamage(e, f);
		if ((Combatify.CONFIG.fishingHookKB() && source.getDirectEntity() instanceof FishingHook) || (!source.is(DamageTypeTags.IS_PROJECTILE) && Combatify.CONFIG.midairKB()))
			MethodHandler.projectileKnockback(instance, d, e, f);
		else if (Combatify.CONFIG.ctsKB())
			MethodHandler.knockback(instance, d, e, f);
		else
			original.call(instance, d, e, f);
	}

	@ModifyExpressionValue(method = "startUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isUsingItem()Z"))
	public boolean addCooldownCheck(boolean original, @Local(ordinal = 0) ItemStack itemStack) {
		return original || MethodHandler.getCooldowns(thisEntity).isOnCooldown(itemStack);
	}

	@ModifyExpressionValue(method = "isDamageSourceBlocked", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/AbstractArrow;getPierceLevel()B"))
	public byte isDamageSourceBlocked(byte original) {
		return Combatify.CONFIG.arrowDisableMode().pierceArrowsBlocked() ? 0 : original;
	}

	@Inject(method = "isDamageSourceBlocked", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;calculateViewVector(FF)Lnet/minecraft/world/phys/Vec3;"), cancellable = true)
	public void isDamageSourceBlocked(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
		Vec3 currentVector = this.calculateViewVector(0.0F, this.getYHeadRot()).normalize();
		Vec3 sourceVector = Objects.requireNonNull(source.getSourcePosition()).vectorTo(this.position());
		sourceVector = (new Vec3(sourceVector.x, 0.0, sourceVector.z)).normalize();
		cir.setReturnValue(sourceVector.dot(currentVector) * 3.1415927410125732 < -0.8726646304130554);
	}
	@Override
	public boolean combatify$hasEnabledShieldOnCrouch() {
		return true;
	}

	@Override
	public void combatify$setUseItemRemaining(int ticks) {
		this.useItemRemaining = ticks;
	}
}
