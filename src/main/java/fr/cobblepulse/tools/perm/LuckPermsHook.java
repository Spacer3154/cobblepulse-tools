package fr.cobblepulse.tools.perm;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;

import java.util.UUID;

/** Seule classe qui touche l'API LuckPerms (chargee uniquement si le mod est present). */
final class LuckPermsHook {
    private LuckPermsHook() {}

    /** true / false si le noeud est defini (heritage et jokers compris), null sinon. */
    static Boolean check(UUID uuid, String node) {
        User u = LuckPermsProvider.get().getUserManager().getUser(uuid);
        if (u == null) return null;
        Tristate t = u.getCachedData().getPermissionData().checkPermission(node);
        return t == Tristate.UNDEFINED ? null : t.asBoolean();
    }
}
