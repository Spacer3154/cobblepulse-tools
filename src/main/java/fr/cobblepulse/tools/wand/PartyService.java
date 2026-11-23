package fr.cobblepulse.tools.wand;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.storage.party.PlayerPartyStore;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import fr.cobblepulse.tools.CobblePulseTools;
import fr.cobblepulse.tools.ModContent;
import fr.cobblepulse.tools.ToolsConfig;
import fr.cobblepulse.tools.net.ToolsNet;
import fr.cobblepulse.tools.perm.Perms;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Baguette sur un joueur (ou dans le vide = soi-meme) : vue de l'equipe, edition d'un emplacement, import Showdown
 * (un set dans un emplacement vide, ou une equipe entiere dans les emplacements libres). Confiscation automatique de
 * la baguette chez les joueurs sans la permission.
 */
public final class PartyService {
    private PartyService() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PartyService::confiscateTick);
    }

    // ------------------------------------------------------------------ confiscation

    private static int removeWands(Container c) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack st = c.getItem(i);
            if (!st.isEmpty() && st.is(ModContent.EDITOR_WAND)) {
                n += st.getCount();
                c.setItem(i, ItemStack.EMPTY);
            }
        }
        return n;
    }

    private static boolean holdsWand(ServerPlayer p) {
        if (p.getInventory().contains(st -> st.is(ModContent.EDITOR_WAND))) return true;
        if (p.containerMenu != null && p.containerMenu.getCarried().is(ModContent.EDITOR_WAND)) return true;
        Container ec = p.getEnderChestInventory();
        for (int i = 0; i < ec.getContainerSize(); i++) if (ec.getItem(i).is(ModContent.EDITOR_WAND)) return true;
        return false;
    }

    /** Detruit toutes les baguettes du joueur (inventaire, curseur, ender chest). */
    public static int confiscate(ServerPlayer p) {
        int n = removeWands(p.getInventory()) + removeWands(p.getEnderChestInventory());
        if (p.containerMenu != null && p.containerMenu.getCarried().is(ModContent.EDITOR_WAND)) {
            n += p.containerMenu.getCarried().getCount();
            p.containerMenu.setCarried(ItemStack.EMPTY);
        }
        if (n > 0) {
            p.containerMenu.broadcastChanges();
            p.inventoryMenu.broadcastChanges();
            WandService.msg(p, "Éditeur de Pokémon détruit : réservé au staff.", ChatFormatting.RED);
            CobblePulseTools.LOG.warn("[CobblePulse-Tools] {} avait {} Éditeur(s) de Pokémon sans la permission {} : détruit(s).",
                    p.getGameProfile().getName(), n, ToolsConfig.get().admin_permission);
        }
        return n;
    }

    private static void confiscateTick(MinecraftServer server) {
        if (ModContent.EDITOR_WAND == null || server.getTickCount() % 20 != 7) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (holdsWand(p) && !Perms.isAdmin(p)) confiscate(p);
        }
    }

    // ------------------------------------------------------------------ vue de l'equipe

    static JsonObject partyJson(ServerPlayer admin, ServerPlayer owner) {
        JsonObject o = new JsonObject();
        o.addProperty("owner", owner.getGameProfile().getName());
        o.addProperty("owner_uuid", owner.getUUID().toString());
        o.addProperty("self", owner == admin);
        JsonArray slots = new JsonArray();
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
        for (int i = 0; i < 6; i++) {
            Pokemon pk = party.get(i);
            if (pk == null) {
                slots.add(JsonNull.INSTANCE);
                continue;
            }
            JsonObject s = new JsonObject();
            s.addProperty("uuid", pk.getUuid().toString());
            s.addProperty("species", pk.getSpecies().getResourceIdentifier().toString());
            s.addProperty("nickname", pk.getNickname() == null ? "" : pk.getNickname().getString());
            s.addProperty("level", pk.getLevel());
            s.addProperty("shiny", pk.getShiny());
            s.addProperty("gender", PokemonData.gender(pk.getGender()));
            JsonArray aspects = new JsonArray();
            pk.getAspects().forEach(aspects::add);
            s.add("aspects", aspects);
            ItemStack held = pk.heldItem();
            s.addProperty("held", held == null || held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
            s.addProperty("fainted", pk.isFainted());
            slots.add(s);
        }
        o.add("slots", slots);
        return o;
    }

    public static void open(ServerPlayer admin, ServerPlayer owner) {
        if (!Perms.isAdmin(admin)) {
            confiscate(admin);
            return;
        }
        if (!ToolsNet.hasClient(admin)) {
            WandService.msg(admin, "L'éditeur demande le mod CobblePulse Tools côté client.", ChatFormatting.RED);
            return;
        }
        ToolsNet.send(admin, "party_open", partyJson(admin, owner));
    }

    private static ServerPlayer owner(ServerPlayer admin, JsonObject o) {
        try {
            UUID id = UUID.fromString(o.get("owner").getAsString());
            return admin.server.getPlayerList().getPlayer(id);
        } catch (Exception e) {
            return null;
        }
    }

    private static void reply(ServerPlayer admin, ServerPlayer owner, List<String> changed, List<String> errors) {
        JsonObject o = partyJson(admin, owner);
        JsonArray c = new JsonArray();
        changed.forEach(c::add);
        JsonArray e = new JsonArray();
        errors.forEach(e::add);
        o.add("changed", c);
        o.add("errors", e);
        ToolsNet.send(admin, "party_open", o);
    }

    /** Actions « party_* » de l'ecran d'equipe (permission re-verifiee). */
    static void handle(ServerPlayer admin, String action, JsonObject o) {
        if (!Perms.isAdmin(admin)) {
            confiscate(admin);
            return;
        }
        ServerPlayer owner = owner(admin, o);
        if (owner == null) {
            WandService.msg(admin, "Joueur introuvable (déconnecté ?).", ChatFormatting.RED);
            return;
        }
        int slot = o.has("slot") ? o.get("slot").getAsInt() : -1;
        PlayerPartyStore party = Cobblemon.INSTANCE.getStorage().getParty(owner);
        switch (action) {
            case "party_open" -> open(admin, owner);
            case "party_edit" -> {
                Pokemon pk = slot >= 0 && slot < 6 ? party.get(slot) : null;
                if (pk == null) reply(admin, owner, List.of(), List.of("Emplacement " + (slot + 1) + " vide."));
                else WandService.openForStore(admin, owner, pk);
            }
            case "party_import" -> importInto(admin, owner, party, slot, o.has("text") ? o.get("text").getAsString() : "");
            default -> {
            }
        }
    }

    /** Import Showdown : un set dans l'emplacement {@code slot}, ou (slot = -1) une equipe dans les emplacements libres. */
    static void importInto(ServerPlayer admin, ServerPlayer owner, PlayerPartyStore party, int slot, String text) {
        List<String> changed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> sets = ShowdownSets.splitTeam(text);
        if (sets.isEmpty()) errors.add("Rien à importer : colle un set ou une équipe au format Showdown.");
        if (slot >= 0) {
            if (slot > 5) errors.add("Emplacement invalide.");
            else if (party.get(slot) != null) errors.add("Emplacement " + (slot + 1) + " occupé : édite ce Pokémon et importe dans l'éditeur.");
            else if (sets.size() > 1) errors.add("Un seul set pour un emplacement (utilise « Importer une équipe »).");
            if (!errors.isEmpty()) {
                reply(admin, owner, changed, errors);
                return;
            }
        }
        int next = 0;
        for (String set : sets) {
            int target = slot;
            if (target < 0) {
                while (next < 6 && party.get(next) != null) next++;
                if (next >= 6) {
                    errors.add("Équipe pleine : sets restants ignorés.");
                    break;
                }
                target = next;
            }
            ShowdownSets.Parsed p = ShowdownSets.parse(set);
            for (String e : p.errors()) errors.add(p.label() + " : " + e);
            if (!p.ok()) continue;
            Pokemon pk = create(p.data(), errors);
            if (pk == null) continue;
            PokemonData.Result r = PokemonData.apply(pk, p.data());
            for (String e : r.errors) errors.add(pk.getSpecies().getName() + " : " + e);
            party.set(target, pk);
            changed.add("emplacement " + (target + 1) + " : " + pk.getSpecies().getName() + " nv. " + pk.getLevel());
            if (ToolsConfig.get().log_edits) {
                CobblePulseTools.LOG.info("[CobblePulse-Tools] {} a importé {} (Showdown) dans l'équipe de {}, emplacement {}",
                        admin.getGameProfile().getName(), pk.getSpecies().getName(), owner.getGameProfile().getName(), target + 1);
            }
            if (slot >= 0) break;
        }
        reply(admin, owner, changed, errors);
    }

    /** Nouveau Pokemon de l'espece et au niveau du set (le reste est applique ensuite). */
    public static Pokemon create(JsonObject d, List<String> errors) {
        try {
            int max = PokemonData.maxLevel(null);
            int lv = d.has("level") ? Math.max(1, Math.min(max, d.get("level").getAsInt())) : 100;
            if (d.has("level") && d.get("level").getAsInt() > max) errors.add("Niveau ramené à " + max + " (maximum du serveur)");
            d.addProperty("level", lv);
            PokemonProperties pp = PokemonProperties.Companion.parse("level=" + lv);
            pp.setSpecies(d.get("species").getAsString());
            Pokemon pk = pp.create();
            pk.heal();
            return pk;
        } catch (Throwable t) {
            errors.add("Création impossible : " + t.getMessage());
            return null;
        }
    }
}
