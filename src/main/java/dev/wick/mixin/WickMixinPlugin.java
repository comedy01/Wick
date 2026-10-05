package dev.wick.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public final class WickMixinPlugin implements IMixinConfigPlugin {
    private static final String[] SODIUM_CLASSES = {
            "net/caffeinemc/mods/sodium/client/model/light/smooth/AoFaceData.class",
            "me/jellysquid/mods/sodium/client/model/light/smooth/AoFaceData.class"};

    private boolean sodium;

    @Override
    public void onLoad(String mixinPackage) {
        for (String name : SODIUM_CLASSES) {
            sodium |= WickMixinPlugin.class.getClassLoader().getResource(name) != null;
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !mixinClassName.startsWith("dev.wick.mixin.sodium.") || sodium;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
