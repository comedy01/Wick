package dev.wick.client;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.RegistryCodecs;

final class HolderSets {
    private HolderSets() {
    }

    static Codec<HolderSet<EntityType<?>>> entityTypes() {
        return RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE);
    }
}
