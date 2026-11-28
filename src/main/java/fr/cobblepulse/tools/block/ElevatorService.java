package fr.cobblepulse.tools.block;

import fr.cobblepulse.tools.ModContent;
import fr.cobblepulse.tools.ToolsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trajet en ascenseur. Le client detecte l'appui sur saut / accroupi (le serveur ne recoit pas ces touches) et
 * demande le trajet ; le serveur verifie tout : joueur debout sur un ascenseur, cible dans la meme colonne a
 * {@code elevator_max_distance} blocs au plus, place libre au-dessus de la cible (les ascenseurs obstrues sont sautes),
 * delai entre deux trajets.
 */
public final class ElevatorService {
    /** Delai minimal entre deux trajets d'un meme joueur (ticks). */
    public static final int COOLDOWN = 6;
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private ElevatorService() {}

    public static boolean isElevator(BlockGetter level, BlockPos pos) {
        return ModContent.ELEVATOR != null && level.getBlockState(pos).is(ModContent.ELEVATOR);
    }

    /** Ascenseur sous les pieds de l'entite (tolere le debut d'un saut et un tapis pose dessus), sinon null. */
    public static BlockPos under(Level level, Entity e) {
        for (double dy : new double[]{0.2, 0.6}) {
            BlockPos p = BlockPos.containing(e.getX(), e.getY() - dy, e.getZ());
            if (isElevator(level, p)) return p;
        }
        return null;
    }

    /** Hitbox de l'entite posee sur l'ascenseur {@code pos} (meme x / z qu'actuellement). */
    private static AABB boxOn(Entity e, BlockPos pos) {
        return e.getBoundingBox().move(0, pos.getY() + 1 - e.getY(), 0);
    }

    /**
     * Prochain ascenseur libre au-dessus (up) ou en dessous, dans la colonne de {@code from}, a {@code max} blocs au plus.
     * Un ascenseur sans place pour l'entite au-dessus est saute. Null si aucun.
     */
    public static BlockPos find(Level level, BlockPos from, boolean up, int max, Entity e) {
        for (int d = 1; d <= max; d++) {
            BlockPos p = from.above(up ? d : -d);
            if (p.getY() < level.getMinBuildHeight() || p.getY() >= level.getMaxBuildHeight()) return null;
            if (!isElevator(level, p)) continue;
            if (e == null ? level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                    && level.getBlockState(p.above(2)).getCollisionShape(level, p.above(2)).isEmpty()
                    : level.noCollision(e, boxOn(e, p))) {
                return p;
            }
        }
        return null;
    }

    /** Demande du client : monter (saut) ou descendre (accroupi). */
    public static void ride(ServerPlayer p, boolean up) {
        if (p.isSpectator() || p.isPassenger() || p.isSleeping() || !p.isAlive() || p.getAbilities().flying) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last >= 0 && now - last < COOLDOWN) return;
        BlockPos from = under(level, p);
        if (from == null) return;
        LAST.put(p.getUUID(), now);
        int max = ToolsConfig.get().elevator_max_distance;
        BlockPos to = find(level, from, up, max, p);
        if (to == null) {
            p.displayClientMessage(Component.translatable(up ? "message.cobblepulse_tools.elevator_none_up"
                    : "message.cobblepulse_tools.elevator_none_down", max), true);
            return;
        }
        double y = to.getY() + 1;
        p.connection.teleport(p.getX(), y, p.getZ(), p.getYRot(), p.getXRot());
        p.setDeltaMovement(0, 0, 0);
        p.resetFallDistance();
        if (ToolsConfig.get().elevator_sound) {
            level.playSound(null, p.getX(), y, p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 0.5f, up ? 1.3f : 1.0f);
        }
    }

    public static void forget(UUID player) {
        LAST.remove(player);
    }
}
