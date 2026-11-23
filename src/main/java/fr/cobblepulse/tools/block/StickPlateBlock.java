package fr.cobblepulse.tools.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Stick Plate (Pixelmon) : ralentit comme le sable des ames (on peut sauter) et arrete net une entite lancee par une
 * Movement Plate. La marche normale n'est que ralentie.
 */
public class StickPlateBlock extends PlateBlock {
    public static final MapCodec<StickPlateBlock> CODEC = simpleCodec(StickPlateBlock::new);

    public StickPlateBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!onPlate(pos, entity) || !handledHere(level, entity)) return;
        if (consumePush(entity, level.getGameTime(), 10)) {
            Vec3 v = entity.getDeltaMovement();
            entity.setDeltaMovement(0, Math.min(v.y, 0), 0);
            entity.hasImpulse = true;
            endRide(entity);
        }
    }
}
