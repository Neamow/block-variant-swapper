package net.neamow.blockvariantswapper.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

// Removes recipes from the recipe map at the point it is built, in two cases:
//  1. PRODUCERS: any recipe whose result is a block variant (obtained via swapping instead)
//  2. COMPETITORS: any foreign recipe that produces a block we craft ourselves via a hand-authored blockvariantswapper:recipe AND consumes a variant child,
//     i.e. a mod's original slab-consuming recipe that would otherwise reappear as a duplicate next to our tuned replacement
//
// RecipeMap.create builds both immutable indexes (byKey and byType) from the recipe registry's listElements() stream
// By filtering that stream we make these recipes truly absent from the map, so everything downstream (matching, the recipe book,
// the stonecutter panel, and property sets) sees a map that never contained them (no per-site filtering needed)
//
// On 26.3 the recipe map is immutable, so this build-time filter is the ONLY place genuine removal can happen
// Item tags are NOT yet bound at this point, so the competitor's "consumes a variant" test is tag-safe
//
// @ModifyExpressionValue is additive and non-destructive: it wraps the listElements() result and composes with other mods' injectors,
// rather than overwriting the method, so it plays nicely alongside other recipe-touching mods
// Runs once per RecipeManager construction (i.e. per data reload)
@Mixin(RecipeMap.class)
public abstract class RecipeMapMixin {

    // Wrap the registry element stream feeding create() and drop variant producers and owned-result competitors
    @ModifyExpressionValue(
        method = "create",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderLookup;listElements()Ljava/util/stream/Stream;")
    )
    private static Stream<Holder.Reference<Recipe<?>>> blockvariantswapper$stripVariantRecipes(Stream<Holder.Reference<Recipe<?>>> original) {
        // Materialise once up front so we can filter, count, and still have a valid fallback stream (the original stream can only be consumed once)
        // listElements() is finite per reload
        List<Holder.Reference<Recipe<?>>> all = original.toList();
        // Self-safeguard: on any failure, fall back to the unfiltered list rather than break recipe loading
        try {
            // Ensure variant family data reflects the current config before filtering
            // Safe and idempotent, and decouples us from reload-listener ordering
            BlockVariantManager.initialize();

            // Result blocks we own via a hand-authored blockvariantswapper:recipe
            Set<Item> ownedResults = new HashSet<>();
            for (Holder.Reference<Recipe<?>> ref : all) {
                if (!ref.key().identifier().getNamespace().equals(BlockVariantSwapper.MOD_ID)) continue;
                Item result = BlockVariantManager.recipeResultItem(ref.value());
                if (result != null) ownedResults.add(result);
            }

            int producersRemoved = 0;
            int competitorsRemoved = 0;
            List<Holder.Reference<Recipe<?>>> kept = new ArrayList<>(all.size());
            for (Holder.Reference<Recipe<?>> ref : all) {
                Recipe<?> recipe = ref.value();

                // 1. Drop recipes that produce a variant shape
                if (BlockVariantManager.recipeProducesVariant(recipe)) {
                    producersRemoved++;
                    continue;
                }

                // 2. Drop a foreign recipe that competes with one of our hand-authored replacements:
                //    it produces one of our owned result blocks and consumes a variant child
                boolean isOurs = ref.key().identifier().getNamespace().equals(BlockVariantSwapper.MOD_ID);
                if (!isOurs && !ownedResults.isEmpty()) {
                    Item result = BlockVariantManager.recipeResultItem(recipe);
                    if (result != null && ownedResults.contains(result)
                            && BlockVariantManager.recipeConsumesVariantTagSafe(recipe)) {
                        competitorsRemoved++;
                        continue;
                    }
                }

                kept.add(ref);
            }

            if (producersRemoved > 0) {
                BlockVariantSwapper.LOGGER.info("Removed " + producersRemoved + " recipes that produce block variants (obtained via swapping instead).");
            }
            if (competitorsRemoved > 0) {
                BlockVariantSwapper.LOGGER.info("Removed " + competitorsRemoved + " recipes that duplicate a hand-authored replacement.");
            }
            return kept.stream();
        } catch (Throwable t) {
            BlockVariantSwapper.LOGGER.error("Failed to strip variant/competitor recipes; leaving recipes unchanged.", t);
            return all.stream();
        }
    }
}
