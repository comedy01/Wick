package dev.wick.client;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.world.entity.EntityType;

final class HolderSets {
    private HolderSets() {
    }

    static Codec<HolderSet<EntityType<?>>> entityTypes() {
        return RegistryCodecs.homogeneousList(Registry.ENTITY_TYPE_REGISTRY);
    }
}
