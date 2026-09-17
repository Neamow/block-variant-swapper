package net.neamow.blockvariantswapper;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

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

    // Single source of truth for "does this recipe produce a variant shape?"
    // Used both to hide variant recipes from the recipe book and to make them unmatchable
    // Reads the recipe's displayed result item directly, avoiding component resolution that isn't available during recipe load
    public static boolean recipeProducesVariant(RecipeHolder<?> holder) {
        try {
            for (RecipeDisplay display : holder.value().display()) {
                Item resultItem = extractResultItem(display.result());
                if (resultItem != null && isVariant(resultItem)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Some special recipes have no simple result; those are never variants
        }
        return false;
    }

    // Pull the result Item out of the common SlotDisplay types without resolving components
    private static Item extractResultItem(SlotDisplay display) {
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            return itemDisplay.item().value();
        } else if (display instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            return stackDisplay.stack().item().value();
        }
        return null;
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
            Identifier baseId = Identifier.tryParse(baseIdStr);
            // Silently skip this entire group if the base item ID is invalid or the item doesn't exist in the game
            if (baseId == null || !BuiltInRegistries.ITEM.containsKey(baseId)) {
                continue;
            }

            // If we found the base item, increment the counter for its namespace (e.g. "minecraft", "biomesoplenty")
            namespaceCounts.merge(baseId.getNamespace(), 1, Integer::sum);

            Item baseItem = BuiltInRegistries.ITEM.getValue(baseId);
            List<Item> group = new ArrayList<>();

            // Process all the variants listed for this group
            for (String variantIdStr : variantIdsStr) {
                Identifier variantId = Identifier.tryParse(variantIdStr);
                // Silently skip any variant that has an invalid ID or doesn't exist in the game
                if (variantId != null && BuiltInRegistries.ITEM.containsKey(variantId)) {
                    Item variantItem = BuiltInRegistries.ITEM.getValue(variantId);
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
