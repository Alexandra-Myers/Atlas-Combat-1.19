package net.atlas.combatify.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.atlas.atlascore.AtlasCore;
import net.atlas.atlascore.config.AtlasConfig;
import net.atlas.combatify.Combatify;
import net.atlas.combatify.networking.NetworkingHandler;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AtlasCore.class)
public class AtlasCoreMixin {
	@WrapOperation(method = "lambda$onInitialize$0", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking;send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"))
	private static void removeForUnmodded(ServerPlayer player, CustomPacketPayload payload, Operation<Void> original, @Local(ordinal = 0) AtlasConfig atlasConfig) {
		if (!ServerPlayNetworking.canSend(player, NetworkingHandler.RemainingUseSyncPacket.TYPE) || !atlasConfig.name.equals(Combatify.CONFIG.name)) return;
		original.call(player, payload);
	}
}
