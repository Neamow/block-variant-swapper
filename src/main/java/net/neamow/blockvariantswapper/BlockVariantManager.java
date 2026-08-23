package net.neamow.blockvariantswapper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.*;
import java.util.stream.Collectors;

// This class manages block variant swapping, loads variant groups from config files
// and provides helper methods for other parts of the mod to access them
public class BlockVariantManager {
    // This map stores the main relationship: base item -> list of all its variants
    private static final Map<Item, List<Item>> VARIANTS = new HashMap<>();
    // This is a reverse map that lets us quickly find the base item from any of its variants
    private static final Map<Item, Item> ORIGINAL_ITEM_MAP = new HashMap<>();
    // Flag to make sure we only initialise once
    private static boolean initialized = false;

    // Get the list of all variants for a given base item
    public static List<Item> getVariants(Item original) {
        if (!initialized) return Collections.emptyList();
        return VARIANTS.getOrDefault(original, Collections.emptyList());
    }

    // Get the base item from one of its variants
    // If the item passed in isn't a variant, it just returns the item itself
    public static Item getOriginalItem(Item variantItem) {
        if (!initialized) return variantItem;
        return ORIGINAL_ITEM_MAP.getOrDefault(variantItem, variantItem);
    }

    // Check if a given item is a variant (not a base item in a group)
    public static boolean isVariant(Item item) {
        if (!initialized) return false;
        // An item is a variant if it exists as a key in our reverse map
        return ORIGINAL_ITEM_MAP.containsKey(item);
    }

    // Core method that reads the config and builds our maps
    public static void initialize() {
        if (initialized) {
            // If already initialised, we assume this is a /reload command
            VARIANTS.clear();
            ORIGINAL_ITEM_MAP.clear();
        }

        BlockVariantSwapper.LOGGER.info("Initializing Block Variant Manager...");

        // First, load and merge all config files from the config directory
        BlockVariantConfig.load();
        Map<String, List<String>> config = BlockVariantConfig.getMergedVariants();
        // This map will store the counts of loaded groups for our summary log message
        Map<String, Integer> namespaceCounts = new HashMap<>();

        // Now, iterate over every group defined in the merged config
        for (Map.Entry<String, List<String>> entry : config.entrySet()) {
            String baseIdStr = entry.getKey();
            List<String> variantIdsStr = entry.getValue();

            // Try to parse the base item's ID string
            ResourceLocation baseId = ResourceLocation.tryParse(baseIdStr);
            // Silently skip this entire group if the base item ID is invalid or the item doesn't exist in the game
            if (baseId == null || !BuiltInRegistries.ITEM.containsKey(baseId)) {
                continue;
            }

            // If we found the base item, increment the counter for its namespace (e.g., "minecraft", "biomesoplenty")
            namespaceCounts.merge(baseId.getNamespace(), 1, Integer::sum);

            Item baseItem = BuiltInRegistries.ITEM.get(baseId);
            List<Item> group = new ArrayList<>();

            // Process all the variants listed for this group
            for (String variantIdStr : variantIdsStr) {
                ResourceLocation variantId = ResourceLocation.tryParse(variantIdStr);
                // Silently skip any variant that has an invalid ID or doesn't exist in the game
                if (variantId != null && BuiltInRegistries.ITEM.containsKey(variantId)) {
                    Item variantItem = BuiltInRegistries.ITEM.get(variantId);
                    group.add(variantItem);

                    // If this variant isn't the same as the base item, add it to our reverse map for quick lookups
                    if (variantItem != baseItem) {
                        ORIGINAL_ITEM_MAP.put(variantItem, baseItem);
                    }
                }
            }

            // Just in case the base item wasn't listed in its own variant list, add it to the start
            if (!group.contains(baseItem)) {
                group.add(0, baseItem);
            }

            // If the group has more than one item, it's a valid variant group, so we store it
            if (group.size() > 1) {
                VARIANTS.put(baseItem, group);
            }
        }

        initialized = true;

        // Build the detailed log message with a breakdown by namespace
        String breakdown = namespaceCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // Sort alphabetically for consistent output
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));

        if (!breakdown.isEmpty()) {
            BlockVariantSwapper.LOGGER.info("Block Variant Manager initialized. Loaded " + VARIANTS.size() + " variant groups: " + breakdown + ".");
        } else {
            BlockVariantSwapper.LOGGER.info("Block Variant Manager initialized. Loaded 0 variant groups.");
        }
    }
}
