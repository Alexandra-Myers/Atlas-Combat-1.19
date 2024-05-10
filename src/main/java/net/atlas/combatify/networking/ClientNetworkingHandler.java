package net.atlas.combatify.networking;

import net.atlas.combatify.Combatify;
import net.atlas.combatify.config.ItemConfig;
import net.atlas.combatify.extensions.LivingEntityExtensions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import static net.atlas.combatify.Combatify.*;

@SuppressWarnings("unused")
public class ClientNetworkingHandler {
	private ClientNetworkingHandler() {
	}
	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(NetworkingHandler.RemainingUseSyncPacket.TYPE, (packet, player) -> {
			Entity entity = Minecraft.getInstance().level.getEntity(packet.id());
			if (entity instanceof LivingEntityExtensions livingEntity)
				livingEntity.setUseItemRemaining(packet.ticks());
		});
		ClientPlayConnectionEvents.JOIN.register(modDetectionNetworkChannel,(handler, sender, client) -> {
			if (!ClientPlayNetworking.canSend(NetworkingHandler.ServerboundMissPacket.TYPE)) {
				Combatify.CONFIG.reloadFromDefault();
				ITEMS.reloadFromDefault();
			}
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(modDetectionNetworkChannel, client -> {
			ITEMS = new ItemConfig();
			Combatify.modify();

			Combatify.LOGGER.info("Loaded items config.");
		});
	}
}
