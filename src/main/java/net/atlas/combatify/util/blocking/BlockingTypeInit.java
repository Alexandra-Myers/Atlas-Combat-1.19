package net.atlas.combatify.util.blocking;

import com.mojang.serialization.MapCodec;
import net.atlas.combatify.Combatify;
import net.atlas.combatify.util.blocking.damage_parsers.DamageParser;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import static net.minecraft.resources.ResourceKey.createRegistryKey;

public class BlockingTypeInit {
	public static final ResourceKey<Registry<MapCodec<? extends DamageParser>>> DAMAGE_PARSER_TYPE = createRegistryKey(Combatify.id("damage_parser"));
	public static final Registry<MapCodec<? extends DamageParser>> DAMAGE_PARSER_TYPE_REG = FabricRegistryBuilder.createSimple(
		DAMAGE_PARSER_TYPE
	).attribute(RegistryAttribute.OPTIONAL).buildAndRegister();
	public static final ResourceKey<Registry<BlockingType>> BLOCKING_TYPE = createRegistryKey(Combatify.id("blocking_type"));
	public static void init() {
		DamageParser.bootstrap(DAMAGE_PARSER_TYPE_REG);
		DynamicRegistries.registerSynced(BLOCKING_TYPE, BlockingType.CODEC);
	}
}
