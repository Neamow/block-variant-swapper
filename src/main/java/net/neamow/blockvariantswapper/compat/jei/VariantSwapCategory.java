package net.neamow.blockvariantswapper.compat.jei;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.gui.widgets.IScrollGridWidget;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neamow.blockvariantswapper.BlockVariantSwapper;
import net.neamow.blockvariantswapper.client.ModKeyBinding;

import java.util.List;

// JEI category "recipe" page
public class VariantSwapCategory implements IRecipeCategory<VariantSwapRecipe> {
    public static final ResourceLocation UID = BlockVariantSwapper.id("variant_swap");
    public static final RecipeType<VariantSwapRecipe> RECIPE_TYPE =
            RecipeType.create(UID.getNamespace(), UID.getPath(), VariantSwapRecipe.class);

    private static final int SLOT = 18;              // one slot cell including its 2px border
    private static final int COLUMNS = 7;            // variants per row
    private static final int VISIBLE_ROWS = 3;       // rows shown at once; extra rows scroll into view
    private static final int SCROLLBAR_WIDTH = 14;   // room for the grid's scrollbar on the right
    private static final int PADDING = 4;            // spacing

    // Top row: base block slot with a "Base block" label to its right
    private static final int BASE_SLOT_X = PADDING;
    private static final int BASE_SLOT_Y = PADDING;
    private static final int BASE_LABEL_X = BASE_SLOT_X + SLOT + 4;

    // Hint line below the base row
    private static final int HINT_Y = BASE_SLOT_Y + SLOT + 4;

    // Scrolling variants grid below the hint
    private static final int GRID_X = PADDING;
    private static final int GRID_Y = HINT_Y + 12;

    // Fixed category size: the scroll grid keeps a constant footprint regardless of family size
    private static final int WIDTH = GRID_X + COLUMNS * SLOT + SCROLLBAR_WIDTH + PADDING;
    private static final int HEIGHT = GRID_Y + VISIBLE_ROWS * SLOT + PADDING;

    // Category tab icon is 16x16 (JEI default tab icon size)
    private static final int ICON_SIZE = 16;
    // Custom texture overlay (a swap arrow) drawn on top of the item icon
    private static final ResourceLocation OVERLAY_TEXTURE = BlockVariantSwapper.id("textures/gui/jei_category.png");

    private final IDrawable icon;

    public VariantSwapCategory(IGuiHelper guiHelper) {
        // Composite tab icon: draw the oak stairs item first (uses the item renderer so it stays sharp at any resolution)
        // Swap-arrow overlay icon on top
        // drawableBuilder with setTextureSize is used because plain createDrawable assumes a 256x256 sheet
        IDrawable base = guiHelper.createDrawableItemStack(new ItemStack(Items.OAK_STAIRS));
        IDrawable overlay = guiHelper.drawableBuilder(OVERLAY_TEXTURE, 0, 0, ICON_SIZE, ICON_SIZE)
                .setTextureSize(ICON_SIZE, ICON_SIZE)
                .build();
        this.icon = new CompositeDrawable(base, overlay);
    }

    // Draws one IDrawable over another at the same position
    private record CompositeDrawable(IDrawable base, IDrawable overlay) implements IDrawable {
        @Override
        public int getWidth() {
            return base.getWidth();
        }

        @Override
        public int getHeight() {
            return base.getHeight();
        }

        @Override
        public void draw(GuiGraphics guiGraphics, int xOffset, int yOffset) {
            base.draw(guiGraphics, xOffset, yOffset);
            overlay.draw(guiGraphics, xOffset, yOffset);
        }
    }

    @Override
    public RecipeType<VariantSwapRecipe> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.blockvariantswapper.jei.category");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    // getBackground() is deprecated and null by default, so we supply the fixed size ourselves
    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, VariantSwapRecipe recipe, IFocusGroup focuses) {
        // Base block on the top row (the input everything reverts to), at a fixed position
        builder.addSlot(RecipeIngredientRole.INPUT, BASE_SLOT_X, BASE_SLOT_Y)
                .setStandardSlotBackground()
                .addIngredients(VanillaTypes.ITEM_STACK, List.of(new ItemStack(recipe.base())));

        // Variant slots (outputs). No positions here: the scroll grid lays them out in createRecipeExtras
        for (Item variant : recipe.variants()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 0, 0)
                    .setStandardSlotBackground()
                    .addIngredients(VanillaTypes.ITEM_STACK, List.of(new ItemStack(variant)));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, VariantSwapRecipe recipe, IFocusGroup focuses) {
        // Hand the variant (output) slots to a scroll grid so large families stay compact and scrollable.
        // The base slot is an input, so grabbing the output slots gives us exactly the variants
        List<IRecipeSlotDrawable> variantSlots = builder.getRecipeSlots().getSlots(RecipeIngredientRole.OUTPUT);
        if (variantSlots.isEmpty()) return;

        IScrollGridWidget grid = builder.addScrollGridWidget(variantSlots, COLUMNS, VISIBLE_ROWS);
        grid.setPosition(GRID_X, GRID_Y);
    }

    @Override
    public void draw(VariantSwapRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;

        // Base block label to the right of the base slot, vertically centred against it
        Component baseLabel = Component.translatable("gui.blockvariantswapper.jei.base");
        int labelY = BASE_SLOT_Y + (SLOT - font.lineHeight) / 2;
        guiGraphics.drawString(font, baseLabel, BASE_LABEL_X, labelY, 0x555555, false);

        // Keybind hint below the base row. The key comes from the live keybind, so it follows any rebind
        Component hint = Component.translatable("gui.blockvariantswapper.jei.hint", ModKeyBinding.SWAP_KEY.getTranslatedKeyMessage());
        guiGraphics.drawString(font, hint, PADDING, HINT_Y, 0x555555, false);
    }

    // Stable, unique id per entry (keyed on the base) so JEI can bookmark and identify families
    @Override
    public ResourceLocation getRegistryName(VariantSwapRecipe recipe) {
        return BuiltInRegistries.ITEM.getKey(recipe.base());
    }
}
