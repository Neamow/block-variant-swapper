package net.neamow.blockvariantswapper.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.ArrayList;

// BLOCK VARIANT SWAPPER
// Mixin for the Block class to modify block drops
@Mixin(Block.class)
public class BlockMixin {

    // Intercepts the getDroppedStacks method which determines what items a block drops when broken
    @Inject(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static void onGetDroppedStacks(BlockState state, ServerWorld world, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemStack stack, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> originalDrops = cir.getReturnValue();
        List<ItemStack> newDrops = new ArrayList<>();

        // Iterate through the list of items the block would normally drop
        for (ItemStack drop : originalDrops) {
            // Check if the dropped item is a variant
            Item originalItem = BlockVariantManager.getOriginalItem(drop.getItem());

            if (originalItem != drop.getItem()) {
                // If it is a variant, convert it back to its original base item
                // This creates a loop: e.g. wood planks -> scroll to stairs -> place stairs -> break stairs -> drop planks
                ItemStack newDrop = new ItemStack(originalItem, drop.getCount());
                newDrops.add(newDrop);
            } else {
                // If it's not a variant, drop it as normal
                newDrops.add(drop);
            }
        }

        // Replace the original drops with our modified list
        cir.setReturnValue(newDrops);
    }
}
