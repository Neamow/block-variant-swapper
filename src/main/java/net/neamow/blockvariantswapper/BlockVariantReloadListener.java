package net.neamow.blockvariantswapper;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

// Simple reload listener that triggers variant manager initialisation on world load and /reload
public class BlockVariantReloadListener extends SimplePreparableReloadListener<Void> {

    @Override
    protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
        // Nothing to prepare; config loading happens in apply()
        return null;
    }

    @Override
    protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {
        BlockVariantManager.initialize();
    }
}
