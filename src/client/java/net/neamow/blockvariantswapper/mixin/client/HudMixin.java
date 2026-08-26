package net.neamow.blockvariantswapper.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import net.neamow.blockvariantswapper.client.BlockVariantSwapperClientState;
import net.neamow.blockvariantswapper.client.ModKeyBinding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Mixin for the Hud (in-game overlay)
// Renders the ghost-slot variant preview above the hotbar and hides overlapping HUD clutter while the swap key is held
@Mixin(Hud.class)
public class HudMixin {

    // Texture for the ghost preview slots
    @Unique
    private static final Identifier BLOCKVARIANTSWAPPER$GHOST_SLOT_TEXTURE =
        Identifier.fromNamespaceAndPath(BlockVariantSwapper.MOD_ID, "textures/gui/sprites/hud/ghost_slot.png");

    @Shadow @Final private Minecraft minecraft;

    // Should the preview be shown right now? (swap key held, in-world, held item has variants)
    @Unique
    private boolean blockvariantswapper$shouldShowVariants() {
        if (!ModKeyBinding.swapKey.isDown() || this.minecraft.player == null) {
            return false;
        }

        ItemStack stack = this.minecraft.player.getMainHandItem();
        if (stack.isEmpty()) {
            return false;
        }

        Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());
        List<Item> variants = BlockVariantManager.getVariants(originalItem);
        return !variants.isEmpty() && variants.size() > 1;
    }

    // Draw the ghost-slot preview after the item hotbar has been extracted
    @Inject(method = "extractItemHotbar", at = @At("TAIL"))
    private void blockvariantswapper$renderVariantGhosts(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!this.blockvariantswapper$shouldShowVariants()) {
            BlockVariantSwapperClientState.scrollAnimation = 0.0f;
            return;
        }

        Player player = this.minecraft.player;
        ItemStack stack = player.getMainHandItem();

        Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());
        List<Item> variants = BlockVariantManager.getVariants(originalItem);
        int currentIndex = variants.indexOf(stack.getItem());
        if (currentIndex == -1) currentIndex = 0;

        int center = graphics.guiWidth() / 2;
        int hotbarY = graphics.guiHeight() - 22;
        int selectedSlot = player.getInventory().getSelectedSlot();

        // X position of the selected hotbar slot (vanilla 9-slot hotbar is 182px wide, centred at 91)
        int slotX = center - 91 + 2 + selectedSlot * 20;

        // Animate the scroll (icons ease towards their resting position)
        float animationSpeed = 0.25f;
        if (BlockVariantSwapperClientState.scrollAnimation > 0) {
            BlockVariantSwapperClientState.scrollAnimation = Math.max(0, BlockVariantSwapperClientState.scrollAnimation - animationSpeed);
        } else if (BlockVariantSwapperClientState.scrollAnimation < 0) {
            BlockVariantSwapperClientState.scrollAnimation = Math.min(0, BlockVariantSwapperClientState.scrollAnimation + animationSpeed);
        }

        float yOffset = BlockVariantSwapperClientState.scrollAnimation * 20.0f;

        // 1. Draw the ghost-slot backgrounds stacked above the selected slot
        for (int i = 1; i < variants.size(); i++) {
            int baseY = hotbarY - (i * 20);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BLOCKVARIANTSWAPPER$GHOST_SLOT_TEXTURE, slotX - 1, baseY - 1, 0.0f, 0.0f, 20, 20, 20, 20);
        }

        // 2. Draw each upcoming variant's icon and name
        for (int i = 1; i < variants.size(); i++) {
            int variantIndex = (currentIndex + i) % variants.size();
            Item variantItem = variants.get(variantIndex);
            ItemStack variantStack = new ItemStack(variantItem);

            int baseY = hotbarY - (i * 20);
            int itemY = (int) (baseY + yOffset);

            graphics.item(variantStack, slotX + 1, itemY + 1);

            Component name = variantStack.getHoverName();
            graphics.text(this.minecraft.font, name, slotX + 22, itemY + 5, 0xFFFFFFFF, true);
        }
    }

    // Hide overlapping HUD clutter while the preview is active
    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$hidePlayerHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.blockvariantswapper$shouldShowVariants()) ci.cancel();
    }
    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$hideVehicleHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.blockvariantswapper$shouldShowVariants()) ci.cancel();
    }
    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$hideFood(GuiGraphicsExtractor graphics, Player player, int yLineBase, int xRight, CallbackInfo ci) {
        if (this.blockvariantswapper$shouldShowVariants()) ci.cancel();
    }
    @Inject(method = "extractAirBubbles", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$hideAirBubbles(GuiGraphicsExtractor graphics, Player player, int vehicleHearts, int yLineAir, int xRight, CallbackInfo ci) {
        if (this.blockvariantswapper$shouldShowVariants()) ci.cancel();
    }
    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void blockvariantswapper$hideSelectedItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.blockvariantswapper$shouldShowVariants()) ci.cancel();
    }
}
