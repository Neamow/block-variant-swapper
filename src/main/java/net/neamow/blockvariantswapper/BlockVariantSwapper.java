package net.neamow.blockvariantswapper;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neamow.blockvariantswapper.network.NetworkHandler;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

// Main mod entrypoint for NeoForge
@Mod(BlockVariantSwapper.MOD_ID)
public class BlockVariantSwapper {
    public static final String MOD_ID = "blockvariantswapper";
    public static final Logger LOGGER = LogUtils.getLogger();

    public BlockVariantSwapper(IEventBus modEventBus) {
        LOGGER.info("Initializing Block Variant Swapper");

        // Register networking (packet types + server handlers) on the mod event bus
        modEventBus.addListener(NetworkHandler::register);

        // Ensure the user config directory exists for player overrides
        BlockVariantConfig.createConfigDirectory();

        // Register a data reload listener so the variant manager rebuilds on world load and /reload
        NeoForge.EVENT_BUS.addListener(this::onAddReloadListeners);
    }

    // Fires during server/data reload; we add a simple listener that initialises the variant maps
    private void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(id("block_variant_loader"), new BlockVariantReloadListener());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
