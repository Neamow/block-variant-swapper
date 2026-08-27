package net.neamow.blockvariantswapper.mixin;

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
//
// Filter at the HEAD of finalizeRecipeLoading, NOT in apply(). apply() just stores the recipe map, but other mods / Fabric recipe hooks
// can re-assign RecipeManager.recipes between apply() and finalizeRecipeLoading(), which caused a non-deterministic race where my filtered
// map got overwritten randomly 50% of the time.
// finalizeRecipeLoading is the single choke point that (re)builds everything the game uses from this.recipes, so filtering here guarantees
// removal takes effect regardless of who touched the field earlier in the reload.
// Can potentially still break on larger modpacks in case other mods mixin into finalizeRecipeLoading or with some exotic crafting. To monitor.
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow private RecipeMap recipes;

    @Inject(method = "finalizeRecipeLoading", at = @At("HEAD"))
    private void blockvariantswapper$removeVariantRecipes(CallbackInfo ci) {
        // Self-safeguard: if anything fails, degrade to "variants simply aren't removed" rather than crash the reload
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
                // Overwrite with the filtered map right before finalizeRecipeLoading reads it to build
                // the stonecutter/property/display structures, so none of them include variant recipes
                this.recipes = RecipeMap.create(kept);
                BlockVariantSwapper.LOGGER.info("Removed " + removed + " recipes that produce block variants (obtained via swapping instead).");
            }
        } catch (Throwable t) {
            BlockVariantSwapper.LOGGER.error("Failed to filter variant-producing recipes; leaving recipes unchanged.", t);
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
