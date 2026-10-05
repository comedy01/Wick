package dev.wick.client;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.state.BlockState;

final class ItemData {
    private ItemData() {
    }

    static BlockState withStackState(ItemStack stack, BlockState state) {
        BlockItemStateProperties properties = stack.get(DataComponents.BLOCK_STATE);
        return properties == null || properties.isEmpty() ? state : properties.apply(state);
    }

    static boolean storedEnchantments(ItemStack stack) {
        return !stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY).isEmpty();
    }
}
