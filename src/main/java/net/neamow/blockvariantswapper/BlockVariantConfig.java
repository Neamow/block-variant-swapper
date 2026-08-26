package net.neamow.blockvariantswapper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

// BLOCK VARIANT SWAPPER
// Manages configuration for the block variant swapping feature.
//
// Config model:
//  - Default variant groups are bundled inside the mod jar and loaded fresh on every (re)load,
//    so they always stay current with the installed mod version. They are never written to disk.
//  - The on-disk config folder (config/block-variant-swapper/) is user territory. It is never auto-populated.
//    Any *_block_variants.json files a user places there are layered on top of the defaults,
//    and a user entry for a given base block replaces the default group for that block.
public class BlockVariantConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_SUFFIX = "_block_variants.json";

    // Default variant group definitions bundled in the mod's resources.
    // These are always loaded from the jar; they are never written to the config folder.
    private static final List<String> BUNDLED_DEFAULT_RESOURCES = List.of(
        "/assets/" + BlockVariantSwapper.MOD_ID + "/config/minecraft_block_variants.json"
    );

    private static Map<String, List<String>> mergedVariants = new LinkedHashMap<>();

    // Ensure the (empty) user config directory exists,
    // so players have an obvious place to add their own *_block_variants.json overrides
    public static void createConfigDirectory() {
        Path configDir = getConfigDir();
        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                BlockVariantSwapper.LOGGER.error("Failed to create config directory", e);
            }
        }
    }

    // Build the active variant map: bundled defaults first, then user overrides layered on top
    // Called by the BlockVariantManager at world load and on /reload
    public static void load() {
        Map<String, List<String>> result = new LinkedHashMap<>();

        // 1. Load bundled defaults straight from the jar (always current with the mod version)
        //    Merge with UNION semantics: if two default files contribute to the same base block
        //    (e.g. a mod adds shapes to a vanilla base), their variant lists are combined rather
        //    than one silently replacing the other
        for (String resourcePath : BUNDLED_DEFAULT_RESOURCES) {
            mergeUnion(result, loadFromResources(resourcePath));
        }

        // 2. Layer user overrides on top; a user entry for a base block REPLACES its default group
        Path configDir = getConfigDir();
        if (Files.exists(configDir)) {
            try (Stream<Path> stream = Files.list(configDir)) {
                stream.filter(path -> path.toString().endsWith(CONFIG_SUFFIX))
                      .sorted() // deterministic order; if two user files define the same base block, the last (by filename) wins
                      .forEach(path -> loadAndOverride(path, result));
            } catch (IOException e) {
                BlockVariantSwapper.LOGGER.error("Failed to list config files", e);
            }
        }

        mergedVariants = result;
    }

    // Merge a group map into the target with UNION semantics: variant lists for a shared base block
    // are combined (deduplicated, order-preserving) rather than one file's group replacing another's
    private static void mergeUnion(Map<String, List<String>> target, Map<String, List<String>> add) {
        if (add == null) return;
        add.forEach((key, values) -> {
            List<String> list = target.computeIfAbsent(key, k -> new ArrayList<>());
            for (String v : values) {
                if (!list.contains(v)) list.add(v);
            }
        });
    }

    // Read a single user JSON file and apply its groups on top of the map, replacing any existing group
    private static void loadAndOverride(Path path, Map<String, List<String>> map) {
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, List<String>> loaded = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, List<String>>>(){}.getType());
            if (loaded != null) {
                loaded.forEach((key, value) -> map.put(key, new ArrayList<>(value)));
            }
        } catch (IOException e) {
            BlockVariantSwapper.LOGGER.error("Failed to load config file: " + path, e);
        }
    }

    // Load a config file bundled inside the mod's own resources
    private static Map<String, List<String>> loadFromResources(String path) {
        try (InputStream in = BlockVariantConfig.class.getResourceAsStream(path)) {
            if (in == null) {
                BlockVariantSwapper.LOGGER.error("Bundled config resource not found: " + path);
                return new LinkedHashMap<>();
            }
            try (Reader reader = new InputStreamReader(in)) {
                Map<String, List<String>> loaded = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, List<String>>>(){}.getType());
                return loaded != null ? loaded : new LinkedHashMap<>();
            }
        } catch (IOException e) {
            BlockVariantSwapper.LOGGER.error("Failed to load resource: " + path, e);
            return new LinkedHashMap<>();
        }
    }

    private static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(BlockVariantSwapper.MOD_ID);
    }

    // Getter that allows other parts of the mod to access the loaded variant data
    public static Map<String, List<String>> getMergedVariants() {
        return mergedVariants;
    }
}
