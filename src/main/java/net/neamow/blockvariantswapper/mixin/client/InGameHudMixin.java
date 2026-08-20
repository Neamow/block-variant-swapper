package net.neamow.blockvariantswapper.mixin.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.JumpingMount;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import net.neamow.blockvariantswapper.client.BlockVariantSwapperClientState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// BLOCK VARIANT SWAPPER
// Mixin for InGameHud (the main class responsible for rendering the in-game UI overlay)
// Renders the ghost-slot variant preview above the hotbar and hides HUD clutter while ALT is held
@Mixin(InGameHud.class)
public class InGameHudMixin {

    // Custom texture for the ghost slots used in the variant swapper preview
    @Unique
    private static final Identifier GHOST_SLOT_TEXTURE = Identifier.of(BlockVariantSwapper.MOD_ID, "textures/gui/sprites/hud/ghost_slot.png");

    @Shadow @Final private MinecraftClient client;

    // Helper to check if the variant swapper preview should be shown
    @Unique
    private boolean shouldShowVariants() {
        if (!Screen.hasAltDown() || this.client.player == null || this.client.currentScreen != null) {
            return false;
        }

        ItemStack stack = this.client.player.getMainHandStack();
        if (stack.isEmpty()) {
            return false;
        }

        Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());

        List<Item> variants = BlockVariantManager.getVariants(originalItem);
        return !variants.isEmpty() && variants.size() > 1;
    }

    // Render the variant swapper preview (ghost slots floating above the hotbar)
    @Inject(method = "renderHotbar", at = @At("TAIL"))
    private void renderVariantGhosts(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (!shouldShowVariants()) {
            BlockVariantSwapperClientState.scrollAnimation = 0.0f;
            return;
        }

        PlayerEntity player = this.client.player;
        ItemStack stack = player.getMainHandStack();

        // Determine the base item for the variant group
        Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());

        List<Item> variants = BlockVariantManager.getVariants(originalItem);
        int currentIndex = variants.indexOf(stack.getItem());
        if (currentIndex == -1) currentIndex = 0;

        int center = this.client.getWindow().getScaledWidth() / 2;
        int hotbarY = this.client.getWindow().getScaledHeight() - 22;
        int selectedSlot = player.getInventory().selectedSlot;

        // Calculate X position relative to the selected hotbar slot (vanilla 9-slot hotbar is 182px wide, centred at 91)
        int slotX = center - 91 + 2 + selectedSlot * 20;

        // Animate the scrolling
        float animationSpeed = 0.25f;
        if (BlockVariantSwapperClientState.scrollAnimation > 0) {
            BlockVariantSwapperClientState.scrollAnimation = Math.max(0, BlockVariantSwapperClientState.scrollAnimation - animationSpeed);
        } else if (BlockVariantSwapperClientState.scrollAnimation < 0) {
            BlockVariantSwapperClientState.scrollAnimation = Math.min(0, BlockVariantSwapperClientState.scrollAnimation + animationSpeed);
        }

        float yOffset = BlockVariantSwapperClientState.scrollAnimation * 20.0f;

        // Render setup
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ZERO);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();

        // 1. Draw background textures for the preview slots
        for (int i = 1; i < variants.size(); i++) {
            int baseY = hotbarY - (i * 20);
            context.drawTexture(GHOST_SLOT_TEXTURE, slotX - 1, baseY - 1, 0, 0, 20, 20, 20, 20);
        }

        // 2. Draw items and their names
        for (int i = 1; i < variants.size(); i++) {
            int variantIndex = (currentIndex + i) % variants.size();
            Item variantItem = variants.get(variantIndex);
            ItemStack variantStack = new ItemStack(variantItem);

            int baseY = hotbarY - (i * 20);
            int itemY = (int) (baseY + yOffset);

            context.drawItem(variantStack, slotX + 1, itemY + 1);

            Text name = variantStack.getName();
            int textX = slotX + 22;
            int textY = itemY + 5;
            context.drawTextWithShadow(this.client.textRenderer, name, textX, textY, 0xFFFFFF);
        }

        RenderSystem.disableBlend();
    }

    // --- Hide other HUD elements when showing variants ---
    // These injections cancel the rendering of other HUD elements to reduce clutter when the variant preview is active

    // Status bars (hunger, armour...)
    @Inject(method = "renderStatusBars", at = @At("HEAD"), cancellable = true)
    private void hideStatusBars(DrawContext context, CallbackInfo ci) {
        if (shouldShowVariants()) {
            ci.cancel();
        }
    }

    // Mount health bar
    @Inject(method = "renderMountHealth", at = @At("HEAD"), cancellable = true)
    private void hideMountHealth(DrawContext context, CallbackInfo ci) {
        if (shouldShowVariants()) {
            ci.cancel();
        }
    }

    // XP bar
    @Inject(method = "renderExperienceBar", at = @At("HEAD"), cancellable = true)
    private void hideExperienceBar(DrawContext context, int x, CallbackInfo ci) {
        if (shouldShowVariants()) {
            ci.cancel();
        }
    }

    // Experience level (number)
    @Inject(method = "renderExperienceLevel", at = @At("HEAD"), cancellable = true)
    private void hideExperienceLevel(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (shouldShowVariants()) {
            ci.cancel();
        }
    }

    // Mount jump bar
    @Inject(method = "renderMountJumpBar", at = @At("HEAD"), cancellable = true)
    private void hideMountJumpBar(JumpingMount mount, DrawContext context, int x, CallbackInfo ci) {
        if (shouldShowVariants()) {
            ci.cancel();
        }
    }

    // Held item tooltip
    // This disappears after a few seconds when a tool is selected, but when it's on it can overlap
    @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
    private void hideHeldItemTooltip(DrawContext context, CallbackInfo ci) {
        if (shouldShowVariants()) {
            ci.cancel();
        }
    }
}
