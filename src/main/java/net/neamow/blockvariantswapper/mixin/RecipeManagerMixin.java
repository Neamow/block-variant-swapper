package net.neamow.blockvariantswapper.mixin;

import net.minecraft.core.HolderLookup;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.ArrayList;
import java.util.List;

// Dynamically removes any loaded recipe whose result is a block variant
// It is result-based, so it works across every namespace/mod with no per-mod tuning
// Runs server-side on data load/reload; clients get the filtered set via recipe sync
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow @Final private HolderLookup.Provider registries;

    // apply() stores the prepared recipe map into the manager
    // Rewrite the incoming map to a filtered copy before it is stored, so no variant-producing recipe ever becomes active
    @ModifyVariable(
        method = "apply(Lnet/minecraft/world/item/crafting/RecipeMap;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private RecipeMap blockvariantswapper$removeVariantRecipes(RecipeMap recipes) {
        // Self-safeguard: this whole block only removes our variant-producing recipes
        // If anything fails, it must degrade to "variants simply aren't removed" rather than crashing the reload
        try {
            // Ensure the variant family data reflects the current config before filtering
            // This is safe and idempotent, and decouples us from reload-listener ordering
            BlockVariantManager.initialize();

            // Minimal context for resolving a recipe's displayed result stacks
            // Both context keys are optional; supplying the registries is enough for result resolution
            ContextMap context = new ContextMap.Builder()
                .withParameter(SlotDisplayContext.REGISTRIES, this.registries)
                .create(SlotDisplayContext.CONTEXT);

            List<RecipeHolder<?>> kept = new ArrayList<>();
            int removed = 0;
            for (RecipeHolder<?> holder : recipes.values()) {
                if (blockvariantswapper$producesVariant(holder, context)) {
                    removed++;
                } else {
                    kept.add(holder);
                }
            }

            if (removed > 0) {
                BlockVariantSwapper.LOGGER.info("Removed " + removed + " recipes that produce block variants (obtained via swapping instead).");
                return RecipeMap.create(kept);
            }
        } catch (Throwable t) {
            BlockVariantSwapper.LOGGER.error("Failed to filter variant-producing recipes; leaving recipes unchanged.", t);
        }
        return recipes;
    }

    // A recipe produces a variant if any of its displayed result stacks is a block variant
    // The display API is the version-general way to read a recipe's result without a crafting input
    private static boolean blockvariantswapper$producesVariant(RecipeHolder<?> holder, ContextMap context) {
        try {
            for (RecipeDisplay display : holder.value().display()) {
                for (ItemStack result : display.result().resolveForStacks(context)) {
                    if (!result.isEmpty() && BlockVariantManager.isVariant(result.getItem())) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // Some special recipes may not resolve a static result; those are never variants
        }
        return false;
    }
}
