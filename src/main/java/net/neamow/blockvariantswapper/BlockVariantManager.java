package net.neamow.blockvariantswapper;

import com.mojang.datafixers.util.Either;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neamow.blockvariantswapper.mixin.accessor.IngredientAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.ShapedRecipeAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.ShapedRecipePatternAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.ShapelessRecipeAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.SingleItemRecipeAccessor;

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
    // Used to strip variant recipes from the recipe map at build time, so they are genuinely absent and not just hidden
    // Reads the recipe's displayed result item directly, avoiding component resolution that isn't available during recipe load
    public static boolean recipeProducesVariant(Recipe<?> recipe) {
        try {
            for (RecipeDisplay display : recipe.display()) {
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

    // Resolve a recipe's result item from its display, or null if it has none
    // Reads the displayed result (same tag-free path as recipeProducesVariant), avoiding component resolution
    public static Item recipeResultItem(Recipe<?> recipe) {
        try {
            for (RecipeDisplay display : recipe.display()) {
                Item item = extractResultItem(display.result());
                if (item != null) return item;
            }
        } catch (Exception e) {
            // Some special recipes have no simple result; treat as none
        }
        return null;
    }

    // Build a widened copy of a recipe ingredient that also accepts the base block of any variant it matches
    // Returns a new Ingredient, or null if the ingredient referenced no variant
    // This lets recipes that consume a variant child still be crafted from the base block
    //
    // IMPORTANT: must run only after item tags are bound (e.g. END_DATA_PACK_RELOAD)
    // At recipe-load time tags are not yet populated, so an ingredient's tag would resolve to nothing
    public static Ingredient widenToIncludeBase(Ingredient ingredient) {
        if (!initialized || ingredient == null || ingredient.isEmpty()) return null;

        // On 26.3 an Ingredient is a HolderSet of items; read the items it currently accepts
        List<Item> items = new ArrayList<>();
        boolean foundVariant = false;
        for (Holder<Item> holder : ingredient.items().toList()) {
            Item item = holder.value();
            if (!items.contains(item)) items.add(item);
            if (isVariant(item)) {
                foundVariant = true;
                Item base = getOriginalItem(item);
                if (!items.contains(base)) items.add(base);
            }
        }

        // Nothing to do if this ingredient never referenced a variant
        if (!foundVariant) return null;

        // Rebuild from the item set (resolved members plus the bases); this is only the runtime recipe set
        return Ingredient.of(items.stream());
    }

    // Tag-SAFE check: does this recipe consume a variant child, decided WITHOUT resolving any tags?
    // This runs at RecipeMap.create (removal of competitors to our hand-authored recipes), where item tags are NOT yet bound, so we must not call Ingredient.items()/getMatchingStacks on a tag ingredient
    // We only inspect direct (explicit) ingredient sets; a tag-backed ingredient is treated as "unknown" and left alone (its contents cannot be read safely this early)
    // In practice the competitors we remove are concrete recipes consuming a concrete variant, so direct-set detection catches them
    public static boolean recipeConsumesVariantTagSafe(Recipe<?> recipe) {
        try {
            if (recipe instanceof ShapedRecipe shaped) {
                ShapedRecipePattern pattern = ((ShapedRecipeAccessor) shaped).blockvariantswapper$getPattern();
                for (Optional<Ingredient> slot : ((ShapedRecipePatternAccessor) (Object) pattern).blockvariantswapper$getIngredients()) {
                    if (slot.isPresent() && ingredientConsumesVariantTagSafe(slot.get())) return true;
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                for (Ingredient ingredient : ((ShapelessRecipeAccessor) shapeless).blockvariantswapper$getIngredients()) {
                    if (ingredientConsumesVariantTagSafe(ingredient)) return true;
                }
            } else if (recipe instanceof SingleItemRecipe single) {
                if (ingredientConsumesVariantTagSafe(((SingleItemRecipeAccessor) single).blockvariantswapper$getInput())) return true;
            }
        } catch (Exception e) {
            // Unreadable recipe: treat as "no variant" so we never wrongly remove it
            return false;
        }
        return false;
    }

    // Tag-safe variant check for a single ingredient: inspect its HolderSet via unwrap()
    // A direct (Right) set gives us the explicit holder list with no tag resolution; a tag (Left) set is skipped
    private static boolean ingredientConsumesVariantTagSafe(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return false;
        HolderSet<Item> values = ((IngredientAccessor) (Object) ingredient).blockvariantswapper$getValues();
        if (values == null) return false;

        Either<TagKey<Item>, List<Holder<Item>>> unwrapped = values.unwrap();
        // Only the Right (direct item list) side is safe to read before tags bind
        Optional<List<Holder<Item>>> direct = unwrapped.right();
        if (direct.isEmpty()) return false; // tag-backed set: cannot determine safely, leave it alone

        for (Holder<Item> holder : direct.get()) {
            if (isVariant(holder.value())) return true;
        }
        return false;
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
