package net.neamow.blockvariantswapper;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.neamow.blockvariantswapper.mixin.accessor.NormalCraftingRecipeAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.RecipeManagerAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.ShapedRecipeAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.ShapedRecipePatternAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.ShapelessRecipeAccessor;
import net.neamow.blockvariantswapper.mixin.accessor.SingleItemRecipeAccessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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

            // Result blocks that we craft via a hand-authored replacement recipe
            Set<Item> ownedResults = collectOwnedResults(recipeManager);

            // Phase 1
            boolean changed = removeOwnedCompetitors(recipeManager, ownedResults);
            // Phase 2
            changed |= widenRemaining(recipeManager, ownedResults);

            // Rebuild the derived display structures (recipe-book ghosts, item property sets, stonecutter set)
            // finalizeRecipeLoading snapshots recipe.display() during the reload, BEFORE our widening runs,
            // so the recipe book keeps showing the un-widened ghost until we regenerate those snapshots from the live recipes
            if (changed) {
                recipeManager.finalizeRecipeLoading(server.getWorldData().enabledFeatures());
            }
        } catch (Throwable t) {
            // Degrade to "recipes unchanged" rather than disrupting the reload
            BlockVariantSwapper.LOGGER.error("Failed to patch variant-consuming recipes; leaving recipes unchanged.", t);
        }
    }

    // Gather the result item of every recipe in our own namespace
    // These are the blocks the mod deliberately takes ownership of with a hand-authored replacement, so any competing recipe for them needs ot be removed
    private static Set<Item> collectOwnedResults(RecipeManager recipeManager) {
        Set<Item> owned = new HashSet<>();
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            if (!holder.id().identifier().getNamespace().equals(BlockVariantSwapper.MOD_ID)) continue;
            Item result = BlockVariantManager.recipeResultItem(holder.value());
            if (result != null) {
                owned.add(result);
            }
        }
        return owned;
    }

    // Phase 1: remove any recipe that is NOT ours, produces one of our owned result blocks, and consumes a variant
    // 26.2 has no public replaceRecipes, so we rebuild a fresh RecipeMap and reassign the field via the accessor
    // Returns true if anything was removed
    private static boolean removeOwnedCompetitors(RecipeManager recipeManager, Set<Item> ownedResults) {
        if (ownedResults.isEmpty()) return false;

        List<RecipeHolder<?>> kept = new ArrayList<>();
        int removed = 0;
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            boolean isOurs = holder.id().identifier().getNamespace().equals(BlockVariantSwapper.MOD_ID);
            Item result = BlockVariantManager.recipeResultItem(holder.value());

            boolean competes = !isOurs
                    && result != null
                    && ownedResults.contains(result)
                    && consumesVariant(holder.value());

            if (competes) {
                removed++;
                continue;
            }
            kept.add(holder);
        }

        if (removed > 0) {
            ((RecipeManagerAccessor) recipeManager).blockvariantswapper$setRecipes(RecipeMap.create(kept));
            BlockVariantSwapper.LOGGER.info("Removed " + removed + " recipes that duplicate a hand-authored replacement.");
            return true;
        }
        return false;
    }

    // Phase 2: widen every remaining variant-consuming recipe
    // Skip our owned results so we never widen our own replacements or re-add a competitor for a block we have already taken ownership of
    // Returns true if any recipe was widened
    private static boolean widenRemaining(RecipeManager recipeManager, Set<Item> ownedResults) {
        int widened = 0;
        int scanned = 0;
        for (RecipeHolder<?> holder : recipeManager.getRecipes()) {
            scanned++;
            Item result = BlockVariantManager.recipeResultItem(holder.value());
            if (result != null && ownedResults.contains(result)) continue;

            try {
                if (widenRecipe(holder.value())) {
                    widened++;
                }
            } catch (Exception e) {
                // Don't interrupt overall widening for failed edge cases
                BlockVariantSwapper.LOGGER.warn("Could not widen ingredients for recipe " + holder.id().identifier() + "; leaving it unchanged.", e);
            }
        }

        BlockVariantSwapper.LOGGER.info("Widened " + widened + " of " + scanned + " recipes to also accept the base block of a consumed variant.");
        return widened > 0;
    }

    // True if any of the recipe's ingredients currently matches a variant item
    // 26.2 Recipe has no generic getIngredients(), so we read the ingredients per handled type
    // Exotic/other types report false (and so are never removed)
    private static boolean consumesVariant(Recipe<?> recipe) {
        try {
            if (recipe instanceof ShapedRecipe shaped) {
                ShapedRecipePattern pattern = ((ShapedRecipeAccessor) shaped).blockvariantswapper$getPattern();
                for (Optional<Ingredient> slot : ((ShapedRecipePatternAccessor) (Object) pattern).blockvariantswapper$getIngredients()) {
                    if (slot.isPresent() && ingredientHasVariant(slot.get())) return true;
                }
            } else if (recipe instanceof ShapelessRecipe shapeless) {
                for (Ingredient ingredient : ((ShapelessRecipeAccessor) shapeless).blockvariantswapper$getIngredients()) {
                    if (ingredientHasVariant(ingredient)) return true;
                }
            } else if (recipe instanceof SingleItemRecipe single) {
                if (ingredientHasVariant(((SingleItemRecipeAccessor) single).blockvariantswapper$getInput())) return true;
            }
        } catch (Exception e) {
            // Treat an unreadable recipe as "no variant"; it is safer to keep it than to remove it
            return false;
        }
        return false;
    }

    // True if an ingredient currently accepts any variant item
    private static boolean ingredientHasVariant(Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return false;
        return ingredient.items().anyMatch(holder -> BlockVariantManager.isVariant(holder.value()));
    }

    // Widens the ingredients of a single recipe in place and returns true if anything changed
    // Handles vanilla recipe shapes that use Ingredients; other/modded types are skipped safely
    private static boolean widenRecipe(Recipe<?> recipe) {
        // Shaped crafting: the real matching list lives on the backing ShapedRecipePattern, not the copy returned by ShapedRecipe.getIngredients()
        // Each slot is an Optional<Ingredient> (empty for blank grid cells), so we unwrap, widen, and rewrap
        if (recipe instanceof ShapedRecipe shaped) {
            ShapedRecipePattern pattern = ((ShapedRecipeAccessor) shaped).blockvariantswapper$getPattern();
            ShapedRecipePatternAccessor accessor = (ShapedRecipePatternAccessor) (Object) pattern;
            List<Optional<Ingredient>> current = accessor.blockvariantswapper$getIngredients();

            List<Optional<Ingredient>> rebuilt = new ArrayList<>(current.size());
            boolean changed = false;
            for (Optional<Ingredient> slot : current) {
                if (slot.isPresent()) {
                    Ingredient widened = BlockVariantManager.widenToIncludeBase(slot.get());
                    if (widened != null) {
                        rebuilt.add(Optional.of(widened));
                        changed = true;
                        continue;
                    }
                }
                rebuilt.add(slot);
            }

            if (changed) {
                accessor.blockvariantswapper$setIngredients(rebuilt);
                // Clear the cached PlacementInfo so the recipe book rebuilds its ghost/shift-fill from the widened ingredients
                ((NormalCraftingRecipeAccessor) shaped).blockvariantswapper$setPlacementInfo(null);
            }
            return changed;
        }

        // Shapeless crafting: a plain list of ingredients used directly for matching
        if (recipe instanceof ShapelessRecipe shapeless) {
            ShapelessRecipeAccessor accessor = (ShapelessRecipeAccessor) shapeless;
            List<Ingredient> current = accessor.blockvariantswapper$getIngredients();

            List<Ingredient> rebuilt = new ArrayList<>(current.size());
            boolean changed = false;
            for (Ingredient ingredient : current) {
                Ingredient widened = BlockVariantManager.widenToIncludeBase(ingredient);
                if (widened != null) {
                    rebuilt.add(widened);
                    changed = true;
                } else {
                    rebuilt.add(ingredient);
                }
            }

            if (changed) {
                accessor.blockvariantswapper$setIngredients(rebuilt);
                // Clear the cached PlacementInfo so the recipe book rebuilds its ghost/shift-fill from the widened ingredients
                ((NormalCraftingRecipeAccessor) shapeless).blockvariantswapper$setPlacementInfo(null);
            }
            return changed;
        }

        // Stonecutter (and other single-item recipes): a single Ingredient field
        if (recipe instanceof SingleItemRecipe single) {
            SingleItemRecipeAccessor accessor = (SingleItemRecipeAccessor) single;
            Ingredient widened = BlockVariantManager.widenToIncludeBase(accessor.blockvariantswapper$getInput());
            if (widened != null) {
                accessor.blockvariantswapper$setInput(widened);
                // Clear the cached PlacementInfo so the recipe book rebuilds from the widened ingredient
                accessor.blockvariantswapper$setPlacementInfo(null);
                return true;
            }
            return false;
        }

        // Any other recipe type (smelting, modded types, special recipes) is left untouched
        return false;
    }
}
