package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

// Stonecutter recipes (SingleItemRecipe) keep a single Ingredient in a final field, so we need both a getter to read it and a mutable setter to swap in the widened copy
// We also expose the cached PlacementInfo so it can be cleared after widening (drives the recipe book ghost/shift-fill)
@Mixin(SingleItemRecipe.class)
public interface SingleItemRecipeAccessor {
    @Accessor("input")
    Ingredient blockvariantswapper$getInput();

    @Mutable
    @Accessor("input")
    void blockvariantswapper$setInput(Ingredient input);

    @Accessor("placementInfo")
    void blockvariantswapper$setPlacementInfo(PlacementInfo placementInfo);
}
