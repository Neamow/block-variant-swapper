package net.neamow.blockvariantswapper.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// Mixin for HopperBlockEntity
// Safety net for automated item movement: every hopper and dropper transfer funnels through the
// private addItem helper (the public overloads just loop over slots and delegate here)
// This catches variants moving through hoppers/droppers (e.g. pulled out of a decorated pot) and reverts them
@Mixin(HopperBlockEntity.class)
public class HopperBlockEntityMixin {

    @ModifyVariable(
        method = "addItem(Lnet/minecraft/world/Container;Lnet/minecraft/world/Container;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/item/ItemStack;",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static ItemStack blockvariantswapper$revertVariant(ItemStack stack) {
        Item item = stack.getItem();
        if (BlockVariantManager.isVariant(item)) {
            Item original = BlockVariantManager.getOriginalItem(item);
            return stack.transmuteCopy(original, stack.getCount());
        }
        return stack;
    }
}
