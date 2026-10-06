package dev.wick.forge;

import dev.wick.client.WickClient;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;

@Mod(WickClient.MOD_ID)
public final class WickForge {
    public WickForge() {
        ModLoadingContext context = ModLoadingContext.get();
        context.registerExtensionPoint(
                IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }

        WickClient.init(FMLPaths.CONFIGDIR.get());

        KeyMapping toggle = WickClient.createToggleKey();
        WickClient.setToggleKey(toggle);
        ForgeClient.register(context, FMLJavaModLoadingContext.get().getModEventBus(), toggle);
    }
}
