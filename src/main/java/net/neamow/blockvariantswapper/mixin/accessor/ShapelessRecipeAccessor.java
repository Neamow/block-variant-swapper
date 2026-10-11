package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the shapeless recipe ingredient list, which is the same list used for matching
@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeAccessor {
    @Accessor("ingredients")
    NonNullList<Ingredient> blockvariantswapper$getIngredients();
}
