package net.atlas.combatify.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.atlas.combatify.extensions.PlayerExtensions;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.extensions.IForgePlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(IForgePlayer.class)
public interface ForgePlayerMixin {
	@Shadow
	Player self();

	@ModifyReturnValue(method = "getEntityReach", at = @At(value = "RETURN"), remap = false)
	default double modReach(double original) {
		return ((PlayerExtensions)self()).getCurrentAttackReach(0.0F);
	}
}
