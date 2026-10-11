package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes the live recipe map so the widener can replace it after reload (26.2 has no public replaceRecipes;
// the field is reassigned with a fresh RecipeMap, same mechanism the producer-removal mixin uses)
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {
    @Accessor("recipes")
    RecipeMap blockvariantswapper$getRecipes();

    @Accessor("recipes")
    void blockvariantswapper$setRecipes(RecipeMap recipes);
}
