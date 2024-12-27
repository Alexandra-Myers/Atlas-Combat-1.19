package net.atlas.combatify.util.blocking.condition;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public interface BlockingCondition {
	StreamCodec<RegistryFriendlyByteBuf, BlockingCondition> STREAM_CODEC = StreamCodec.of((buf, blockingCondition) -> {
		buf.writeResourceLocation(blockingCondition.id());
		BlockingConditions.STREAM_CODEC_MAP.get(blockingCondition.id()).encode(buf, blockingCondition);
	}, buf -> BlockingConditions.STREAM_CODEC_MAP.get(buf.readResourceLocation()).decode(buf));
	boolean canBlock(ServerLevel serverLevel, LivingEntity instance, ItemStack blockingItem, DamageSource source, float amount);

	boolean canUse(ItemStack itemStack, Level level, Player player, InteractionHand interactionHand);

	boolean canShowInToolTip(ItemStack itemStack, Player player);

	boolean overridesUseDurationAndAnimation(ItemStack itemStack);

	MapCodec<? extends BlockingCondition> type();

	ResourceLocation id();
}
