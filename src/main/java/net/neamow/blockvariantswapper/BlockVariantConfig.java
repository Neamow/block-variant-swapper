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
// This class manages the configuration for the block variant swapping feature
public class BlockVariantConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_SUFFIX = "_block_variants.json";
    private static final String MINECRAFT_CONFIG_NAME = "minecraft" + CONFIG_SUFFIX;
    private static final String MINECRAFT_RESOURCE_PATH = "/assets/" + BlockVariantSwapper.MOD_ID + "/config/" + MINECRAFT_CONFIG_NAME;

    // A list of all pre-made config files to ship with the mod
    // On first run, these files will be copied from the mod's resources into the user's config folder
    private static final List<String> BUNDLED_CONFIG_RESOURCES = List.of(
        "/assets/" + BlockVariantSwapper.MOD_ID + "/config/biomesoplenty_block_variants.json"
        // To add more, place the file in mod resources and add its path here, e.g.:
        // "/assets/blockvariantswapper/config/create_block_variants.json"
    );

    private static Map<String, List<String>> mergedVariants = new LinkedHashMap<>();

    // Create the config directory and copy all default/bundled configs on game startup
    public static void createDefaultConfigs() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(BlockVariantSwapper.MOD_ID);

        if (!Files.exists(configDir)) {
            try {
                Files.createDirectories(configDir);
            } catch (IOException e) {
                BlockVariantSwapper.LOGGER.error("Failed to create config directory", e);
                return;
            }
        }

        // Create the default config file for vanilla blocks first
        Path defaultConfig = configDir.resolve(MINECRAFT_CONFIG_NAME);
        if (!Files.exists(defaultConfig)) {
            BlockVariantSwapper.LOGGER.info("Creating default block variants config...");
            Map<String, List<String>> defaultMap = loadFromResources(MINECRAFT_RESOURCE_PATH);
            if (!defaultMap.isEmpty()) {
                save(defaultConfig, defaultMap);
            }
        }

        // Then create any other bundled configs, preserving their original filenames
        for (String resourcePath : BUNDLED_CONFIG_RESOURCES) {
            try {
                String filename = new File(resourcePath).getName();
                Path destPath = configDir.resolve(filename);

                if (!Files.exists(destPath)) {
                    BlockVariantSwapper.LOGGER.info("Creating bundled config: " + filename);
                    Map<String, List<String>> modMap = loadFromResources(resourcePath);
                    if (!modMap.isEmpty()) {
                        save(destPath, modMap);
                    }
                }
            } catch (Exception e) {
                BlockVariantSwapper.LOGGER.error("Error processing bundled config resource: " + resourcePath, e);
            }
        }
    }

    // Load data from all existing config files, called by the BlockVariantManager at world load or /reload command
    public static void load() {
        Map<String, List<String>> newMergedMap = new LinkedHashMap<>();
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve(BlockVariantSwapper.MOD_ID);

        if (!Files.exists(configDir)) {
            BlockVariantSwapper.LOGGER.warn("Block variants config directory not found. Cannot load any variants.");
            mergedVariants = newMergedMap;
            return;
        }

        try (Stream<Path> stream = Files.list(configDir)) {
            stream.filter(path -> path.toString().endsWith(CONFIG_SUFFIX))
                  .sorted()
                  .forEach(path -> loadAndMerge(path, newMergedMap));
        } catch (IOException e) {
            BlockVariantSwapper.LOGGER.error("Failed to list config files", e);
        }

        mergedVariants = newMergedMap;
    }

    // Helper method that reads a single JSON file and merges its contents into the main map
    private static void loadAndMerge(Path path, Map<String, List<String>> map) {
        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, List<String>> loaded = GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, List<String>>>(){}.getType());
            if (loaded != null) {
                loaded.forEach((key, value) -> map.merge(key, new ArrayList<>(value), (existing, aNew) -> {
                    existing.addAll(aNew);
                    return existing;
                }));
            }
        } catch (IOException e) {
            BlockVariantSwapper.LOGGER.error("Failed to load config file: " + path, e);
        }
    }

    // Helper method that saves a map of variants to a JSON file
    private static void save(Path file, Map<String, List<String>> map) {
        try (Writer writer = Files.newBufferedWriter(file)) {
            GSON.toJson(map, writer);
        } catch (IOException e) {
            BlockVariantSwapper.LOGGER.error("Failed to save config file", e);
        }
    }

    // Helper method that loads a config file from within the mod's own resources
    private static Map<String, List<String>> loadFromResources(String path) {
        try (InputStream in = BlockVariantConfig.class.getResourceAsStream(path)) {
            if (in == null) {
                BlockVariantSwapper.LOGGER.error("Bundled config resource not found: " + path);
                return new LinkedHashMap<>();
            }
            try (Reader reader = new InputStreamReader(in)) {
                return GSON.fromJson(reader, new TypeToken<LinkedHashMap<String, List<String>>>(){}.getType());
            }
        } catch (IOException e) {
            BlockVariantSwapper.LOGGER.error("Failed to load resource: " + path, e);
            return new LinkedHashMap<>();
        }
    }

    // Getter that allows other parts of the mod to access the loaded variant data
    public static Map<String, List<String>> getMergedVariants() {
        return mergedVariants;
    }
}
