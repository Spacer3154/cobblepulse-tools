package fr.cobblepulse.tools.block;

import com.mojang.serialization.MapCodec;
import fr.cobblepulse.tools.ToolsConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Movement Plate (Pixelmon) : propulse toute entite posee dessus (joueur, mob, Pokemon, objet) dans le sens de la
 * fleche, en la recentrant sur l'axe de la plaque. Les plaques s'enchainent ; une Stick Plate arrete le mouvement.
 * Boucles interdites : refusees a la pose, et arretees en jeu si elles existent quand meme (/setblock, structures).
 */
public class MovementPlateBlock extends PlateBlock {
    public static final MapCodec<MovementPlateBlock> CODEC = simpleCodec(MovementPlateBlock::new);
    /** Longueur maximale de chaine suivie pour detecter une boucle a la pose. */
    private static final int MAX_CHAIN = 1024;

    public MovementPlateBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        if (s != null && formsLoop(ctx.getLevel(), ctx.getClickedPos(), s.getValue(FACING))) {
            Player p = ctx.getPlayer();
            if (p != null && !ctx.getLevel().isClientSide) {
                p.displayClientMessage(Component.translatable("message.cobblepulse_tools.loop_refused"), true);
            }
            return null;
        }
        return s;
    }

    /**
     * Une plaque posee en {@code start} vers {@code dir} fermerait-elle une boucle ? On suit la chaine de Movement Plates
     * (meme hauteur, case suivante dans le sens de chaque fleche) ; revenir a {@code start} = boucle.
     */
    public static boolean formsLoop(BlockGetter level, BlockPos start, Direction dir) {
        BlockPos pos = start;
        Direction d = dir;
        for (int i = 0; i < MAX_CHAIN; i++) {
            pos = pos.relative(d);
            if (pos.equals(start)) return true;
            BlockState st = level.getBlockState(pos);
            if (!(st.getBlock() instanceof MovementPlateBlock)) return false;
            d = st.getValue(FACING);
        }
        return false;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!onPlate(pos, entity) || !handledHere(level, entity)) return;
        long now = level.getGameTime();
        if (!rideStep(entity, pos, now)) {
            // boucle : l'entite est arretee et n'est plus poussee tant qu'elle reste sur les plaques
            Vec3 v = entity.getDeltaMovement();
            if (v.horizontalDistanceSqr() > 1.0E-4) {
                entity.setDeltaMovement(0, Math.min(v.y, 0), 0);
                entity.hasImpulse = true;
            }
            return;
        }
        push(state.getValue(FACING), pos, entity);
        markPushed(entity, now);
    }

    /** Vitesse dans le sens de la fleche + rappel vers le centre de la plaque sur l'autre axe. */
    public static void push(Direction dir, BlockPos pos, Entity entity) {
        ToolsConfig cfg = ToolsConfig.get();
        double speed = cfg.movement_plate_speed;
        double k = cfg.movement_plate_centering;
        Vec3 v = entity.getDeltaMovement();
        double x, z;
        if (dir.getAxis() == Direction.Axis.X) {
            x = dir.getStepX() * speed;
            z = (pos.getZ() + 0.5 - entity.getZ()) * k;
        } else {
            z = dir.getStepZ() * speed;
            x = (pos.getX() + 0.5 - entity.getX()) * k;
        }
        entity.setDeltaMovement(x, v.y, z);
        entity.hasImpulse = true;
    }
}
