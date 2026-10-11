package net.neamow.blockvariantswapper;

import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
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

        // Widen variant-consuming recipes so a recipe that needs a variant child also accepts its base block
        // Must run after item tags are bound: at recipe-load time tags are not yet populated, so a tag ingredient like #wooden_slabs would resolve to nothing
        // Two hooks, each covering one entry point: ServerStartedEvent for world load, OnDatapackSyncEvent for /reload
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onDatapackSync);
    }

    // Fires during server/data reload; we add a simple listener that initialises the variant maps
    private void onAddReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(id("block_variant_loader"), new BlockVariantReloadListener());
    }

    // Fires once on initial world load, after tags are bound
    private void onServerStarted(ServerStartedEvent event) {
        RecipeWidener.widenAll(event.getServer());
    }

    // Fires on /reload (and per-player on login); gate on getPlayer() == null so we run on the full reload only
    private void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) return;
        RecipeWidener.widenAll(event.getPlayerList().getServer());
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
