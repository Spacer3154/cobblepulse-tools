package fr.cobblepulse.tools.command;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.pc.PCPosition;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.cobblepulse.tools.ModContent;
import fr.cobblepulse.tools.ToolsConfig;
import fr.cobblepulse.tools.net.ToolsNet;
import fr.cobblepulse.tools.perm.Perms;
import fr.cobblepulse.tools.test.SelfTest;
import fr.cobblepulse.tools.wand.WandService;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /cptools (alias /cobblepulsetools), reserve au staff :
 * baguette [joueur] · editer &lt;joueur&gt; &lt;1-6&gt; · editer &lt;joueur&gt; pc &lt;boite&gt; &lt;emplacement&gt; · reload.
 */
public final class ToolsCommands {
    private ToolsCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var root = Commands.literal("cptools").requires(Perms::isAdmin)
                .then(Commands.literal("baguette")
                        .executes(c -> giveWand(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .executes(c -> giveWand(c.getSource(), EntityArgument.getPlayer(c, "joueur")))))
                .then(Commands.literal("editer")
                        .then(Commands.argument("joueur", EntityArgument.player())
                                .then(Commands.argument("emplacement", IntegerArgumentType.integer(1, 6))
                                        .executes(ToolsCommands::editParty))
                                .then(Commands.literal("pc")
                                        .then(Commands.argument("boite", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("emplacement", IntegerArgumentType.integer(1, 30))
                                                        .executes(ToolsCommands::editPc))))))
                .then(Commands.literal("reload").executes(c -> {
                    ToolsConfig.load();
                    for (ServerPlayer p : c.getSource().getServer().getPlayerList().getPlayers()) ToolsNet.sendConfig(p);
                    c.getSource().sendSuccess(() -> Component.literal("[Tools] tools.json rechargé.").withStyle(ChatFormatting.GREEN), true);
                    return 1;
                }));
        if (SelfTest.ENABLED) {
            root.then(Commands.literal("selftest").executes(c -> {
                SelfTest.start(c.getSource().getServer());
                return 1;
            }));
        }
        LiteralCommandNode<CommandSourceStack> node = d.register(root);
        d.register(Commands.literal("cobblepulsetools").requires(Perms::isAdmin).redirect(node));
    }

    private static int giveWand(CommandSourceStack src, ServerPlayer target) {
        ItemStack st = new ItemStack(ModContent.EDITOR_WAND);
        if (!target.getInventory().add(st)) target.drop(st, false);
        src.sendSuccess(() -> Component.literal("[Tools] Baguette d'édition donnée à " + target.getGameProfile().getName() + ".")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int editParty(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        ServerPlayer owner = EntityArgument.getPlayer(c, "joueur");
        int slot = IntegerArgumentType.getInteger(c, "emplacement");
        Pokemon pk = Cobblemon.INSTANCE.getStorage().getParty(owner).get(slot - 1);
        if (pk == null) {
            c.getSource().sendFailure(Component.literal("[Tools] Emplacement " + slot + " vide dans l'équipe de " + owner.getGameProfile().getName() + "."));
            return 0;
        }
        WandService.openForStore(admin, owner, pk);
        return 1;
    }

    private static int editPc(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer admin = c.getSource().getPlayerOrException();
        ServerPlayer owner = EntityArgument.getPlayer(c, "joueur");
        int box = IntegerArgumentType.getInteger(c, "boite");
        int slot = IntegerArgumentType.getInteger(c, "emplacement");
        Pokemon pk = null;
        try {
            pk = Cobblemon.INSTANCE.getStorage().getPC(owner).get(new PCPosition(box - 1, slot - 1));
        } catch (Throwable ignored) {
        }
        if (pk == null) {
            c.getSource().sendFailure(Component.literal("[Tools] Boîte " + box + ", emplacement " + slot + " vide (ou inexistant) dans le PC de "
                    + owner.getGameProfile().getName() + "."));
            return 0;
        }
        WandService.openForStore(admin, owner, pk);
        return 1;
    }
}
