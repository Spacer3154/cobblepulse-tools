package fr.cobblepulse.tools.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Plaque fine (hauteur d'un tapis) orientee : base commune de la Movement Plate et de la Stick Plate.
 * Posee, elle regarde a l'oppose du joueur (la fleche part de lui). Waterloggable.
 */
public abstract class PlateBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    protected static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);
    /** Derniere poussee recue par chaque entite (temps de jeu) : la Stick Plate n'arrete que ce mouvement-la. */
    private static final Map<Entity, Long> PUSHED = Collections.synchronizedMap(new WeakHashMap<>());

    protected PlateBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, WATERLOGGED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluid = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection())
                .setValue(WATERLOGGED, fluid.getType() == Fluids.WATER);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return !level.isEmptyBlock(pos.below());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (dir == Direction.DOWN && !state.canSurvive(level, pos)) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type == PathComputationType.WATER ? state.getFluidState().is(net.minecraft.tags.FluidTags.WATER) : type == PathComputationType.LAND;
    }

    /**
     * L'entite est-elle posee sur cette plaque : centre au-dessus de la case (une seule plaque agit a la fois quand
     * l'entite chevauche deux cases) et pas en train de sauter au-dessus ?
     */
    protected static boolean onPlate(BlockPos pos, Entity e) {
        return e.getY() < pos.getY() + 0.45 && Math.floor(e.getX()) == pos.getX() && Math.floor(e.getZ()) == pos.getZ();
    }

    /**
     * Le mouvement d'un joueur (et de ce qu'il monte) est calcule par son client : la plaque agit la-bas. Le reste
     * (mobs, Pokemon sauvages, objets) est deplace par le serveur.
     */
    protected static boolean handledHere(Level level, Entity e) {
        if (e.isSpectator() || e.isPassenger()) return false;
        if (e instanceof Player p && p.getAbilities().flying) return false;
        Player driver = e instanceof Player p ? p : e.getControllingPassenger() instanceof Player p2 ? p2 : null;
        if (driver != null) return level.isClientSide && driver.isLocalPlayer();
        return !level.isClientSide;
    }

    protected static void markPushed(Entity e, long time) {
        PUSHED.put(e, time);
    }

    /** Vrai (et oublie) si l'entite a ete poussee par une Movement Plate il y a moins de {@code within} ticks. */
    protected static boolean consumePush(Entity e, long time, int within) {
        Long t = PUSHED.get(e);
        if (t == null || time - t > within) return false;
        PUSHED.remove(e);
        return true;
    }

    public static boolean recentlyPushed(Entity e, long time, int within) {
        Long t = PUSHED.get(e);
        return t != null && time - t <= within;
    }

    // ---- anti-boucle en jeu ----

    /** Trajet en cours d'une entite sur des Movement Plates. */
    private static final class Ride {
        long last = Long.MIN_VALUE;
        long current = Long.MIN_VALUE;
        final java.util.Set<Long> visited = new java.util.HashSet<>();
        boolean blocked;
    }

    /** Un trajet se termine apres 1 s sans toucher de Movement Plate (l'elan qui franchit un trou reste le meme trajet). */
    public static final int RIDE_GAP_TICKS = 20;
    private static final Map<Entity, Ride> RIDES = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Enregistre le passage de l'entite sur la plaque {@code pos}. Faux si l'entite revient sur une plaque deja
     * traversee pendant le meme trajet (boucle) : elle n'est plus poussee tant qu'elle ne quitte pas les plaques.
     */
    protected static boolean rideStep(Entity e, BlockPos pos, long now) {
        Ride r = RIDES.computeIfAbsent(e, k -> new Ride());
        if (r.last == Long.MIN_VALUE || now - r.last > RIDE_GAP_TICKS || now < r.last) {
            r.visited.clear();
            r.blocked = false;
            r.current = Long.MIN_VALUE;
        }
        r.last = now;
        if (r.blocked) return false;
        long key = pos.asLong();
        if (key != r.current) {
            if (!r.visited.add(key)) {
                r.blocked = true;
                return false;
            }
            r.current = key;
            if (r.visited.size() > 4096) r.visited.clear();
        }
        return true;
    }

    /** Fin de trajet (arret sur une Stick Plate). */
    protected static void endRide(Entity e) {
        RIDES.remove(e);
    }

    /** Vrai si l'entite a ete arretee par l'anti-boucle (trajet en cours). */
    public static boolean loopBlocked(Entity e) {
        Ride r = RIDES.get(e);
        return r != null && r.blocked;
    }
}
