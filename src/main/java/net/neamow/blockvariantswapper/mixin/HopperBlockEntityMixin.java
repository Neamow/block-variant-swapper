package net.neamow.blockvariantswapper.mixin;

import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// BLOCK VARIANT SWAPPER
// Mixin for HopperBlockEntity
//
// Safety net for automated item movement
// Every hopper and dropper transfer funnels through the private transfer method (the public overload just loops over slots and delegates here)
// Will catch variants transferring through hoppers/droppers (e.g. from a decorated pot)
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {

    @ModifyVariable(
        method = "transfer(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/item/ItemStack;ILnet/minecraft/util/math/Direction;)Lnet/minecraft/item/ItemStack;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static ItemStack blockvariantswapper$revertVariant(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            Item original = BlockVariantManager.getOriginalItem(item);
            return stack.copyComponentsToNewStack(original, stack.getCount());
        }
        return stack;
    }
}
