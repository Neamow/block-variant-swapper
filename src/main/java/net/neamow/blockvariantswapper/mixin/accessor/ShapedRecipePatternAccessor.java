package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Optional;

// Exposes the internal ingredient list that shaped crafting actually matches against
// ShapedRecipe.getIngredients() builds a fresh copy, so mutating that would not affect matching
// On 26.2 each slot is an Optional<Ingredient> (empty for blank grid cells), and the backing list may be immutable, so we expose a setter to swap in a fresh widened list
@Mixin(ShapedRecipePattern.class)
public interface ShapedRecipePatternAccessor {
    @Accessor("ingredients")
    List<Optional<Ingredient>> blockvariantswapper$getIngredients();

    @Mutable
    @Accessor("ingredients")
    void blockvariantswapper$setIngredients(List<Optional<Ingredient>> ingredients);
}
