package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.ShapedRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the RawShapedRecipe backing a ShapedRecipe so we can reach its real ingredient list
@Mixin(ShapedRecipe.class)
public interface ShapedRecipeAccessor {
    @Accessor("raw")
    RawShapedRecipe blockvariantswapper$getRaw();
}
