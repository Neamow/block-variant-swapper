package net.neamow.blockvariantswapper.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Hides variant-producing recipes from the recipe book, stonecutter panel and slot property sets
//
// finalizeRecipeLoading builds all the DISPLAY/derived structures from recipes.values()
// RecipeMap is final so can't rebuild it here; instead filter the collection returned by recipes.values()
// at the two spots finalizeRecipeLoading reads it, which keeps variants out of everything shown to the player

// Display-only hook; actual crafting matching reads the live RecipeMap, so RecipeMapMixin handles making variants uncraftable
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    // Filtered recipe list for the current reload, so both values() reads see the same set and we log once
    private List<RecipeHolder<?>> blockvariantswapper$filtered;

    // Intercept every recipes.values() call inside finalizeRecipeLoading and hand back the variant-free list
    @ModifyExpressionValue(
        method = "finalizeRecipeLoading",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/crafting/RecipeMap;values()Ljava/util/Collection;")
    )
    private Collection<RecipeHolder<?>> blockvariantswapper$filterVariantRecipes(Collection<RecipeHolder<?>> original) {
        // Self-safeguard: on any failure, fall back to the unfiltered list rather than crash the reload
        try {
            // Build the filtered list once per reload; the second values() read reuses it
            if (this.blockvariantswapper$filtered == null) {
                // Ensure variant family data reflects the current config before filtering
                // Safe and idempotent, and decouples us from reload-listener ordering
                BlockVariantManager.initialize();

                List<RecipeHolder<?>> kept = new ArrayList<>();
                int removed = 0;
                for (RecipeHolder<?> holder : original) {
                    if (BlockVariantManager.recipeProducesVariant(holder)) {
                        removed++;
                    } else {
                        kept.add(holder);
                    }
                }

                this.blockvariantswapper$filtered = kept;
                if (removed > 0) {
                    BlockVariantSwapper.LOGGER.info("Removed " + removed + " recipes that produce block variants (obtained via swapping instead).");
                }
            }
            return this.blockvariantswapper$filtered;
        } catch (Throwable t) {
            BlockVariantSwapper.LOGGER.error("Failed to filter variant-producing recipes; leaving recipes unchanged.", t);
            return original;
        }
    }
}
