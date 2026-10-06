package dev.wick.client;

import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

final class ResourceFiles {
    private ResourceFiles() {
    }

    static List<Map.Entry<String, Callable<Reader>>> json(ResourceManager manager, String directory) {
        List<Map.Entry<String, Callable<Reader>>> files = new ArrayList<>();
        manager.listResources(directory, id -> id.getPath().endsWith(".json"))
                .forEach((id, resource) -> files.add(Map.entry(id.toString(), resource::openAsReader)));
        return files;
    }
}
