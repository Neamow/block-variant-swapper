package net.neamow.blockvariantswapper.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.core.Holder;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;
import java.util.stream.Stream;

// Genuinely removes variant-producing recipes from the recipe map at the point it is built
//
// RecipeMap.create is the single choke point that builds both immutable indexes (byKey and byType)
// from the recipe registry's listElements() stream. By filtering that stream we make variant recipes
// truly absent from the map, so everything downstream, matching, the recipe book, the stonecutter panel
// and property sets, sees a map that never contained them (no per-site filtering needed).
//
// @ModifyExpressionValue is additive and non-destructive: it wraps the listElements() result and
// composes with other mods' injectors, rather than overwriting the method, so it plays nicely alongside
// other recipe-touching mods. Runs once per RecipeManager construction (i.e. per data reload).
@Mixin(RecipeMap.class)
public abstract class RecipeMapMixin {

    // Wrap the registry element stream feeding create() and drop every variant-producing recipe
    @ModifyExpressionValue(
        method = "create",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/core/HolderLookup;listElements()Ljava/util/stream/Stream;")
    )
    private static Stream<Holder.Reference<Recipe<?>>> blockvariantswapper$stripVariantRecipes(Stream<Holder.Reference<Recipe<?>>> original) {
        // Materialise once up front so we can filter, count, and still have a valid fallback stream
        // (the original stream can only be consumed once). listElements() is finite per reload.
        List<Holder.Reference<Recipe<?>>> all = original.toList();
        // Self-safeguard: on any failure, fall back to the unfiltered list rather than break recipe loading
        try {
            // Ensure variant family data reflects the current config before filtering
            // Safe and idempotent, and decouples us from reload-listener ordering
            BlockVariantManager.initialize();

            List<Holder.Reference<Recipe<?>>> kept = all.stream()
                .filter(ref -> !BlockVariantManager.recipeProducesVariant(ref.value()))
                .toList();

            int removed = all.size() - kept.size();
            if (removed > 0) {
                BlockVariantSwapper.LOGGER.info("Removed " + removed + " recipes that produce block variants (obtained via swapping instead).");
            }
            return kept.stream();
        } catch (Throwable t) {
            BlockVariantSwapper.LOGGER.error("Failed to strip variant-producing recipes; leaving recipes unchanged.", t);
            return all.stream();
        }
    }
}
