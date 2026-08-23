package net.neamow.blockvariantswapper.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neamow.blockvariantswapper.BlockVariantManager;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import net.neamow.blockvariantswapper.network.CycleVariantPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Set;

// Client-side runtime behaviour, on the game event bus (client only):
//  - Scroll-to-cycle: while the swap key is held and no screen is open, a scroll sends a CycleVariantPayload to the server and cancels the vanilla hotbar scroll
//  - Ghost preview: draws the variant preview above the hotbar and hides HUD clutter while held
@EventBusSubscriber(modid = BlockVariantSwapper.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEvents {

    private static final ResourceLocation GHOST_SLOT_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(BlockVariantSwapper.MOD_ID, "textures/gui/sprites/hud/ghost_slot.png");

    // Vanilla HUD layers to suppress while the variant preview is showing (reduces clutter/overlap)
    private static final Set<ResourceLocation> HIDDEN_LAYERS = Set.of(
        VanillaGuiLayers.PLAYER_HEALTH,
        VanillaGuiLayers.ARMOR_LEVEL,
        VanillaGuiLayers.FOOD_LEVEL,
        VanillaGuiLayers.AIR_LEVEL,
        VanillaGuiLayers.VEHICLE_HEALTH,
        VanillaGuiLayers.JUMP_METER,
        VanillaGuiLayers.EXPERIENCE_BAR,
        VanillaGuiLayers.EXPERIENCE_LEVEL,
        VanillaGuiLayers.SELECTED_ITEM_NAME
    );

    // --- Scroll to cycle -------------------------------------------------------------------------

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft client = Minecraft.getInstance();

        if (!ModKeyBinding.SWAP_KEY.isDown() || client.player == null || client.screen != null) {
            return;
        }

        ItemStack stack = client.player.getMainHandItem();
        if (stack.isEmpty()) return;

        BlockVariantSwapper.LOGGER.info("[debug] client sees main hand = {}",
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));

        Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());
        List<Item> variants = BlockVariantManager.getVariants(originalItem);
        if (variants.size() <= 1) return;

        // Only act if the server can actually handle the packet (e.g. it has the mod)
        // Otherwise let the vanilla hotbar scroll happen normally
        if (client.getConnection() == null || !client.getConnection().hasChannel(CycleVariantPayload.TYPE)) {
            return;
        }

        int direction = (int) Math.signum(event.getScrollDeltaY());
        if (direction == 0) return;

        PacketDistributor.sendToServer(new CycleVariantPayload(direction));
        BlockVariantSwapperClientState.onScroll(direction);

        // Cancel the vanilla hotbar scroll
        event.setCanceled(true);
    }

    // --- HUD preview + clutter hiding ------------------------------------------------------------

    private static boolean shouldShowVariants() {
        Minecraft client = Minecraft.getInstance();
        if (!ModKeyBinding.SWAP_KEY.isDown() || client.player == null || client.screen != null) {
            return false;
        }
        // Don't fight with the F3 debug overlay
        DebugScreenOverlay debug = client.getDebugOverlay();
        if (debug != null && debug.showDebugScreen()) {
            return false;
        }

        ItemStack stack = client.player.getMainHandItem();
        if (stack.isEmpty()) return false;

        Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());
        List<Item> variants = BlockVariantManager.getVariants(originalItem);
        return variants.size() > 1;
    }

    // Suppress clutter HUD layers while the preview is active
    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (HIDDEN_LAYERS.contains(event.getName()) && shouldShowVariants()) {
            event.setCanceled(true);
        }
    }

    // Draw the ghost-slot preview right after the hotbar is rendered
    @SubscribeEvent
    public static void onRenderHotbar(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) return;

        if (!shouldShowVariants()) {
            BlockVariantSwapperClientState.scrollAnimation = 0.0f;
            return;
        }

        Minecraft client = Minecraft.getInstance();
        GuiGraphics context = event.getGuiGraphics();
        LocalPlayer player = client.player;
        ItemStack stack = player.getMainHandItem();

        Item originalItem = BlockVariantManager.getOriginalItem(stack.getItem());
        List<Item> variants = BlockVariantManager.getVariants(originalItem);
        int currentIndex = variants.indexOf(stack.getItem());
        if (currentIndex == -1) currentIndex = 0;

        int center = client.getWindow().getGuiScaledWidth() / 2;
        int hotbarY = client.getWindow().getGuiScaledHeight() - 22;
        int selectedSlot = player.getInventory().selected;

        // X position relative to the selected hotbar slot (vanilla 9-slot hotbar is 182px wide, centred at 91)
        int slotX = center - 91 + 2 + selectedSlot * 20;

        // Animate the scrolling
        float animationSpeed = 0.25f;
        if (BlockVariantSwapperClientState.scrollAnimation > 0) {
            BlockVariantSwapperClientState.scrollAnimation = Math.max(0, BlockVariantSwapperClientState.scrollAnimation - animationSpeed);
        } else if (BlockVariantSwapperClientState.scrollAnimation < 0) {
            BlockVariantSwapperClientState.scrollAnimation = Math.min(0, BlockVariantSwapperClientState.scrollAnimation + animationSpeed);
        }

        float yOffset = BlockVariantSwapperClientState.scrollAnimation * 20.0f;

        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableDepthTest();

        // 1. Background textures for the preview slots
        for (int i = 1; i < variants.size(); i++) {
            int baseY = hotbarY - (i * 20);
            context.blit(GHOST_SLOT_TEXTURE, slotX - 1, baseY - 1, 0, 0, 20, 20, 20, 20);
        }

        // 2. Items and their names
        for (int i = 1; i < variants.size(); i++) {
            int variantIndex = (currentIndex + i) % variants.size();
            Item variantItem = variants.get(variantIndex);
            ItemStack variantStack = new ItemStack(variantItem);

            int baseY = hotbarY - (i * 20);
            int itemY = (int) (baseY + yOffset);

            context.renderItem(variantStack, slotX + 1, itemY + 1);

            Component name = variantStack.getHoverName();
            int textX = slotX + 22;
            int textY = itemY + 5;
            context.drawString(client.font, name, textX, textY, 0xFFFFFF);
        }

        RenderSystem.disableBlend();
    }
}
