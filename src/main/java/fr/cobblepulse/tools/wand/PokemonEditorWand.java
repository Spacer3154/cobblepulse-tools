package fr.cobblepulse.tools.wand;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Baguette d'edition de Pokemon (outil staff) : clic droit sur un Pokemon (sauvage, sorti par un joueur) pour ouvrir
 * l'editeur ; clic droit sur un joueur : son equipe ; clic dans le vide : sa propre equipe. L'interaction elle-meme est interceptee dans {@link WandService} avant celle du Pokemon.
 */
public class PokemonEditorWand extends Item {
    public PokemonEditorWand(Properties props) {
        super(props);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // clic dans le vide : sa propre equipe
        if (!level.isClientSide && player instanceof net.minecraft.server.level.ServerPlayer sp) WandService.useInAir(sp);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.cobblepulse_tools.pokemon_editor_wand.tooltip1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.cobblepulse_tools.pokemon_editor_wand.tooltip2").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.cobblepulse_tools.pokemon_editor_wand.tooltip3").withStyle(ChatFormatting.DARK_RED));
    }
}
