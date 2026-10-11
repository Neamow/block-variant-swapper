package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the shapeless recipe ingredient list, which is the same list used for matching
@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeAccessor {
    @Accessor("ingredients")
    DefaultedList<Ingredient> blockvariantswapper$getIngredients();
}
