package dev.wick.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.util.Constants;

final class ItemData {
    private ItemData() {
    }

    static BlockState withStackState(ItemStack stack, BlockState state) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("BlockStateTag", Constants.NBT.TAG_COMPOUND)) {
            return state;
        }
        CompoundTag properties = tag.getCompound("BlockStateTag");
        for (String name : properties.getAllKeys()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property != null) {
                state = with(state, property, properties.get(name).getAsString());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    static boolean storedEnchantments(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && !tag.getList("StoredEnchantments", Constants.NBT.TAG_COMPOUND).isEmpty();
    }
}
