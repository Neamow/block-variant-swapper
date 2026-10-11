package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Lets us clear the cached PlacementInfo on shaped/shapeless recipes after we widen their ingredients
// PlacementInfo drives the recipe book ghost/shift-fill and is built once from the original ingredients,
// so without resetting it the recipe book keeps showing the pre-widen recipe (crafting matching is fine, it reads the live ingredients)
@Mixin(NormalCraftingRecipe.class)
public interface NormalCraftingRecipeAccessor {
    @Accessor("placementInfo")
    void blockvariantswapper$setPlacementInfo(PlacementInfo placementInfo);
}
