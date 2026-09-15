package net.neamow.blockvariantswapper;

import net.neamow.blockvariantswapper.client.ModKeyBinding;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

// Client-side entrypoint; only loads on the client (Dist.CLIENT)
@Mod(value = BlockVariantSwapper.MOD_ID, dist = Dist.CLIENT)
public class BlockVariantSwapperClient {

    public BlockVariantSwapperClient(IEventBus modEventBus) {
        // Register keybindings via the mod event bus
        modEventBus.addListener(this::registerKeyMappings);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeyBinding.register(event);
    }
}
