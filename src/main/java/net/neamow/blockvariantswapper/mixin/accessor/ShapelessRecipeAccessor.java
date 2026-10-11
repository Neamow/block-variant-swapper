package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

// Exposes the shapeless recipe ingredient list, which is the same list used for matching
// The backing list may be immutable, so we expose a setter to swap in a fresh widened list
@Mixin(ShapelessRecipe.class)
public interface ShapelessRecipeAccessor {
    @Accessor("ingredients")
    List<Ingredient> blockvariantswapper$getIngredients();

    @Mutable
    @Accessor("ingredients")
    void blockvariantswapper$setIngredients(List<Ingredient> ingredients);
}
