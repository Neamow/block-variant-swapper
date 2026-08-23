package net.neamow.blockvariantswapper;

import net.neamow.blockvariantswapper.client.ModKeyBinding;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

// Client entrypoint, register the swap key mapping on the mod event bus (client only)
@Mod(value = BlockVariantSwapper.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BlockVariantSwapper.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class BlockVariantSwapperClient {

    public BlockVariantSwapperClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBinding.SWAP_KEY);
    }
}
