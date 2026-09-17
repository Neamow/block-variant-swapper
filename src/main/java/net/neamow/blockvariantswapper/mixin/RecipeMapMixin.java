package net.neamow.blockvariantswapper.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.stream.Stream;

// Makes variant-producing recipes genuinely unmatchable, not just hidden
//
// Every recipe lookup funnels through RecipeManager.getRecipeFor -> RecipeMap.getRecipesFor, which streams byType(type) filtered by Recipe.matches
// RecipeMap is final so can't strip entries from it; instead filter the returned stream so variant recipes never surface as a match
// Complements RecipeManagerMixin, which only hides them from the recipe book / stonecutter panel
@Mixin(RecipeMap.class)
public abstract class RecipeMapMixin {

    // Drop any variant-producing holder from the match stream before the caller reads it
    // Lazy stream filter, so the cost is only paid on holders actually enumerated for a lookup
    // Raw Stream keeps the handler assignable to the generic getRecipesFor return type
    @SuppressWarnings({"rawtypes", "unchecked"})
    @ModifyReturnValue(method = "getRecipesFor", at = @At("RETURN"))
    private Stream blockvariantswapper$excludeVariantRecipes(Stream original) {
        // Self-safeguard: never let a filter error break recipe matching for the whole game
        try {
            return original.filter(holder -> !BlockVariantManager.recipeProducesVariant((RecipeHolder<?>) holder));
        } catch (Throwable t) {
            BlockVariantSwapper.LOGGER.error("Failed to filter variant recipes from matching; leaving matches unchanged.", t);
            return original;
        }
    }
}
