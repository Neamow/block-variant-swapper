package net.neamow.blockvariantswapper.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neamow.blockvariantswapper.BlockVariantManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

// Mixin for the Block class to modify block drops
@Mixin(Block.class)
public class BlockMixin {

    // Intercepts getDrops (the full overload with entity/tool context) which decides what a block drops when broken
    // The simpler overload delegates here, so this covers every break path
    @Inject(
        method = "getDrops(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemInstance;)Ljava/util/List;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void onGetDrops(BlockState state, ServerLevel level, BlockPos pos, BlockEntity blockEntity, Entity entity, ItemInstance tool, CallbackInfoReturnable<List<ItemStack>> cir) {
        List<ItemStack> originalDrops = cir.getReturnValue();
        List<ItemStack> newDrops = new ArrayList<>();

        // Go through each item the block would normally drop
        for (ItemStack drop : originalDrops) {
            Item originalItem = BlockVariantManager.getOriginalItem(drop.getItem());

            if (originalItem != drop.getItem()) {
                // If it's a variant, drop its base block instead
                // Closes the loop: planks -> scroll to stairs -> place -> break -> drop planks
                newDrops.add(new ItemStack(originalItem, drop.getCount()));
            } else {
                newDrops.add(drop);
            }
        }

        cir.setReturnValue(newDrops);
    }
}
