package net.neamow.blockvariantswapper.mixin;

import com.google.gson.JsonElement;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

// BLOCK VARIANT SWAPPER
// Dynamically removes any loaded recipe whose result is a block variant
// Replacement for a pack.mcmeta filename filter
// It is result-based, so it works across every namespace/mod and needs no per-mod tuning
// Runs server-side on data load/reload; clients receive the filtered set via the normal recipe sync
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {

    @Shadow @Final private RegistryWrapper.WrapperLookup registryLookup;

    @Shadow public abstract Collection<RecipeEntry<?>> values();

    @Shadow public abstract void setRecipes(Iterable<RecipeEntry<?>> recipes);

    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("TAIL"))
    private void blockvariantswapper$removeVariantRecipes(Map<Identifier, JsonElement> map, ResourceManager resourceManager, Profiler profiler, CallbackInfo ci) {
        // Self-safeguard: this whole block only serves to remove our variant-producing recipes
        // If any of it fails, that must degrade to "variants simply aren't removed" rather than crashing the data reload
        try {
            // Ensure the variant family data reflects the current config before filtering. This is safe and
            // idempotent, and decouples us from reload-listener ordering (our loader may run after recipes).
            BlockVariantManager.initialize();

            List<RecipeEntry<?>> kept = new ArrayList<>();
            int removed = 0;
            for (RecipeEntry<?> entry : this.values()) {
                ItemStack result;
                try {
                    result = entry.value().getResult(this.registryLookup);
                } catch (Exception e) {
                    // Some special recipes may not resolve a static result; those are never variants.
                    result = ItemStack.EMPTY;
                }

                if (!result.isEmpty() && BlockVariantManager.isVariant(result.getItem())) {
                    removed++;
                    continue;
                }
                kept.add(entry);
            }

            if (removed > 0) {
                this.setRecipes(kept);
                BlockVariantSwapper.LOGGER.info("Removed " + removed + " recipes that produce block variants (obtained via swapping instead).");
            }
        } catch (Throwable t) {
            // Never let a failure in our recipe filtering crash the reload.
            BlockVariantSwapper.LOGGER.error("Failed to filter variant-producing recipes; leaving recipes unchanged.", t);
        }
    }
}
