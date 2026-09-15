package net.neamow.blockvariantswapper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

import java.util.*;
import java.util.stream.Collectors;

// Manages block variant swapping; loads variant groups from config files
// and provides helper methods for other parts of the mod to access them
public class BlockVariantManager {
    // Base item -> list of all its variants (including the base itself)
    private static final Map<Item, List<Item>> VARIANTS = new HashMap<>();
    // Reverse map: variant item -> its base item
    private static final Map<Item, Item> ORIGINAL_ITEM_MAP = new HashMap<>();
    private static boolean initialized = false;

    // Get the list of all variants for a given base item
    public static List<Item> getVariants(Item original) {
        if (!initialized) return Collections.emptyList();
        return VARIANTS.getOrDefault(original, Collections.emptyList());
    }

    // Get the base item from one of its variants
    // Returns the item itself if it isn't a variant
    public static Item getOriginalItem(Item variantItem) {
        if (!initialized) return variantItem;
        return ORIGINAL_ITEM_MAP.getOrDefault(variantItem, variantItem);
    }

    // Check if a given item is a variant (not a base item)
    public static boolean isVariant(Item item) {
        if (!initialized) return false;
        return ORIGINAL_ITEM_MAP.containsKey(item);
    }

    // Core method that reads the config and builds the lookup maps
    public static void initialize() {
        if (initialized) {
            // Already initialised; this is a /reload, so clear and rebuild
            VARIANTS.clear();
            ORIGINAL_ITEM_MAP.clear();
        }

        BlockVariantSwapper.LOGGER.info("Initializing Block Variant Manager...");

        // Load and merge all config files
        BlockVariantConfig.load();
        Map<String, List<String>> config = BlockVariantConfig.getMergedVariants();
        Map<String, Integer> namespaceCounts = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : config.entrySet()) {
            String baseIdStr = entry.getKey();
            List<String> variantIdsStr = entry.getValue();

            // Try to parse the base item's ID string
            Identifier baseId = Identifier.tryParse(baseIdStr);
            // Silently skip if the base item doesn't exist in the game's registry
            if (baseId == null || !BuiltInRegistries.ITEM.containsKey(baseId)) {
                continue;
            }

            namespaceCounts.merge(baseId.getNamespace(), 1, Integer::sum);

            Item baseItem = BuiltInRegistries.ITEM.getValue(baseId);
            List<Item> group = new ArrayList<>();

            for (String variantIdStr : variantIdsStr) {
                Identifier variantId = Identifier.tryParse(variantIdStr);
                // Silently skip any variant that doesn't exist in the registry
                if (variantId != null && BuiltInRegistries.ITEM.containsKey(variantId)) {
                    Item variantItem = BuiltInRegistries.ITEM.getValue(variantId);
                    group.add(variantItem);

                    if (variantItem != baseItem) {
                        ORIGINAL_ITEM_MAP.put(variantItem, baseItem);
                    }
                }
            }

            // Ensure the base is always first in the list
            if (!group.contains(baseItem)) {
                group.add(0, baseItem);
            }

            // Only store groups with at least 2 items (base + at least one variant)
            if (group.size() > 1) {
                VARIANTS.put(baseItem, group);
            }
        }

        initialized = true;

        // Log a breakdown by namespace
        String breakdown = namespaceCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));

        if (!breakdown.isEmpty()) {
            BlockVariantSwapper.LOGGER.info("Block Variant Manager initialized. Loaded {} variant groups: {}.", VARIANTS.size(), breakdown);
        } else {
            BlockVariantSwapper.LOGGER.info("Block Variant Manager initialized. Loaded 0 variant groups.");
        }
    }
}
