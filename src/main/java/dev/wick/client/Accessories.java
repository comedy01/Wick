package dev.wick.client;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

final class Accessories {
    private static final Logger LOGGER = LogManager.getLogger("wick");

    private static Method trinketsAttachment;
    private static Method trinketsForEach;
    private static Method curiosInventory;
    private static Method curiosFind;

    static {
        try {
            Class<?> api = find("eu.pb4.trinkets.api.TrinketsApi");
            if (api != null) {
                trinketsAttachment = api.getMethod("getAttachment", LivingEntity.class);
                trinketsForEach = find("eu.pb4.trinkets.api.TrinketAttachment").getMethod("forEach", BiConsumer.class);
                LOGGER.info("Trinkets found: worn trinkets give light");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            trinketsAttachment = null;
            LOGGER.warn("Trinkets is installed but its API was not as expected; trinkets will not give light: {}", e.toString());
        }
        try {
            Class<?> api = find("top.theillusivec4.curios.api.CuriosApi");
            if (api != null) {
                curiosInventory = api.getMethod("getCuriosInventory", LivingEntity.class);
                curiosFind = find("top.theillusivec4.curios.api.type.capability.ICuriosItemHandler")
                        .getMethod("findCurios", Predicate.class);
                LOGGER.info("Curios found: worn curios give light");
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            curiosInventory = null;
            LOGGER.warn("Curios is installed but its API was not as expected; curios will not give light: {}", e.toString());
        }
    }

    private Accessories() {
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, Accessories.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    static boolean present() {
        return trinketsAttachment != null || curiosInventory != null;
    }

    static void forEach(LivingEntity entity, Consumer<ItemStack> visitor) {
        if (trinketsAttachment != null) {
            try {
                Object attachment = trinketsAttachment.invoke(null, entity);
                if (attachment != null) {
                    BiConsumer<Object, ItemStack> each = (slot, stack) -> visitor.accept(stack);
                    trinketsForEach.invoke(attachment, each);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                trinketsAttachment = null;
                LOGGER.warn("Reading trinkets failed; trinkets will not give light: {}", e.toString());
            }
        }
        if (curiosInventory != null) {
            try {
                if (curiosInventory.invoke(null, entity) instanceof Optional<?> inventory && inventory.isPresent()) {
                    Predicate<ItemStack> each = stack -> {
                        visitor.accept(stack);
                        return false;
                    };
                    curiosFind.invoke(inventory.get(), each);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                curiosInventory = null;
                LOGGER.warn("Reading curios failed; curios will not give light: {}", e.toString());
            }
        }
    }
}
