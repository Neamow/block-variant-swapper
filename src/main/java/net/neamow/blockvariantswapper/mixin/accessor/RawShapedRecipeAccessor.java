package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the internal ingredient list that shaped crafting actually matches against
// ShapedRecipe.getIngredients() builds a fresh copy, so mutating that would not affect matching
@Mixin(RawShapedRecipe.class)
public interface RawShapedRecipeAccessor {
    @Accessor("ingredients")
    DefaultedList<Ingredient> blockvariantswapper$getIngredients();
}
