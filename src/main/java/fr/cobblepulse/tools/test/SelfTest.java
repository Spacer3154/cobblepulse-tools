package fr.cobblepulse.tools.test;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cobblepulse.tools.CobblePulseTools;
import fr.cobblepulse.tools.ModContent;
import fr.cobblepulse.tools.block.MovementPlateBlock;
import fr.cobblepulse.tools.block.PlateBlock;
import fr.cobblepulse.tools.wand.PokemonData;
import fr.cobblepulse.tools.wand.WandService;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Auto-tests serveur (CI, -Dcobblepulse_tools.selftest=true puis /cptools selftest) : contenu enregistre, recettes au fer,
 * loot, tags, physique des plaques sur de vraies entites (chaine, Stick Plate, elan, Pokemon), anti-boucle (pose et
 * en jeu) et application de l'editeur sur un vrai Pokemon sauvage.
 */
public final class SelfTest {
    public static final boolean ENABLED = Boolean.getBoolean("cobblepulse_tools.selftest");
    private static final String TAG = "[CobblePulse-Tools][selftest] ";

    private record Task(int at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static int pass, fail, tick;
    private static boolean running;

    private SelfTest() {}

    public static void install() {
        ServerTickEvents.END_SERVER_TICK.register(s -> {
            if (!running) return;
            tick++;
            List<Task> due = new ArrayList<>();
            TASKS.removeIf(t -> {
                if (t.at() <= tick) {
                    due.add(t);
                    return true;
                }
                return false;
            });
            for (Task t : due) {
                try {
                    t.run().run();
                } catch (Throwable e) {
                    check("exception_t" + tick, false, e.toString());
                    CobblePulseTools.LOG.error(TAG + "erreur", e);
                }
            }
            if (TASKS.isEmpty()) {
                running = false;
                CobblePulseTools.LOG.info(TAG + "SELFTEST SUMMARY: {} pass, {} fail", pass, fail);
                CobblePulseTools.LOG.info(TAG + "SELFTEST DONE");
            }
        });
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) pass++;
        else fail++;
        CobblePulseTools.LOG.info(TAG + "{} {}{}", ok ? "PASS" : "FAIL", name, detail == null || detail.isEmpty() ? "" : " - " + detail);
    }

    private static void at(int t, Runnable r) {
        TASKS.add(new Task(t, r));
    }

    // zone de test, loin du spawn
    private static final int X0 = 2000, Y = 200, Z0 = 2000;

    private static BlockPos p(int dx, int dz) {
        return new BlockPos(X0 + dx, Y, Z0 + dz);
    }

    private static BlockState plate(Block b, Direction d) {
        return b.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d);
    }

    private static ItemEntity drop(ServerLevel lvl, BlockPos pos) {
        ItemEntity it = new ItemEntity(lvl, pos.getX() + 0.5, pos.getY() + 0.07, pos.getZ() + 0.5, new ItemStack(Items.COBBLESTONE));
        it.setDeltaMovement(0, 0, 0);
        it.setPickUpDelay(32767);
        it.setUnlimitedLifetime();
        lvl.addFreshEntity(it);
        return it;
    }

    private static String pos(Entity e) {
        return String.format("x=%.2f z=%.2f v=%.3f", e.getX(), e.getZ(), e.getDeltaMovement().horizontalDistance());
    }

    public static void start(MinecraftServer server) {
        TASKS.clear();
        pass = fail = tick = 0;
        running = true;
        ServerLevel lvl = server.overworld();
        for (int cx = (X0 - 16) >> 4; cx <= (X0 + 48) >> 4; cx++)
            for (int cz = (Z0 - 16) >> 4; cz <= (Z0 + 32) >> 4; cz++) lvl.setChunkForced(cx, cz, true);

        at(1, () -> contentTests(server));
        at(5, () -> {
            // sol de pierre
            for (int dx = -4; dx <= 44; dx++) for (int dz = -4; dz <= 24; dz++) {
                lvl.setBlock(new BlockPos(X0 + dx, Y - 1, Z0 + dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy < 3; dy++) lvl.setBlock(new BlockPos(X0 + dx, Y + dy, Z0 + dz), Blocks.AIR.defaultBlockState(), 2);
            }
        });
        // A : 5 Movement Plates vers l'est puis une Stick Plate
        final ItemEntity[] a = new ItemEntity[1];
        at(10, () -> {
            for (int i = 0; i < 5; i++) lvl.setBlock(p(i, 0), plate(ModContent.MOVEMENT_PLATE, Direction.EAST), 3);
            lvl.setBlock(p(5, 0), plate(ModContent.STICK_PLATE, Direction.EAST), 3);
            a[0] = drop(lvl, p(0, 0));
        });
        at(80, () -> check("chaine_puis_stick_plate", a[0] != null && a[0].isAlive() && Math.floor(a[0].getX()) == X0 + 5
                && a[0].getDeltaMovement().horizontalDistance() < 0.02 && Math.abs(a[0].getZ() - (Z0 + 0.5)) < 0.2, a[0] == null ? "" : pos(a[0])));
        // B : 4 plaques puis sol nu : l'elan emporte l'entite au-dela de la derniere plaque
        final ItemEntity[] b = new ItemEntity[1];
        at(10, () -> {
            for (int i = 0; i < 4; i++) lvl.setBlock(p(i, 4), plate(ModContent.MOVEMENT_PLATE, Direction.EAST), 3);
            b[0] = drop(lvl, p(0, 4));
        });
        at(80, () -> check("elan_apres_la_chaine", b[0] != null && b[0].getX() > X0 + 4.2, b[0] == null ? "" : pos(b[0])));
        // C : plaque vers l'ouest, objet pose en biais : recentre sur l'axe
        final ItemEntity[] c = new ItemEntity[1];
        at(10, () -> {
            for (int i = 0; i < 6; i++) lvl.setBlock(p(14 - i, 8), plate(ModContent.MOVEMENT_PLATE, Direction.WEST), 3);
            ItemEntity it = drop(lvl, p(14, 8));
            it.setPos(X0 + 14.5, Y + 0.07, Z0 + 8.85);
            c[0] = it;
        });
        at(80, () -> check("ouest_et_recentrage", c[0] != null && c[0].getX() < X0 + 9.5 && Math.abs(c[0].getZ() - (Z0 + 8.5)) < 0.15,
                c[0] == null ? "" : pos(c[0])));
        // D : un mob (cochon) pousse vers le nord
        final Entity[] pig = new Entity[1];
        at(10, () -> {
            for (int i = 0; i < 6; i++) lvl.setBlock(p(20, 20 - i), plate(ModContent.MOVEMENT_PLATE, Direction.NORTH), 3);
            Entity e = EntityType.PIG.create(lvl);
            if (e != null) {
                e.moveTo(X0 + 20.5, Y + 0.07, Z0 + 20.5, 0, 0);
                lvl.addFreshEntity(e);
            }
            pig[0] = e;
        });
        at(60, () -> check("mob_pousse_vers_le_nord", pig[0] != null && pig[0].getZ() < Z0 + 16, pig[0] == null ? "absent" : pos(pig[0])));
        // E : Stick Plate = sable des ames
        at(12, () -> check("stick_plate_ralentit", Math.abs(ModContent.STICK_PLATE.getSpeedFactor() - 0.4f) < 1e-4,
                String.valueOf(ModContent.STICK_PLATE.getSpeedFactor())));
        // F : boucle 2x2 posee par /setblock : arretee en jeu
        final ItemEntity[] f = new ItemEntity[1];
        final double[] fpos = new double[2];
        at(10, () -> {
            lvl.setBlock(p(30, 0), plate(ModContent.MOVEMENT_PLATE, Direction.EAST), 3);
            lvl.setBlock(p(31, 0), plate(ModContent.MOVEMENT_PLATE, Direction.SOUTH), 3);
            lvl.setBlock(p(31, 1), plate(ModContent.MOVEMENT_PLATE, Direction.WEST), 3);
            lvl.setBlock(p(30, 1), plate(ModContent.MOVEMENT_PLATE, Direction.NORTH), 3);
            f[0] = drop(lvl, p(30, 0));
        });
        at(15, () -> check("boucle_detectee_a_la_pose", MovementPlateBlock.formsLoop(lvl, p(30, 0), Direction.EAST)
                && !MovementPlateBlock.formsLoop(lvl, p(0, 0), Direction.EAST)
                && MovementPlateBlock.formsLoop(lvl, p(40, 0), Direction.EAST) == false, ""));
        at(16, () -> {
            // deux plaques face a face : la seconde fermerait une boucle
            lvl.setBlock(p(40, 4), plate(ModContent.MOVEMENT_PLATE, Direction.EAST), 3);
            check("boucle_face_a_face", MovementPlateBlock.formsLoop(lvl, p(41, 4), Direction.WEST)
                    && !MovementPlateBlock.formsLoop(lvl, p(41, 4), Direction.EAST), "");
        });
        at(100, () -> {
            if (f[0] != null) {
                fpos[0] = f[0].getX();
                fpos[1] = f[0].getZ();
            }
        });
        at(140, () -> check("boucle_arretee_en_jeu", f[0] != null && f[0].isAlive() && PlateBlock.loopBlocked(f[0])
                && Math.abs(f[0].getX() - fpos[0]) < 0.05 && Math.abs(f[0].getZ() - fpos[1]) < 0.05, f[0] == null ? "" : pos(f[0])));
        // H : ascenseur. Colonne : y, y+5, y+9 (obstrue), y+12 ; y+30 hors de portee
        at(12, () -> {
            BlockPos base = new BlockPos(X0 + 36, Y + 2, Z0 + 14);
            for (int dy = -1; dy <= 40; dy++) lvl.setBlock(base.above(dy), Blocks.AIR.defaultBlockState(), 2);
            for (int dy : new int[]{0, 5, 9, 12, 30}) lvl.setBlock(base.above(dy), ModContent.ELEVATOR.defaultBlockState(), 3);
            lvl.setBlock(base.above(11), Blocks.STONE.defaultBlockState(), 3);
            int max = 10;
            BlockPos u1 = fr.cobblepulse.tools.block.ElevatorService.find(lvl, base, true, max, null);
            BlockPos u2 = fr.cobblepulse.tools.block.ElevatorService.find(lvl, base.above(5), true, max, null);
            BlockPos u3 = fr.cobblepulse.tools.block.ElevatorService.find(lvl, base.above(12), true, max, null);
            BlockPos u4 = fr.cobblepulse.tools.block.ElevatorService.find(lvl, base.above(12), true, 18, null);
            BlockPos d1 = fr.cobblepulse.tools.block.ElevatorService.find(lvl, base.above(12), false, max, null);
            BlockPos d2 = fr.cobblepulse.tools.block.ElevatorService.find(lvl, base, false, max, null);
            BlockPos u5 = fr.cobblepulse.tools.block.ElevatorService.find(lvl, base, true, 4, null);
            check("ascenseur_monte_au_suivant", base.above(5).equals(u1), String.valueOf(u1));
            check("ascenseur_saute_l_obstrue", base.above(12).equals(u2), String.valueOf(u2));
            check("ascenseur_portee_max", u3 == null && base.above(30).equals(u4) && u5 == null, u3 + " " + u4 + " " + u5);
            check("ascenseur_descend", base.above(5).equals(d1) && d2 == null, d1 + " " + d2);
        });
        // G : baguette (editeur) sur un vrai Pokemon sauvage
        at(20, () -> wandTests(server, lvl));
        at(150, () -> {
            for (int cx = (X0 - 16) >> 4; cx <= (X0 + 48) >> 4; cx++)
                for (int cz = (Z0 - 16) >> 4; cz <= (Z0 + 32) >> 4; cz++) lvl.setChunkForced(cx, cz, false);
        });
    }

    private static boolean accepts(RecipeHolder<?> r, net.minecraft.world.item.Item item) {
        for (Ingredient ing : r.value().getIngredients()) if (ing.test(new ItemStack(item))) return true;
        return false;
    }

    private static void contentTests(MinecraftServer server) {
        check("blocs_et_objets", BuiltInRegistries.BLOCK.containsKey(CobblePulseTools.id("movement_plate"))
                && BuiltInRegistries.BLOCK.containsKey(CobblePulseTools.id("stick_plate"))
                && BuiltInRegistries.ITEM.containsKey(CobblePulseTools.id("pokemon_editor_wand")), "");
        var mp = server.getRecipeManager().byKey(CobblePulseTools.id("movement_plate"));
        var sp = server.getRecipeManager().byKey(CobblePulseTools.id("stick_plate"));
        check("recette_movement_plate", mp.isPresent() && accepts(mp.get(), Items.IRON_INGOT) && accepts(mp.get(), Items.LIME_DYE)
                && accepts(mp.get(), Items.REDSTONE) && accepts(mp.get(), Items.REDSTONE_LAMP)
                && mp.get().value().getResultItem(server.registryAccess()).is(ModContent.MOVEMENT_PLATE_ITEM), mp.map(r -> r.id().toString()).orElse("absente"));
        check("recette_stick_plate", sp.isPresent() && accepts(sp.get(), Items.IRON_INGOT) && accepts(sp.get(), Items.BLUE_DYE)
                && accepts(sp.get(), Items.SOUL_SAND) && accepts(sp.get(), Items.REDSTONE_LAMP)
                && sp.get().value().getResultItem(server.registryAccess()).is(ModContent.STICK_PLATE_ITEM), sp.map(r -> r.id().toString()).orElse("absente"));
        check("baguette_sans_recette", server.getRecipeManager().getRecipes().stream()
                .noneMatch(r -> r.value().getResultItem(server.registryAccess()).is(ModContent.EDITOR_WAND)), "");
        var ev = server.getRecipeManager().byKey(CobblePulseTools.id("elevator"));
        check("recette_ascenseur", ev.isPresent() && accepts(ev.get(), Items.IRON_INGOT) && accepts(ev.get(), Items.REDSTONE)
                && ev.get().value().getIngredients().stream().filter(i -> i.test(new ItemStack(Items.IRON_INGOT))).count() == 8
                && ev.get().value().getResultItem(server.registryAccess()).is(ModContent.ELEVATOR_ITEM), ev.map(r -> r.id().toString()).orElse("absente"));
        for (Block b : new Block[]{ModContent.MOVEMENT_PLATE, ModContent.STICK_PLATE, ModContent.ELEVATOR}) {
            String n = BuiltInRegistries.BLOCK.getKey(b).getPath();
            LootTable lt = server.reloadableRegistries().getLootTable(b.getLootTable());
            ServerLevel lvl = server.overworld();
            List<ItemStack> drops = Block.getDrops(b.defaultBlockState(), lvl, BlockPos.ZERO, null, null, new ItemStack(Items.IRON_PICKAXE));
            check("loot_" + n, lt != LootTable.EMPTY && drops.size() == 1 && drops.get(0).is(b.asItem()), drops.toString());
            check("pioche_" + n, b.defaultBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE) && b.defaultBlockState().requiresCorrectToolForDrops(), "");
        }
        for (String n : new String[]{"movement_plate", "stick_plate", "elevator"}) {
            var adv = server.getAdvancements().get(CobblePulseTools.id("recipes/redstone/" + n));
            check("advancement_" + n, adv != null, "");
        }
    }

    static final String SET = "Sparky (Raichu-Alola) (F) @ Leftovers\nAbility: Surge Surfer\nLevel: 60\nShiny: Yes\nTera Type: Electric\n"
            + "EVs: 252 SpA / 4 SpD / 252 Spe\nTimid Nature\nIVs: 0 Atk\n- Thunderbolt\n- Psychic\n- Focus Blast\n- Nasty Plot";

    private static void showdownTests() {
        var p = fr.cobblepulse.tools.wand.ShowdownSets.parse(SET);
        JsonObject d = p.data();
        check("showdown_lecture", p.ok() && p.errors().isEmpty() && "cobblemon:raichu".equals(d.get("species").getAsString())
                        && "alola".equals(d.get("form").getAsString()) && d.get("level").getAsInt() == 60 && d.getAsJsonObject("ivs").get("atk").getAsInt() == 0
                        && d.getAsJsonObject("evs").get("spa").getAsInt() == 252 && d.getAsJsonArray("moves").size() == 4
                        && "cobblemon:leftovers".equals(d.get("held").getAsString()) && "female".equals(d.get("gender").getAsString()),
                d + " " + p.errors());
        List<String> errs = new ArrayList<>();
        Pokemon x = fr.cobblepulse.tools.wand.PartyService.create(d, errs);
        PokemonData.Result r = x == null ? null : PokemonData.apply(x, d);
        check("showdown_creation", x != null && r.errors.isEmpty() && errs.isEmpty() && x.getSpecies().getName().equalsIgnoreCase("raichu")
                        && "alola".equals(PokemonData.formId(x)) && x.getLevel() == 60 && x.getShiny() && x.getAbility().getName().equals("surgesurfer")
                        && x.getNickname() != null && x.getNickname().getString().equals("Sparky") && x.getNature().getName().getPath().equals("timid")
                        && x.getMoveSet().getMoves().size() == 4 && x.getTeraType().getId().getPath().equals("electric"),
                x == null ? String.valueOf(errs) : describe(x).get() + " " + (r == null ? "" : r.errors));
        if (x != null) {
            String exp = fr.cobblepulse.tools.wand.ShowdownSets.export(x);
            var p2 = fr.cobblepulse.tools.wand.ShowdownSets.parse(exp);
            boolean same = p2.ok() && p2.errors().isEmpty();
            for (String k : new String[]{"species", "form", "level", "nature", "ability", "held", "tera", "nickname", "gender"}) {
                same &= d.has(k) == p2.data().has(k) && (!d.has(k) || d.get(k).equals(p2.data().get(k)));
            }
            same &= d.get("ivs").equals(p2.data().get("ivs")) && d.get("evs").equals(p2.data().get("evs")) && d.get("moves").equals(p2.data().get("moves"));
            check("showdown_export_relu_identique", same, exp.replace('\n', '|') + " -> " + p2.data() + " " + p2.errors());
        }
        String team = "=== [gen9] Équipe ===\n\n" + SET + "\n\nGarchomp @ Choice Scarf\nAbility: Rough Skin\nEVs: 252 Atk / 4 SpD / 252 Spe\nJolly Nature\n- Earthquake\n- Outrage\n";
        var sets = fr.cobblepulse.tools.wand.ShowdownSets.splitTeam(team);
        var g = sets.size() == 2 ? fr.cobblepulse.tools.wand.ShowdownSets.parse(sets.get(1)) : null;
        check("showdown_equipe", sets.size() == 2 && g != null && g.ok() && g.errors().isEmpty() && g.data().get("level").getAsInt() == 100
                && "cobblemon:choice_scarf".equals(g.data().get("held").getAsString()), sets.size() + " " + (g == null ? "" : g.data() + " " + g.errors()));
        var bad = fr.cobblepulse.tools.wand.ShowdownSets.parse("Pasunpokemon @ Leftovers\n- Tackle");
        check("showdown_espece_inconnue", !bad.ok() && !bad.errors().isEmpty(), String.valueOf(bad.errors()));
    }

    private static Supplier<String> describe(Pokemon pk) {
        return () -> pk.getSpecies().getName() + " nv" + pk.getLevel() + (pk.getShiny() ? " shiny" : "") + " " + pk.getNature().getName().getPath()
                + " " + pk.getAbility().getName() + " " + pk.getMoveSet().getMoves().stream().map(m -> m.getName()).toList();
    }

    private static void wandTests(MinecraftServer server, ServerLevel lvl) {
        PokemonEntity pe = PokemonProperties.Companion.parse("pikachu level=10").createEntity(lvl);
        pe.moveTo(X0 + 40.5, Y, Z0 + 20.5, 0, 0);
        lvl.addFreshEntity(pe);
        Pokemon pk = pe.getPokemon();
        WandService.Session s = new WandService.Session(1, pk.getUuid(), pe.getUUID(), Level.OVERWORLD, null, "");
        check("baguette_retrouve_le_sauvage", WandService.resolve(s) == pk, "");
        JsonObject before = PokemonData.toJson(pk);
        check("fiche_pokemon", "cobblemon:pikachu".equals(before.get("species").getAsString()) && before.get("level").getAsInt() == 10
                && before.getAsJsonArray("moves").size() == 4 && before.getAsJsonObject("ivs").size() == 6, before.toString());

        JsonObject d = new JsonObject();
        d.addProperty("species", "cobblemon:raichu");
        d.addProperty("level", 50);
        d.addProperty("shiny", true);
        d.addProperty("nature", "cobblemon:modest");
        d.addProperty("ability", "lightningrod");
        JsonObject ivs = new JsonObject();
        JsonObject evs = new JsonObject();
        for (String st : PokemonData.STAT_IDS) {
            ivs.addProperty(st, 31);
            evs.addProperty(st, 0);
        }
        evs.addProperty("spa", 252);
        evs.addProperty("spe", 252);
        evs.addProperty("hp", 4);
        d.add("ivs", ivs);
        d.add("evs", evs);
        JsonArray mv = new JsonArray();
        for (String m : new String[]{"thunderbolt", "voltswitch", "focusblast", "nastyplot"}) mv.add(m);
        d.add("moves", mv);
        d.addProperty("held", "cobblemon:leftovers");
        d.addProperty("ball", "cobblemon:ultra_ball");
        d.addProperty("nickname", "Testeur");
        d.addProperty("tera", "cobblemon:fairy");
        d.addProperty("friendship", 200);
        d.addProperty("dmax", 5);
        PokemonData.Result r = PokemonData.apply(pk, d);
        boolean ivOk = true;
        for (String st : PokemonData.STAT_IDS) ivOk &= pk.getIvs().getOrDefault(PokemonData.stat(st)) == 31;
        check("edition_complete", r.errors.isEmpty() && pk.getSpecies().getName().equalsIgnoreCase("raichu") && pk.getLevel() == 50 && pk.getShiny()
                        && pk.getNature().getName().getPath().equals("modest") && pk.getAbility().getName().equals("lightningrod") && ivOk
                        && pk.getEvs().getOrDefault(PokemonData.stat("spa")) == 252 && pk.getEvs().getOrDefault(PokemonData.stat("hp")) == 4
                        && pk.getMoveSet().get(1) != null && pk.getMoveSet().get(1).getName().equals("voltswitch")
                        && pk.heldItem().is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("cobblemon:leftovers")))
                        && pk.getCaughtBall().getName().getPath().equals("ultra_ball") && pk.getNickname() != null
                        && pk.getNickname().getString().equals("Testeur") && pk.getTeraType().getId().getPath().equals("fairy")
                        && pk.getFriendship() == 200 && pk.getDmaxLevel() == 5,
                describe(pk).get() + " iv=" + ivOk + " ev=" + pk.getEvs().getOrDefault(PokemonData.stat("spa")) + "/" + pk.getEvs().getOrDefault(PokemonData.stat("hp"))
                        + " held=" + pk.heldItem() + " ball=" + pk.getCaughtBall().getName() + " nick=" + pk.getNickname() + " tera=" + pk.getTeraType().getId()
                        + " friend=" + pk.getFriendship() + " dmax=" + pk.getDmaxLevel() + " gmax=" + pk.getGmaxFactor() + " changed=" + r.changed + " erreurs=" + r.errors);
        pe.setPokemon(pk);
        at(tick + 10, () -> {
            String shown = String.valueOf(pe.getEntityData().get(PokemonEntity.Companion.getSPECIES()));
            check("entite_mise_a_jour", pe.isAlive() && shown.contains("raichu"), shown + " " + pe.getEntityData().get(PokemonEntity.Companion.getASPECTS()));
        });
        // Gigamax : accepte pour Pikachu, refuse (avec message) pour Raichu qui n'a pas de forme Gigamax
        Pokemon gp = PokemonProperties.Companion.parse("pikachu level=20").create();
        JsonObject gj = new JsonObject();
        gj.addProperty("gmax", true);
        PokemonData.Result gr = PokemonData.apply(gp, gj);
        PokemonData.Result gr2 = PokemonData.apply(pk, gj);
        check("gigamax", gp.getGmaxFactor() && gr.errors.isEmpty() && !pk.getGmaxFactor() && gr2.errors.size() == 1,
                gp.getGmaxFactor() + " " + gr.errors + " / " + pk.getGmaxFactor() + " " + gr2.errors);
        // valeurs refusees : rien n'est modifie
        JsonObject bad = new JsonObject();
        bad.addProperty("species", "cobblemon:pasunpokemon");
        bad.addProperty("level", 5000);
        JsonObject evBad = new JsonObject();
        for (String st : PokemonData.STAT_IDS) evBad.addProperty(st, 200);
        bad.add("evs", evBad);
        JsonArray dup = new JsonArray();
        dup.add("thunderbolt");
        dup.add("thunderbolt");
        bad.add("moves", dup);
        bad.addProperty("friendship", 999);
        bad.addProperty("gender", "genderless");
        PokemonData.Result rb = PokemonData.apply(pk, bad);
        check("valeurs_refusees", rb.errors.size() >= 6 && rb.changed.isEmpty() && pk.getLevel() == 50
                && pk.getEvs().getOrDefault(PokemonData.stat("spa")) == 252 && pk.getMoveSet().get(1).getName().equals("voltswitch"), String.valueOf(rb.errors));
        // forme regionale + genre + menthe + talent hors liste (force)
        Pokemon v = PokemonProperties.Companion.parse("vulpix level=20 gender=male").create();
        JsonObject fd = new JsonObject();
        fd.addProperty("form", "alola");
        fd.addProperty("gender", "female");
        fd.addProperty("minted", "cobblemon:timid");
        fd.addProperty("ability", "levitate");
        PokemonData.Result rf = PokemonData.apply(v, fd);
        check("forme_genre_menthe_talent", rf.errors.isEmpty() && "alola".equals(PokemonData.formId(v)) && v.getAspects().contains("alolan")
                        && v.getGender().name().equals("FEMALE") && v.getMintedNature() != null && v.getAbility().getName().equals("levitate")
                        && v.getAbility().getForced(),
                PokemonData.formId(v) + " " + v.getAspects() + " " + v.getGender() + " " + v.getAbility().getName() + " " + rf.errors);
        showdownTests();
        JsonObject back = new JsonObject();
        back.addProperty("form", "");
        back.addProperty("minted", "");
        PokemonData.Result rbk = PokemonData.apply(v, back);
        check("retour_forme_de_base", rbk.errors.isEmpty() && PokemonData.formId(v).isEmpty() && !v.getAspects().contains("alolan")
                && v.getMintedNature() == null, v.getAspects() + " " + rbk.errors);
    }
}
