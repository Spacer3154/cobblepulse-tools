package fr.cobblepulse.tools.perm;

import fr.cobblepulse.tools.CobblePulseTools;
import fr.cobblepulse.tools.ToolsConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Droit d'utiliser la baguette et /cptools : noeud LuckPerms (cobblepulse.tools.admin par defaut). Si LuckPerms est
 * absent ou que le noeud n'est defini nulle part, niveau d'operateur (2 par defaut). Un refus explicite dans LuckPerms
 * l'emporte sur l'operateur.
 */
public final class Perms {
    private static final boolean LUCKPERMS = FabricLoader.getInstance().isModLoaded("luckperms");
    private static boolean warned;

    private Perms() {}

    public static boolean isAdmin(ServerPlayer p) {
        if (p == null) return false;
        ToolsConfig cfg = ToolsConfig.get();
        if (LUCKPERMS) {
            try {
                Boolean b = LuckPermsHook.check(p.getUUID(), cfg.admin_permission);
                if (b != null) return b;
            } catch (Throwable t) {
                if (!warned) {
                    warned = true;
                    CobblePulseTools.LOG.warn("[CobblePulse-Tools] API LuckPerms indisponible, repli sur le niveau d'opérateur : {}", t.toString());
                }
            }
        }
        return p.hasPermissions(cfg.op_level_fallback);
    }

    /** Console et blocs de commande : autorises ; joueur : voir {@link #isAdmin(ServerPlayer)}. */
    public static boolean isAdmin(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer p) return isAdmin(p);
        return src.hasPermission(2);
    }
}
