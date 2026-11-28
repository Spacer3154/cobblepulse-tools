package fr.cobblepulse.tools.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.cobblepulse.tools.CobblePulseTools;
import fr.cobblepulse.tools.ToolsConfig;
import fr.cobblepulse.tools.net.ToolsPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Cote client : reglages des plaques recus du serveur, ecran de l'editeur de Pokemon. */
public final class ToolsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(ToolsPayloads.Data.TYPE, (payload, ctx) -> ctx.client().execute(() -> handle(payload.kind(), payload.json())));
        ElevatorInput.install();
        if (Boolean.getBoolean("cobblepulse_tools.livetest")) LiveTest.install();
    }

    static void send(String action, JsonObject args) {
        if (!ClientPlayNetworking.canSend(ToolsPayloads.Action.TYPE)) return;
        ClientPlayNetworking.send(new ToolsPayloads.Action(action, args == null ? "{}" : args.toString()));
    }

    private static void handle(String kind, String json) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject o;
        try {
            o = JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        try {
            switch (kind) {
                case "config" -> ToolsConfig.applyFromServer(o.get("speed").getAsDouble(), o.get("centering").getAsDouble(),
                        o.has("elevator_max") ? o.get("elevator_max").getAsInt() : 10);
                case "editor_open" -> mc.setScreen(new EditorScreen(o));
                case "party_open" -> {
                    if (mc.screen instanceof PartyScreen ps && o.has("changed")) ps.update(o);
                    else {
                        PartyScreen ps = new PartyScreen(o);
                        mc.setScreen(ps);
                        if (o.has("changed")) ps.update(o);
                    }
                }
                case "editor_result" -> {
                    if (mc.screen instanceof EditorScreen e) e.onResult(o);
                    else if (mc.player != null) {
                        for (var err : o.getAsJsonArray("errors")) mc.player.displayClientMessage(Component.literal("[Tools] " + err.getAsString()), false);
                    }
                }
                default -> {
                }
            }
        } catch (Throwable t) {
            CobblePulseTools.LOG.error("[CobblePulse-Tools] Paquet {} illisible", kind, t);
        }
    }
}
