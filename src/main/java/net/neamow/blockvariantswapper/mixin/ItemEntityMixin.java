package net.neamow.blockvariantswapper.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin for ItemEntity (the entity representation of an item dropped on the ground)
// Reverts a block variant to its base block the moment it becomes a dropped item, so variants can never exist loose in the world
// Because block drops spawn item entities, this single hook also covers broken variant blocks dropping their base
@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    // Intercepts setItem, which sets the item stack this entity represents
    @ModifyVariable(method = "setItem", at = @At("HEAD"), argsOnly = true)
    private ItemStack blockvariantswapper$revertVariant(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            Item original = BlockVariantManager.getOriginalItem(item);
            stack = stack.transmuteCopy(original, stack.getCount());
        }
        return stack;
    }
}
