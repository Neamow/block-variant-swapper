package net.neamow.blockvariantswapper.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin for ItemEntity (an item dropped on the ground)
// Reverts a block variant to its base block the moment it becomes a dropped item
// so variants can never exist loose in the world, consistent with block drops
@Mixin(ItemEntity.class)
public class ItemEntityMixin {

    // Intercepts setItem, which sets the stack this entity represents
    @ModifyVariable(method = "setItem(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), argsOnly = true)
    private ItemStack blockvariantswapper$revertVariant(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            Item original = BlockVariantManager.getOriginalItem(item);
            stack = stack.transmuteCopy(original, stack.getCount());
        }
        return stack;
    }
}
