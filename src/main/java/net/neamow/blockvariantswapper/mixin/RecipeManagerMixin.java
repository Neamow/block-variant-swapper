package net.neamow.blockvariantswapper.mixin;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

// Dynamically removes any loaded recipe whose result is a block variant
// It is result-based, so it works across every namespace/mod with no per-mod tuning
// Runs server-side on data load/reload; clients get the filtered set via recipe sync
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow private RecipeMap recipes;

    // apply() stores the prepared recipe map into 'this.recipes'
    // Overwrite it with a filtered copy, so no variant-producing recipe stays active
    // Injecting at TAIL means the field is already set, and the later finalizeRecipeLoading (stonecutter/property sets) rebuilds from our filtered map
    @Inject(
        method = "apply",
        at = @At("TAIL")
    )
    private void blockvariantswapper$removeVariantRecipes(RecipeMap recipeMap, ResourceManager manager, ProfilerFiller profiler, CallbackInfo ci) {
        // Self-safeguard: this whole block only removes our variant-producing recipes
        // If anything fails, it must degrade to "variants simply aren't removed" rather than crashing the reload
        try {
            // Ensure the variant family data reflects the current config before filtering
            // This is safe and idempotent, and decouples us from reload-listener ordering
            BlockVariantManager.initialize();

            List<RecipeHolder<?>> kept = new ArrayList<>();
            int removed = 0;
            for (RecipeHolder<?> holder : this.recipes.values()) {
                if (blockvariantswapper$producesVariant(holder)) {
                    removed++;
                } else {
                    kept.add(holder);
                }
            }

            if (removed > 0) {
                this.recipes = RecipeMap.create(kept);
                BlockVariantSwapper.LOGGER.info("Removed " + removed + " recipes that produce block variants (obtained via swapping instead).");
            }
        } catch (Throwable t) {
            BlockVariantSwapper.LOGGER.error("Failed to filter variant-producing recipes; leaving recipes unchanged.", t);
        }
    }

    // A recipe produces a variant if its displayed result item is a block variant
    // Extract the item directly from the SlotDisplay type (ItemSlotDisplay or ItemStackSlotDisplay)
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

    private static int blockvariantswapper$diagCount = 0;
}
