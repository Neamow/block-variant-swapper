package net.neamow.blockvariantswapper.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Dynamically removes any loaded recipe whose result is a block variant
// It is result-based, so it works across every namespace/mod with no per-mod tuning
//
// finalizeRecipeLoading is the single choke point that builds everything the game actually uses
// (craftable property sets, the stonecutter set, and the recipe-book displays) from recipes.values().
// The RecipeMap and its backing field are immutable/final here, so instead of rebuilding the map we
// filter the collection returned by recipes.values() at the two spots finalizeRecipeLoading reads it.
// The full map is left intact for lookups; only the derived craftable/displayed structures drop variants,
// which is exactly the intent (you obtain shapes by swapping, not crafting).
// Can potentially still break on larger modpacks if other mods rework finalizeRecipeLoading. To monitor.
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
                    if (blockvariantswapper$producesVariant(holder)) {
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

    // A recipe produces a variant if its displayed result item is a block variant
    // Extract the item directly from the SlotDisplay type (ItemSlotDisplay or ItemStackSlotDisplay),
    // avoiding resolveForStacks which needs bound components not available during recipe load
    private static boolean blockvariantswapper$producesVariant(RecipeHolder<?> holder) {
        try {
            for (RecipeDisplay display : holder.value().display()) {
                Item resultItem = blockvariantswapper$extractResultItem(display.result());
                if (resultItem != null && BlockVariantManager.isVariant(resultItem)) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Some special recipes may not have a simple result; those are never variants
        }
        return false;
    }

    // Extract the result Item directly from common SlotDisplay types without component resolution
    private static Item blockvariantswapper$extractResultItem(SlotDisplay display) {
        if (display instanceof SlotDisplay.ItemSlotDisplay itemDisplay) {
            return itemDisplay.item().value();
        } else if (display instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay) {
            return stackDisplay.stack().item().value();
        }
        return null;
    }
}
