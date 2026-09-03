package net.neamow.blockvariantswapper.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neamow.blockvariantswapper.BlockVariantConfig;
import net.neamow.blockvariantswapper.BlockVariantSwapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// JEI plugin entry point
// On NeoForge JEI discovers this via the @JeiPlugin annotation (no entrypoint needed),
// and only loads it when JEI is present, so JEI stays an optional soft dependency
@JeiPlugin
public class BlockVariantSwapperJeiPlugin implements IModPlugin {
    private static final Identifier PLUGIN_UID = BlockVariantSwapper.id("jei");

    // Built once per plugin instance and reused by the register* passes
    // JEI creates a fresh plugin instance whenever it reloads, so this is naturally rebuilt on reload
    private List<VariantSwapRecipe> cachedRecipes;

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        // This method only runs when JEI has loaded our plugin, so reaching here confirms JEI is present
        BlockVariantSwapper.LOGGER.info("JEI detected, initialising Block Variant Swapper integration.");

        // Register our single custom category
        registration.addRecipeCategories(new VariantSwapCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // One entry per family, sourced from the config (see getRecipes)
        List<VariantSwapRecipe> recipes = getRecipes();
        registration.addRecipes(VariantSwapCategory.RECIPE_TYPE, recipes);
        BlockVariantSwapper.LOGGER.info("Registered " + recipes.size() + " variant families with JEI.");
    }

    // Lazily build and cache the entries, so the register* passes share one config load
    private List<VariantSwapRecipe> getRecipes() {
        if (cachedRecipes == null) {
            cachedRecipes = buildRecipes();
        }
        return cachedRecipes;
    }

    // Build one entry per variant family, pairing the base with all its variants.
    //
    // Read straight from BlockVariantConfig rather than BlockVariantManager: JEI registers its recipes at client resource load,
    // BEFORE any world is joined, but the manager is only populated on the server-data reload (world load or /reload)
    // Sourcing from the config, which loads purely from bundled jar resources plus user files, means the category is populated regardless of world state
    // Item ids are resolved against the registry, which is fully loaded by the time JEI runs
    private static List<VariantSwapRecipe> buildRecipes() {
        List<VariantSwapRecipe> recipes = new ArrayList<>();

        // Load the merged families (bundled defaults + user overrides), same source the manager uses
        BlockVariantConfig.load();
        Map<String, List<String>> families = BlockVariantConfig.getMergedVariants();

        for (Map.Entry<String, List<String>> family : families.entrySet()) {
            // First id in the list is the base everything reverts to
            Item base = resolveItem(family.getKey());
            if (base == null) continue;

            // Collect the family's variants (everything that resolves and is not the base itself)
            List<Item> variants = new ArrayList<>();
            for (String variantId : family.getValue()) {
                Item variant = resolveItem(variantId);
                if (variant == null || variant == base) continue;
                if (!variants.contains(variant)) variants.add(variant);
            }

            // One entry per family: skip families that ended up with no valid variants
            if (!variants.isEmpty()) {
                recipes.add(new VariantSwapRecipe(base, variants));
            }
        }
        return recipes;
    }

    // Resolve a "namespace:path" id to a registered item, or null if the id is invalid or absent
    // Mirrors the manager's silent-skip behaviour so missing modded items just do not appear
    private static Item resolveItem(String id) {
        Identifier parsed = Identifier.tryParse(id);
        if (parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed)) return null;
        return BuiltInRegistries.ITEM.getValue(parsed);
    }
}
