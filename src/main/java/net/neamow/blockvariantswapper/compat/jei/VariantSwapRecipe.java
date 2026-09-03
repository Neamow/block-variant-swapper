package net.neamow.blockvariantswapper.compat.jei;

import net.minecraft.world.item.Item;

import java.util.List;

// A single JEI entry: one variant family, i.e. the base block and all the shape variants you can swap it into
// Displayed as the base plus a grid of variants
public record VariantSwapRecipe(Item base, List<Item> variants) {
}
