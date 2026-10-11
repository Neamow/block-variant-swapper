package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the ShapedRecipePattern backing a ShapedRecipe so we can reach its real ingredient list
@Mixin(ShapedRecipe.class)
public interface ShapedRecipeAccessor {
    @Accessor("pattern")
    ShapedRecipePattern blockvariantswapper$getPattern();
}
