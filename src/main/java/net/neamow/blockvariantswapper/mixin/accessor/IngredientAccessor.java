package net.neamow.blockvariantswapper.mixin.accessor;

import net.minecraft.core.HolderSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Exposes an Ingredient's backing HolderSet so we can inspect it tag-safely (via HolderSet.unwrap) at RecipeMap.create time,
// where item tags are not yet bound and Ingredient.items() must not be called
@Mixin(Ingredient.class)
public interface IngredientAccessor {
    @Accessor("values")
    HolderSet<Item> blockvariantswapper$getValues();
}
