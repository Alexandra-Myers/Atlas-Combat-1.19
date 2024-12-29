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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

import static net.atlas.combatify.util.MethodHandler.getBlocker;

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
	public boolean shield(LivingEntity instance, DamageSource source, Operation<Boolean> original, @Local(ordinal = 0, argsOnly = true) ServerLevel serverLevel, @Local(ordinal = 0, argsOnly = true) LocalFloatRef amount, @Local(ordinal = 2) LocalFloatRef protectedDamage, @Local(ordinal = 0) LocalBooleanRef wasBlocked, @Share("blocked") LocalBooleanRef blocked) {
		ItemStack itemStack = MethodHandler.getBlockingItem(thisEntity).stack();
		if (amount.get() > 0.0F && original.call(instance, source)) {
			getBlocker(itemStack).block(serverLevel, instance, source, itemStack, amount, protectedDamage, wasBlocked);
		}
		blocked.set(wasBlocked.get());
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

	@ModifyReturnValue(method = "isDamageSourceBlocked", at = @At(value = "RETURN", ordinal = 0))
	public boolean isDamageSourceBlocked(boolean original) {
		return Combatify.CONFIG.shieldProtectionArc() == 360D || original;
	}

	@ModifyExpressionValue(method = "isDamageSourceBlocked", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;dot(Lnet/minecraft/world/phys/Vec3;)D"))
	public double modifyDotResultToGetRadians(double original) {
        return Combatify.CONFIG.shieldProtectionArc() == 180D ? original : original * Math.PI;
	}

	@ModifyExpressionValue(method = "isDamageSourceBlocked", at = @At(value = "CONSTANT", args = "doubleValue=0.0", ordinal = 1))
	public double modifyCompareValue(double original) {
		return Combatify.CONFIG.shieldProtectionArc() == 180D ? original : Math.toRadians(Combatify.CONFIG.shieldProtectionArc() - 180D);
	}

	@ModifyReturnValue(method = "getItemBlockingWith", at = @At("RETURN"))
	public ItemStack removeMojangStupidity(ItemStack original) {
		return (original == null || !(original.getItem() instanceof ShieldItem)) && !MethodHandler.getBlockingItem(thisEntity).stack().isEmpty() ? Items.SHIELD.getDefaultInstance() : original;
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
