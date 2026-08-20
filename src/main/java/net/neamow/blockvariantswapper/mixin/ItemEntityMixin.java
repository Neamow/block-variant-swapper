package net.neamow.blockvariantswapper.mixin;

import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// BLOCK VARIANT SWAPPER
// Mixin for ItemEntity (the entity representation of an item dropped on the ground).
// Reverts a block variant to its base block the moment it becomes a dropped item, so
// variants can never exist loose in the world, consistent with block drops.
@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    // Intercepts the setStack method, which sets the item that this entity represents
    @ModifyVariable(method = "setStack", at = @At("HEAD"), argsOnly = true)
    private ItemStack blockvariantswapper$revertVariant(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            Item original = BlockVariantManager.getOriginalItem(item);
            stack = stack.copyComponentsToNewStack(original, stack.getCount());
        }
        return stack;
    }
}
