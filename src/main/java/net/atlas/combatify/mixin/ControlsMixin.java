package net.atlas.combatify.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.atlas.atlascore.util.ArrayListExtensions;
import net.atlas.combatify.CombatifyClient;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(ControlsScreen.class)
public abstract class ControlsMixin {
	@SuppressWarnings("unused")
	@ModifyReturnValue(method = "options", at = @At("RETURN"))
	private static OptionInstance<?>[] injectOptions(OptionInstance<?>[] original, @Local(ordinal = 0, argsOnly = true) Options options) {
		var optionInstance = new ArrayListExtensions<>(Arrays.stream(original).toList());

		optionInstance.addAll(CombatifyClient.autoAttack,
			CombatifyClient.shieldCrouch);

		return optionInstance.toArray(new OptionInstance[0]);
	}
}
