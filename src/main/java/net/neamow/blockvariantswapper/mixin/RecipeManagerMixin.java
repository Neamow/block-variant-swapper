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
// Result-based, so it works across every namespace/mod with no per-mod tuning
//
// Filters at HEAD of finalizeRecipeLoading, the single choke point that builds everything the game uses from the recipe map
// This guarantees removal takes effect regardless of other mods touching the recipes field earlier in the reload cycle
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow private RecipeMap recipes;

    @Inject(method = "finalizeRecipeLoading", at = @At("HEAD"))
    private void blockvariantswapper$removeVariantRecipes(CallbackInfo ci) {
        // Self-safeguard: if anything fails, degrade gracefully rather than crash the reload
        try {
            // Ensure variant data reflects current config before filtering
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
                // Overwrite with filtered map before finalizeRecipeLoading reads it
                this.recipes = RecipeMap.create(kept);
                BlockVariantSwapper.LOGGER.info("Removed {} recipes that produce block variants (obtained via swapping instead).", removed);
            }
        } catch (Throwable t) {
            BlockVariantSwapper.LOGGER.error("Failed to filter variant-producing recipes; leaving recipes unchanged.", t);
        }
    }

    // A recipe produces a variant if its displayed result item is a block variant
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
