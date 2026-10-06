package dev.wick.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

final class ResourceFiles {
    private ResourceFiles() {
    }

    static List<Map.Entry<String, Callable<Reader>>> json(ResourceManager manager, String directory) {
        List<Map.Entry<String, Callable<Reader>>> files = new ArrayList<>();
        for (ResourceLocation id : manager.listResources(directory, path -> path.endsWith(".json"))) {
            files.add(Map.entry(id.toString(), () -> {
                Resource resource = manager.getResource(id);
                return new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8) {
                    @Override
                    public void close() throws IOException {
                        try {
                            super.close();
                        } finally {
                            resource.close();
                        }
                    }
                };
            }));
        }
        return files;
    }
}
