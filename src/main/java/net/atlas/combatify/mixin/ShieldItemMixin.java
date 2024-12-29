package net.atlas.combatify.mixin;

import net.atlas.combatify.util.MethodHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShieldItem.class)
public class ShieldItemMixin {
	@Inject(method = "use", at = @At(value = "HEAD"), cancellable = true)
	public void removeBlockingIfNotMet(Level level, Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
		ItemStack heldItem = player.getItemInHand(interactionHand);
		if (!MethodHandler.getBlocker(heldItem).canUse(heldItem, level, player, interactionHand)) cir.setReturnValue(InteractionResult.PASS);
	}
}
