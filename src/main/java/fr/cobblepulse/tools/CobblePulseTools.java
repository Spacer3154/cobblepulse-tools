package fr.cobblepulse.tools;

import fr.cobblepulse.tools.command.ToolsCommands;
import fr.cobblepulse.tools.net.ToolsNet;
import fr.cobblepulse.tools.test.SelfTest;
import fr.cobblepulse.tools.wand.WandService;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** CobblePulse Tools : Movement Plate, Stick Plate et Baguette d'edition de Pokemon. */
public final class CobblePulseTools implements ModInitializer {
    public static final String MOD_ID = "cobblepulse_tools";
    public static final Logger LOG = LoggerFactory.getLogger("CobblePulse-Tools");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ToolsConfig.load();
        ModContent.register();
        ToolsNet.registerTypes();
        ToolsNet.registerServer();
        WandService.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> ToolsCommands.register(dispatcher));
        if (SelfTest.ENABLED) SelfTest.install();
        LOG.info("[CobblePulse-Tools] Movement Plate, Stick Plate et Baguette d'édition prêtes (vitesse des plaques : {})",
                ToolsConfig.get().movement_plate_speed);
    }
}
