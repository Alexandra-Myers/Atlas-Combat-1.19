package net.alexandra.atlas.atlas_combat.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.alexandra.atlas.atlas_combat.AtlasCombat;
import net.alexandra.atlas.atlas_combat.extensions.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import net.rizecookey.cookeymod.CookeyMod;
import net.rizecookey.cookeymod.config.category.AnimationsCategory;
import net.rizecookey.cookeymod.config.category.HudRenderingCategory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandMixin implements IItemInHandRenderer {
	@Shadow
	@Final
	private Minecraft minecraft;
	@Unique
	private PoseStack poseStack;
	@Unique
	private HumanoidArm humanoidArm;
	@Unique
	private float i;
	@Unique
	private int q;
	@Unique
	private float f;
	@Unique
	private boolean bow = false;
	@Unique
	private ItemStack itemStack;
	HudRenderingCategory hudRenderingCategory = (HudRenderingCategory)CookeyMod.getInstance().getConfig().getCategory(HudRenderingCategory.class);

	@Shadow
	protected abstract void applyItemArmAttackTransform(PoseStack matrices, HumanoidArm arm, float swingProgress);

	@Shadow
	protected abstract void applyItemArmTransform(PoseStack matrices, HumanoidArm arm, float equipProgress);

	AnimationsCategory animationsCategory = CookeyMod.getInstance().getConfig().getCategory(AnimationsCategory.class);

	@Shadow
	public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemTransforms.TransformType renderMode, boolean leftHanded, PoseStack matrices, MultiBufferSource vertexConsumers, int light);

	@Shadow
	protected abstract void applyEatTransform(PoseStack poseStack, float f, HumanoidArm humanoidArm, ItemStack itemStack);

	@Shadow
	protected abstract void renderTwoHandedMap(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float f, float g, float h);

	@Shadow
	protected abstract void renderOneHandedMap(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float f, HumanoidArm humanoidArm, float g, ItemStack itemStack);

	@Shadow
	protected abstract void renderPlayerArm(PoseStack poseStack, MultiBufferSource multiBufferSource, int i, float f, float g, HumanoidArm humanoidArm);
	@ModifyVariable(method = "tick", slice = @Slice(
			from = @At(value = "JUMP", ordinal = 3)
	), at = @At(value = "FIELD", ordinal = 0))
	public float modifyArmHeight(float f) {
		f *= 0.5;
		f = f * f * f * 0.25F + 0.75F;
		double offset = this.hudRenderingCategory.attackCooldownHandOffset.get();
		return (float)((double)f * (1.0 - offset) + offset);
	}

	@Inject(method = "renderArmWithItem", at = @At(value = "HEAD"), cancellable = true)
	private void renderArmWithItem(AbstractClientPlayer abstractClientPlayer, float f, float g, InteractionHand interactionHand, float h, ItemStack itemStack, float i, PoseStack poseStack, MultiBufferSource multiBufferSource, int j, CallbackInfo ci) {
		if (AtlasCombat.CONFIG.swordBlocking()) {
			if (abstractClientPlayer.getUsedItemHand() == interactionHand && !((LivingEntityExtensions)abstractClientPlayer).getBlockingItem().isEmpty() && ((LivingEntityExtensions)abstractClientPlayer).getBlockingItem().getItem() instanceof SwordItem) {
				poseStack.pushPose();
				HumanoidArm humanoidArm = interactionHand == InteractionHand.MAIN_HAND
						? abstractClientPlayer.getMainArm()
						: abstractClientPlayer.getMainArm().getOpposite();
				applyItemArmTransform(poseStack, humanoidArm, i);
				applyItemBlockTransform2(poseStack, humanoidArm);
				if (animationsCategory.swingAndUseItem.get()) {
					this.applyItemArmAttackTransform(poseStack, humanoidArm, h);
				}
				boolean isRightHand = humanoidArm == HumanoidArm.RIGHT;
				renderItem(abstractClientPlayer, itemStack, isRightHand ? ItemTransforms.TransformType.FIRST_PERSON_RIGHT_HAND : ItemTransforms.TransformType.FIRST_PERSON_LEFT_HAND, !isRightHand, poseStack, multiBufferSource, j);

				poseStack.popPose();
				ci.cancel();
			}
		}
	}
	@Inject(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/UseAnim;"), locals = LocalCapture.CAPTURE_FAILSOFT)
	private void modifyBowCode(AbstractClientPlayer abstractClientPlayer, float f, float g, InteractionHand interactionHand, float h, ItemStack itemStack, float i, PoseStack poseStack, MultiBufferSource multiBufferSource, int j, CallbackInfo ci, boolean bl, HumanoidArm humanoidArm, boolean bl2, int q) {
		this.poseStack = poseStack;
		this.humanoidArm = humanoidArm;
		this.i = i;
		this.itemStack = itemStack;
		this.q = q;
		this.f = f;
	}
	@Redirect(method = "renderArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/UseAnim;"))
	private UseAnim modifyBowCode(ItemStack instance) {
		if(instance.getUseAnimation().equals(UseAnim.BOW)) {
			this.applyItemArmTransform(poseStack, humanoidArm, i);
			poseStack.translate((float) q * -0.2785682F, 0.18344387F, 0.15731531F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-13.935F));
			poseStack.mulPose(Axis.YP.rotationDegrees((float) q * 35.3F));
			poseStack.mulPose(Axis.ZP.rotationDegrees((float) q * -9.785F));
			float r = (float) itemStack.getUseDuration() - ((float) this.minecraft.player.getUseItemRemainingTicks() - f + 1.0F);
			float l = r / 20.0F;
			l = (l * l + l * 2.0F) / 3.0F;
			if (l > 1.0F) {
				l = 1.0F;
			}
			if (l > 0.1F) {
				float m = Mth.sin((r - 0.1F) * 1.3F);
				float n = ((IBowItem)itemStack.getItem()).getFatigueForTime((int)r) - 0.1F;
				float o = m * n;
				poseStack.translate(o * 0.0F, o * 0.004F, o * 0.0F);
			}
			poseStack.translate(l * 0.0F, l * 0.0F, l * 0.04F);
			poseStack.scale(1.0F, 1.0F, 1.0F + l * 0.2F);
			poseStack.mulPose(Axis.YN.rotationDegrees((float) q * 45.0F));
			bow = true;
			return UseAnim.NONE;
		}
		bow = false;
		return instance.getUseAnimation();
	}
	@Inject(method = "applyItemArmTransform", at = @At(value = "HEAD"), cancellable = true)
	public void injectSwordBlocking(PoseStack matrices, HumanoidArm arm, float equipProgress, CallbackInfo ci) {
		if(bow) {
			ci.cancel();
		}
		if(((LivingEntityExtensions)minecraft.player).getBlockingItem().getItem() instanceof SwordItem) {
			int i = arm == HumanoidArm.RIGHT ? 1 : -1;
			matrices.translate(((float)i * 0.56F), (-0.52F + 0.0 * -0.6F), -0.72F);
			ci.cancel();
		}
	}
	@Override
	public void applyItemBlockTransform2(PoseStack poseStack, HumanoidArm humanoidArm) {
		int reverse = humanoidArm == HumanoidArm.RIGHT ? 1 : -1;
		poseStack.translate(reverse * -0.14142136F, 0.08F, 0.14142136F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-102.25F));
		poseStack.mulPose(Axis.YP.rotationDegrees((float)reverse * 13.365F));
		poseStack.mulPose(Axis.ZP.rotationDegrees((float)reverse * 78.05F));
	}
}
