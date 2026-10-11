package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

// Stonecutter recipes (SingleItemRecipe) keep a single Ingredient in a final field, so we need both a getter to read it and a mutable setter to swap in the widened copy
@Mixin(SingleItemRecipe.class)
public interface SingleItemRecipeAccessor {
    @Accessor("ingredient")
    Ingredient blockvariantswapper$getIngredient();

    @Mutable
    @Accessor("ingredient")
    void blockvariantswapper$setIngredient(Ingredient ingredient);
}
