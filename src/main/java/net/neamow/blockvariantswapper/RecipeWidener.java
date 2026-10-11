package net.neamow.blockvariantswapper;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CuttingRecipe;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.collection.DefaultedList;
import net.neamow.blockvariantswapper.mixin.accessor.CuttingRecipeAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.RawShapedRecipeAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.ShapedRecipeAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.ShapelessRecipeAccessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// BLOCK VARIANT SWAPPER
// Generalised recipe patcher that runs once the datapack reload has finished (so item tags are bound)
// Two phases:
//  1. REMOVE our competitors to our mod's recipe overrides
//     For every result block we already craft via a hand-authored blockvariantswapper:recipe, remove any OTHER recipe that produces the same block and consumes a variant
//     This retires a mod's original variant child-consuming recipe deterministically (we edit the assembled recipe set in code, so datapack load order no longer matters),
//     stopping it reappearing as a duplicate in-game
//  2. WIDEN everything else
//     Any remaining recipe that consumes a variant is widened so it also accepts that variant's base block, covering vanilla, supported mods, and unknown mods with no per-recipe authoring
public final class RecipeWidener {
    private RecipeWidener() {}

    public static void widenAll(MinecraftServer server) {
        try {
            // Make sure the family maps are current before we read them
            BlockVariantManager.initialize();
            RecipeManager recipeManager = server.getRecipeManager();
            RegistryWrapper.WrapperLookup lookup = server.getRegistryManager();

            // Result blocks that we craft via a hand-authored replacement recipe
            Set<Item> ownedResults = collectOwnedResults(recipeManager, lookup);

            // Phase 1
            removeOwnedCompetitors(recipeManager, lookup, ownedResults);
            // Phase 2
            widenRemaining(recipeManager, lookup, ownedResults);
        } catch (Throwable t) {
            // Degrade to "recipes unchanged" rather than disrupting the reload
            BlockVariantSwapper.LOGGER.error("Failed to patch variant-consuming recipes; leaving recipes unchanged.", t);
        }
    }

    // Gather the result item of every recipe in our own namespace
    // These are the blocks the mod deliberately takes ownership of with a hand-authored replacement, so any competing recipe for them needs ot be removed
    private static Set<Item> collectOwnedResults(RecipeManager recipeManager, RegistryWrapper.WrapperLookup lookup) {
        Set<Item> owned = new HashSet<>();
        for (RecipeEntry<?> entry : recipeManager.values()) {
            if (!entry.id().getNamespace().equals(BlockVariantSwapper.MOD_ID)) continue;
            Item result = resultItem(entry.value(), lookup);
            if (result != null) {
                owned.add(result);
            }
        }
        return owned;
    }

    // Phase 1: remove any recipe that is NOT ours, produces one of our owned result blocks, and consumes a variant
    private static void removeOwnedCompetitors(RecipeManager recipeManager, RegistryWrapper.WrapperLookup lookup, Set<Item> ownedResults) {
        if (ownedResults.isEmpty()) return;

        List<RecipeEntry<?>> kept = new ArrayList<>();
        int removed = 0;
        for (RecipeEntry<?> entry : recipeManager.values()) {
            boolean isOurs = entry.id().getNamespace().equals(BlockVariantSwapper.MOD_ID);
            Item result = resultItem(entry.value(), lookup);

            boolean competes = !isOurs
                    && result != null
                    && ownedResults.contains(result)
                    && consumesVariant(entry.value());

            if (competes) {
                removed++;
                continue;
            }
            kept.add(entry);
        }

        if (removed > 0) {
            recipeManager.setRecipes(kept);
            BlockVariantSwapper.LOGGER.info("Removed " + removed + " recipes that duplicate a hand-authored replacement.");
        }
    }

    // Phase 2: widen every remaining variant-consuming recipe
    // Skip our owned results so we never widen our own replacements or re-add a competitor for a block we have already taken ownership of
    private static void widenRemaining(RecipeManager recipeManager, RegistryWrapper.WrapperLookup lookup, Set<Item> ownedResults) {
        int widened = 0;
        int scanned = 0;
        for (RecipeEntry<?> entry : recipeManager.values()) {
            scanned++;
            Item result = resultItem(entry.value(), lookup);
            if (result != null && ownedResults.contains(result)) continue;

            try {
                if (widenRecipe(entry.value())) {
                    widened++;
                }
            } catch (Exception e) {
                // Don't interrupt overall widening for failed edge cases
                BlockVariantSwapper.LOGGER.warn("Could not widen ingredients for recipe " + entry.id() + "; leaving it unchanged.", e);
            }
        }

        BlockVariantSwapper.LOGGER.info("Widened " + widened + " of " + scanned + " recipes to also accept the base block of a consumed variant.");
    }

    // Resolve a recipe's static result item, or null if it has none (some special recipes don't)
    private static Item resultItem(Recipe<?> recipe, RegistryWrapper.WrapperLookup lookup) {
        try {
            ItemStack result = recipe.getResult(lookup);
            return result.isEmpty() ? null : result.getItem();
        } catch (Exception e) {
            return null;
        }
    }

    // True if any of the recipe's ingredients currently matches a variant item
    // Uses the generic Recipe#getIngredients() so it works for any recipe type
    // Exotic types with no listed ingredients simply report false (and so are never removed)
    private static boolean consumesVariant(Recipe<?> recipe) {
        try {
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient == null || ingredient.isEmpty()) continue;
                for (ItemStack stack : ingredient.getMatchingStacks()) {
                    if (BlockVariantManager.isVariant(stack.getItem())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Treat an unreadable recipe as "no variant"; it is safer to keep it than to remove it
            return false;
        }
        return false;
    }

    // Widens the ingredients of a single recipe in place and returns true if anything changed
    // Handles vanilla recipe shapes that use Ingredients; other/modded types are skipped safely
    private static boolean widenRecipe(Recipe<?> recipe) {
        // Shaped crafting: the real matching list lives on the backing RawShapedRecipe, not the copy returned by ShapedRecipe.getIngredients()
        if (recipe instanceof ShapedRecipe shaped) {
            RawShapedRecipe raw = ((ShapedRecipeAccessor) shaped).blockvariantswapper$getRaw();
            return widenList(((RawShapedRecipeAccessor) (Object) raw).blockvariantswapper$getIngredients());
        }

        // Shapeless crafting: getIngredients() exposes the same list used for matching
        if (recipe instanceof ShapelessRecipe shapeless) {
            return widenList(((ShapelessRecipeAccessor) shapeless).blockvariantswapper$getIngredients());
        }

        // Stonecutter (and other cutting recipes): a single Ingredient field
        if (recipe instanceof CuttingRecipe cutting) {
            CuttingRecipeAccessor accessor = (CuttingRecipeAccessor) cutting;
            Ingredient widened = BlockVariantManager.widenToIncludeBase(accessor.blockvariantswapper$getIngredient());
            if (widened != null) {
                accessor.blockvariantswapper$setIngredient(widened);
                return true;
            }
            return false;
        }

        // Any other recipe type (smelting, modded types, special recipes) is left untouched
        return false;
    }

    // Replaces each variant-matching ingredient in a recipe's ingredient list with a widened copy
    // Returns true if any changed
    private static boolean widenList(DefaultedList<Ingredient> ingredients) {
        boolean changed = false;
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient widened = BlockVariantManager.widenToIncludeBase(ingredients.get(i));
            if (widened != null) {
                ingredients.set(i, widened);
                changed = true;
            }
        }
        return changed;
    }
}
