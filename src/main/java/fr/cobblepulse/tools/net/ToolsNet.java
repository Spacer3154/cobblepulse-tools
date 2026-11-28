package fr.cobblepulse.tools.net;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cobblepulse.tools.CobblePulseTools;
import fr.cobblepulse.tools.ToolsConfig;
import fr.cobblepulse.tools.wand.WandService;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Enregistrement des paquets ; les actions du client sont toujours re-verifiees cote serveur. */
public final class ToolsNet {
    public static final Gson GSON = new Gson();

    private ToolsNet() {}

    public static void registerTypes() {
        PayloadTypeRegistry.playC2S().register(ToolsPayloads.Action.TYPE, ToolsPayloads.Action.CODEC);
        PayloadTypeRegistry.playS2C().register(ToolsPayloads.Data.TYPE, ToolsPayloads.Data.CODEC);
    }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(ToolsPayloads.Action.TYPE, (payload, ctx) -> {
            ServerPlayer p = ctx.player();
            ctx.server().execute(() -> handle(p, payload.action(), payload.json()));
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendConfig(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            WandService.forget(handler.getPlayer().getUUID());
            fr.cobblepulse.tools.block.ElevatorService.forget(handler.getPlayer().getUUID());
        });
    }

    public static boolean hasClient(ServerPlayer p) {
        try {
            return p.connection != null && ServerPlayNetworking.canSend(p, ToolsPayloads.Data.TYPE);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void send(ServerPlayer p, String kind, JsonObject data) {
        if (!hasClient(p)) return;
        ServerPlayNetworking.send(p, new ToolsPayloads.Data(kind, GSON.toJson(data)));
    }

    /** Reglages utiles au client : la poussee d'un joueur est calculee chez lui. */
    public static void sendConfig(ServerPlayer p) {
        JsonObject o = new JsonObject();
        o.addProperty("speed", ToolsConfig.get().movement_plate_speed);
        o.addProperty("centering", ToolsConfig.get().movement_plate_centering);
        o.addProperty("elevator_max", ToolsConfig.get().elevator_max_distance);
        send(p, "config", o);
    }

    private static void handle(ServerPlayer p, String action, String json) {
        if ("elevator_up".equals(action) || "elevator_down".equals(action)) {
            fr.cobblepulse.tools.block.ElevatorService.ride(p, action.equals("elevator_up"));
            return;
        }
        JsonObject o;
        try {
            o = json == null || json.isBlank() ? new JsonObject() : JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            o = new JsonObject();
        }
        try {
            WandService.handleAction(p, action, o);
        } catch (Throwable t) {
            CobblePulseTools.LOG.error("[CobblePulse-Tools] Erreur sur l'action {} de {}", action, p.getGameProfile().getName(), t);
        }
    }
}
