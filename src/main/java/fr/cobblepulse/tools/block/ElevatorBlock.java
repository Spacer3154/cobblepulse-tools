package fr.cobblepulse.tools.block;

import fr.cobblepulse.tools.ToolsConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * Ascenseur (Pixelmon) : debout dessus, sauter emmene au prochain ascenseur libre au-dessus dans la meme colonne,
 * s'accroupir au prochain en dessous. Distance maximale entre deux ascenseurs : {@code elevator_max_distance}
 * (tools.json). La logique est dans {@link ElevatorService}.
 */
public class ElevatorBlock extends Block {
    public ElevatorBlock(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("block.cobblepulse_tools.elevator.tooltip", ToolsConfig.get().elevator_max_distance)
                .withStyle(ChatFormatting.GRAY));
    }
}
