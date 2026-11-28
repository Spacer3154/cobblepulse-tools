package fr.cobblepulse.tools.client;

import com.google.gson.JsonObject;
import fr.cobblepulse.tools.block.ElevatorService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Touches de l'ascenseur : le serveur ne recoit ni le saut ni l'appui sur accroupi, le client les detecte (front
 * montant : un appui = un etage) quand le joueur est sur un ascenseur et demande le trajet au serveur, qui verifie tout.
 */
final class ElevatorInput {
    private static boolean wasJumping, wasSneaking, wasOnElevator;

    private ElevatorInput() {}

    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(ElevatorInput::tick);
    }

    private static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || p.input == null) {
            wasJumping = wasSneaking = wasOnElevator = false;
            return;
        }
        boolean jumping = p.input.jumping;
        boolean sneaking = p.input.shiftKeyDown;
        boolean onElevator = !p.getAbilities().flying && !p.isPassenger() && ElevatorService.under(mc.level, p) != null;
        // au tick du saut le joueur a deja decolle : on regarde s'il etait sur l'ascenseur au tick d'avant
        if (jumping && !wasJumping && (onElevator || wasOnElevator)) ToolsClient.send("elevator_up", new JsonObject());
        else if (sneaking && !wasSneaking && onElevator && p.onGround()) ToolsClient.send("elevator_down", new JsonObject());
        wasJumping = jumping;
        wasSneaking = sneaking;
        wasOnElevator = onElevator && p.onGround();
    }
}
