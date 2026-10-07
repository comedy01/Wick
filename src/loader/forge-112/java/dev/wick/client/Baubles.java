package dev.wick.client;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.items.IItemHandler;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Baubles {
    private static Method handler;
    private static boolean checked;

    private Baubles() {
    }

    static boolean present() {
        if (!checked) {
            checked = true;
            if (Loader.isModLoaded("baubles")) {
                try {
                    handler = Class.forName("baubles.api.BaublesApi").getMethod("getBaublesHandler", EntityPlayer.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    LogManager.getLogger("wick").warn("Baubles is loaded but its API was not found: {}", e.toString());
                }
            }
        }
        return handler != null;
    }

    static List<ItemStack> worn(EntityPlayer player) {
        try {
            Object found = handler.invoke(null, player);
            if (!(found instanceof IItemHandler)) {
                return Collections.emptyList();
            }
            IItemHandler slots = (IItemHandler) found;
            List<ItemStack> stacks = new ArrayList<>(slots.getSlots());
            for (int i = 0; i < slots.getSlots(); i++) {
                stacks.add(slots.getStackInSlot(i));
            }
            return stacks;
        } catch (ReflectiveOperationException | RuntimeException e) {
            handler = null;
            return Collections.emptyList();
        }
    }
}
