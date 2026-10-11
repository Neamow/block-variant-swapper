package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the internal ingredient list that shaped crafting actually matches against
// ShapedRecipe.getIngredients() builds a fresh copy, so mutating that would not affect matching
@Mixin(ShapedRecipePattern.class)
public interface ShapedRecipePatternAccessor {
    @Accessor("ingredients")
    NonNullList<Ingredient> blockvariantswapper$getIngredients();
}
