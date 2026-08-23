package net.neamow.blockvariantswapper;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

// Game-bus (server-side) event listeners
//
// Registers a data reload listener that (re)builds the variant families after all data has loaded,
// on world load and on /reload
//
// Note: RecipeManagerMixin also calls BlockVariantManager.initialize() before it filters recipes,
// so family data is always current at the moment it is needed regardless of listener ordering.
// This reload listener keeps the manager in sync for everything else (drops, containers, pickup, etc.)
@EventBusSubscriber(modid = BlockVariantSwapper.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class ModEvents {

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new VariantReloadListener());
    }

    // Simple reload listener: on every server data (re)load, rebuild the variant families
    // Nothing to prepare off-thread; the (idempotent) rebuild happens in the apply phase
    private static class VariantReloadListener extends SimplePreparableReloadListener<Void> {
        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void data, ResourceManager manager, ProfilerFiller profiler) {
            BlockVariantManager.initialize();
        }
    }
}
