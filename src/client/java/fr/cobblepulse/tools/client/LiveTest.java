package fr.cobblepulse.tools.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import fr.cobblepulse.tools.CobblePulseTools;
import fr.cobblepulse.tools.ModContent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Test de bout en bout (CI, -Dcobblepulse_tools.livetest=true) : vrai client connecte au serveur de test. Poussee du
 * joueur (calculee par le client) jusqu'a la Stick Plate, anti-boucle sur le joueur, orientation a la pose, pose
 * refusee qui fermerait une boucle, editeur ouvert par commande (equipe) et par la baguette (sauvage), application.
 */
final class LiveTest {
    private static final String TAG = "[CobblePulse-Tools][livetest] ";
    private static final List<Consumer<Minecraft>> STEPS = new ArrayList<>();
    private static int ticks, start = -1, pass, fail;
    private static double lx, lz;

    private LiveTest() {}

    static void install() {
        ClientTickEvents.END_CLIENT_TICK.register(LiveTest::tick);
    }

    private static void check(String name, boolean ok, String detail) {
        if (ok) pass++;
        else fail++;
        CobblePulseTools.LOG.info(TAG + "{} {}{}", ok ? "PASS" : "FAIL", name, detail == null ? "" : " - " + detail);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), c -> CobblePulseTools.LOG.info(TAG + "capture {}", name));
    }

    private static void cmd(Minecraft mc, String c) {
        mc.player.connection.sendCommand(c);
    }

    private static String where(Minecraft mc) {
        return String.format("x=%.2f y=%.2f z=%.2f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private static void select(Minecraft mc, Item item) {
        var inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(item)) {
                inv.selected = i;
                return;
            }
        }
    }

    private static void useOn(Minecraft mc, BlockPos floor) {
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
                new BlockHitResult(new Vec3(floor.getX() + 0.5, floor.getY() + 1.0, floor.getZ() + 0.5), Direction.UP, floor, false));
    }

    private static EditorScreen editor(Minecraft mc) {
        return mc.screen instanceof EditorScreen e ? e : null;
    }

    static final String RAICHU = "Sparky (Raichu-Alola) (F) @ Leftovers\nAbility: Surge Surfer\nLevel: 60\nShiny: Yes\nTera Type: Electric\n"
            + "EVs: 252 SpA / 4 SpD / 252 Spe\nTimid Nature\nIVs: 0 Atk\n- Thunderbolt\n- Psychic\n- Focus Blast\n- Nasty Plot";
    static final String GARCHOMP = "Garchomp @ Choice Scarf\nAbility: Rough Skin\nLevel: 50\nEVs: 252 Atk / 4 SpD / 252 Spe\nJolly Nature\n- Earthquake\n- Outrage";
    static final String TEAM = "=== [gen9] Test ===\n\nGholdengo @ Choice Specs\nAbility: Good as Gold\nLevel: 70\nEVs: 252 SpA / 252 Spe\nTimid Nature\n"
            + "- Make It Rain\n- Shadow Ball\n\nDragapult @ Life Orb\nAbility: Infiltrator\nLevel: 70\nShiny: Yes\nEVs: 252 Atk / 252 Spe\nJolly Nature\n- Dragon Darts\n- Phantom Force";

    // terrain de test dans le ciel
    private static final int X = 3000, Y = 300, Z = 3000;

    static {
        STEPS.add(mc -> {
            cmd(mc, "gamemode creative");
            cmd(mc, "forceload add " + (X - 8) + " " + (Z - 8) + " " + (X + 32) + " " + (Z + 16));
        });
        STEPS.add(mc -> cmd(mc, "tp @s " + X + ".5 " + (Y + 3) + " " + Z + ".5"));
        STEPS.add(mc -> {
            cmd(mc, "fill " + (X - 4) + " " + Y + " " + (Z - 6) + " " + (X + 30) + " " + Y + " " + (Z + 10) + " minecraft:stone");
            cmd(mc, "fill " + (X - 4) + " " + (Y + 1) + " " + (Z - 6) + " " + (X + 30) + " " + (Y + 4) + " " + (Z + 10) + " minecraft:air");
        });
        STEPS.add(mc -> {
            for (int i = 0; i < 5; i++) cmd(mc, "setblock " + (X + i) + " " + (Y + 1) + " " + Z + " cobblepulse_tools:movement_plate[facing=east]");
            cmd(mc, "setblock " + (X + 5) + " " + (Y + 1) + " " + Z + " cobblepulse_tools:stick_plate[facing=east]");
            // boucle 2x2 posee par commande (contourne la verification a la pose)
            cmd(mc, "setblock " + (X + 20) + " " + (Y + 1) + " " + Z + " cobblepulse_tools:movement_plate[facing=east]");
            cmd(mc, "setblock " + (X + 21) + " " + (Y + 1) + " " + Z + " cobblepulse_tools:movement_plate[facing=south]");
            cmd(mc, "setblock " + (X + 21) + " " + (Y + 1) + " " + (Z + 1) + " cobblepulse_tools:movement_plate[facing=west]");
            cmd(mc, "setblock " + (X + 20) + " " + (Y + 1) + " " + (Z + 1) + " cobblepulse_tools:movement_plate[facing=north]");
        });
        STEPS.add(mc -> cmd(mc, "tp @s " + X + ".5 " + (Y + 1.07) + " " + Z + ".5 -90 25"));
        STEPS.add(mc -> {
            check("joueur_pousse_puis_arrete", Math.floor(mc.player.getX()) == X + 5 && Math.abs(mc.player.getDeltaMovement().x) < 0.05, where(mc));
            shot(mc, "live_01_plaques");
        });
        STEPS.add(mc -> cmd(mc, "tp @s " + (X + 20) + ".5 " + (Y + 1.07) + " " + Z + ".5 -90 25"));
        STEPS.add(mc -> {
            lx = mc.player.getX();
            lz = mc.player.getZ();
        });
        STEPS.add(mc -> check("boucle_arretee_pour_le_joueur", Math.abs(mc.player.getX() - lx) < 0.05 && Math.abs(mc.player.getZ() - lz) < 0.05
                && mc.player.getX() >= X + 20 && mc.player.getX() < X + 22 && mc.player.getZ() >= Z && mc.player.getZ() < Z + 2, where(mc)));
        // pose : orientation et boucle refusee
        STEPS.add(mc -> {
            cmd(mc, "give @s cobblepulse_tools:movement_plate 8");
            cmd(mc, "setblock " + (X + 14) + " " + (Y + 1) + " " + (Z + 6) + " cobblepulse_tools:movement_plate[facing=west]");
            cmd(mc, "tp @s " + (X + 10) + ".5 " + (Y + 1) + " " + (Z + 6) + ".5 -90 50");
        });
        STEPS.add(mc -> {
            select(mc, ModContent.MOVEMENT_PLATE_ITEM);
            mc.player.setYRot(-90);
            useOn(mc, new BlockPos(X + 12, Y, Z + 6));
        });
        STEPS.add(mc -> {
            BlockState st = mc.level.getBlockState(new BlockPos(X + 12, Y + 1, Z + 6));
            check("pose_dans_le_sens_du_regard", st.is(ModContent.MOVEMENT_PLATE) && st.getValue(HorizontalDirectionalBlock.FACING) == Direction.EAST, st.toString());
            mc.player.setYRot(-90);
            useOn(mc, new BlockPos(X + 13, Y, Z + 6));
        });
        STEPS.add(mc -> {
            BlockState st = mc.level.getBlockState(new BlockPos(X + 13, Y + 1, Z + 6));
            check("pose_refusee_si_boucle", st.isAir(), st.toString());
            shot(mc, "live_02_pose");
        });
        // editeur : equipe du joueur
        STEPS.add(mc -> cmd(mc, "pokegive @s pikachu level=10"));
        STEPS.add(mc -> cmd(mc, "cptools editer " + mc.player.getGameProfile().getName() + " 1"));
        STEPS.add(mc -> {
            EditorScreen e = editor(mc);
            check("editeur_equipe_ouvert", e != null && "party".equals(e.kind()) && e.speciesId().equals("cobblemon:pikachu"), e == null ? "aucun écran" : e.kind());
            shot(mc, "live_03_editeur_general");
            if (e != null) e.setTab(1);
        });
        STEPS.add(mc -> {
            shot(mc, "live_04_editeur_stats");
            EditorScreen e = editor(mc);
            if (e != null) e.setTab(2);
        });
        STEPS.add(mc -> {
            EditorScreen e = editor(mc);
            check("capacites_apprenables", e != null && e.learnableCount() > 20, e == null ? "" : String.valueOf(e.learnableCount()));
            shot(mc, "live_05_editeur_capacites");
            if (e != null) e.setTab(3);
        });
        STEPS.add(mc -> {
            shot(mc, "live_06_editeur_divers");
            EditorScreen e = editor(mc);
            if (e != null) {
                e.setTab(0);
                e.setLevelText("42");
                e.setShiny(true);
                e.apply();
            }
        });
        STEPS.add(mc -> {
            EditorScreen e = editor(mc);
            Pokemon p = CobblemonClient.INSTANCE.getStorage().getParty().get(0);
            check("edition_equipe_appliquee", e != null && e.lastOk && p != null && p.getLevel() == 42 && p.getShiny(),
                    (p == null ? "vide" : p.getLevel() + " shiny=" + p.getShiny()) + " " + (e == null ? "" : e.status()));
            shot(mc, "live_07_editeur_applique");
            if (e != null) {
                e.setLevelText("999");
                e.apply();
            }
        });
        STEPS.add(mc -> {
            EditorScreen e = editor(mc);
            check("valeur_refusee_affichee", e != null && !e.lastOk && String.join(" ", e.status()).contains("Niveau"), e == null ? "" : String.valueOf(e.status()));
            if (e != null) e.onClose();
        });
        // baguette sur un Pokemon sauvage
        STEPS.add(mc -> {
            cmd(mc, "cptools baguette");
            cmd(mc, "tp @s " + (X + 5) + ".5 " + (Y + 1) + " " + (Z + 8) + ".5 0 30");
        });
        STEPS.add(mc -> cmd(mc, "pokespawn pikachu level=5"));
        STEPS.add(mc -> {
            select(mc, ModContent.EDITOR_WAND);
            PokemonEntity wild = null;
            for (PokemonEntity pe : mc.level.getEntitiesOfClass(PokemonEntity.class, mc.player.getBoundingBox().inflate(10))) {
                if (pe.getOwnerUUID() == null) wild = pe;
            }
            check("pokemon_sauvage_present", wild != null, mc.level.getEntitiesOfClass(PokemonEntity.class, mc.player.getBoundingBox().inflate(32)).stream()
                    .map(pe -> pe.getPokemon().getSpecies().getName() + "@" + (int) pe.getX() + "," + (int) pe.getY() + "," + (int) pe.getZ() + " owner=" + pe.getOwnerUUID()).toList().toString());
            if (wild != null) mc.gameMode.interact(mc.player, wild, InteractionHand.MAIN_HAND);
        });
        STEPS.add(mc -> {
            EditorScreen e = editor(mc);
            check("baguette_ouvre_l_editeur", e != null && "wild".equals(e.kind()), e == null ? "aucun écran" : e.kind());
            if (e != null) {
                e.chooseSpecies("cobblemon:raichu");
                e.apply();
            }
        });
        STEPS.add(mc -> {
            EditorScreen e = editor(mc);
            boolean raichu = false;
            for (PokemonEntity pe : mc.level.getEntitiesOfClass(PokemonEntity.class, mc.player.getBoundingBox().inflate(12))) {
                if (pe.getOwnerUUID() == null && pe.getPokemon().getSpecies().getName().equalsIgnoreCase("raichu")) raichu = true;
            }
            check("sauvage_modifie_visible", e != null && e.lastOk && raichu, e == null ? "" : String.valueOf(e.status()));
            shot(mc, "live_08_baguette_sauvage");
            if (e != null) e.onClose();
        });
        // baguette dans le vide : sa propre equipe ; imports Showdown
        STEPS.add(mc -> {
            select(mc, ModContent.EDITOR_WAND);
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        });
        STEPS.add(mc -> {
            PartyScreen ps = mc.screen instanceof PartyScreen p ? p : null;
            check("clic_dans_le_vide_ouvre_mon_equipe", ps != null && ps.owner().equals(mc.player.getGameProfile().getName()) && ps.slot(0) != null,
                    mc.screen == null ? "aucun écran" : mc.screen.getClass().getSimpleName());
            shot(mc, "live_10_mon_equipe");
            if (ps != null) ps.importText(1, GARCHOMP);
        });
        STEPS.add(mc -> {
            PartyScreen ps = mc.screen instanceof PartyScreen p ? p : null;
            Pokemon p1 = CobblemonClient.INSTANCE.getStorage().getParty().get(1);
            check("import_showdown_emplacement", ps != null && p1 != null && p1.getSpecies().getName().equalsIgnoreCase("garchomp")
                    && p1.getMoveSet().getMoves().size() == 2, (p1 == null ? "vide" : p1.getSpecies().getName()) + " " + (ps == null ? "" : ps.status()));
            if (ps != null) ps.importText(-1, TEAM);
        });
        STEPS.add(mc -> {
            PartyScreen ps = mc.screen instanceof PartyScreen p ? p : null;
            Pokemon p2 = CobblemonClient.INSTANCE.getStorage().getParty().get(2);
            Pokemon p3 = CobblemonClient.INSTANCE.getStorage().getParty().get(3);
            check("import_showdown_equipe", p2 != null && p3 != null && p2.getSpecies().getName().equalsIgnoreCase("gholdengo")
                    && p3.getSpecies().getName().equalsIgnoreCase("dragapult") && p3.getShiny(), ps == null ? "" : String.valueOf(ps.status()));
            shot(mc, "live_11_equipe_importee");
            if (ps != null) mc.setScreen(new ShowdownScreen(ps, "Importer une équipe (emplacements libres)", "Colle une équipe Showdown…", t -> {
            }));
            if (mc.screen instanceof ShowdownScreen sd) sd.setText(TEAM);
        });
        STEPS.add(mc -> {
            shot(mc, "live_12_fenetre_showdown");
            if (mc.screen instanceof ShowdownScreen sd) sd.onClose();
            if (mc.screen instanceof PartyScreen ps) ps.edit(0);
        });
        STEPS.add(mc -> {
            EditorScreen e = editor(mc);
            check("equipe_vers_editeur", e != null && "party".equals(e.kind()), e == null ? "aucun écran" : e.kind());
            if (e != null) e.importShowdown(RAICHU);
        });
        STEPS.add(mc -> {
            EditorScreen e = editor(mc);
            Pokemon p0 = CobblemonClient.INSTANCE.getStorage().getParty().get(0);
            check("import_showdown_dans_l_editeur", e != null && e.lastOk && p0 != null && p0.getSpecies().getName().equalsIgnoreCase("raichu")
                            && p0.getAspects().contains("alolan") && p0.getLevel() == 60 && p0.getNickname() != null && p0.getNickname().getString().equals("Sparky"),
                    (p0 == null ? "vide" : p0.getSpecies().getName() + " " + p0.getAspects() + " nv" + p0.getLevel()) + " " + (e == null ? "" : e.status()));
            check("export_showdown", e != null && e.exportText().contains("Raichu-Alola") && e.exportText().contains("Level: 60"),
                    e == null ? "" : e.exportText().replace('\n', '|'));
            shot(mc, "live_13_editeur_import");
            if (e != null) e.onClose();
        });
        STEPS.add(mc -> shot(mc, "live_09_monde"));
        // sans la permission, la baguette est detruite (dernier test : le joueur n'est plus operateur)
        STEPS.add(mc -> {
            select(mc, ModContent.EDITOR_WAND);
            cmd(mc, "deop " + mc.player.getGameProfile().getName());
        });
        STEPS.add(mc -> check("baguette_detruite_sans_permission", !mc.player.getInventory().contains(st -> st.is(ModContent.EDITOR_WAND)), ""));
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        ticks++;
        if (start < 0) {
            start = ticks;
            CobblePulseTools.LOG.info(TAG + "connecté, début dans 10 s");
        }
        int t = ticks - start - 200;
        if (t < 0 || t % 40 != 0) return;
        int i = t / 40;
        try {
            if (i < STEPS.size()) STEPS.get(i).accept(mc);
            else if (i == STEPS.size()) {
                CobblePulseTools.LOG.info(TAG + "LIVETEST SUMMARY: {} pass, {} fail", pass, fail);
                mc.stop();
            }
        } catch (Throwable e) {
            check("exception_etape_" + i, false, e.toString());
            CobblePulseTools.LOG.error(TAG + "erreur", e);
        }
    }
}
