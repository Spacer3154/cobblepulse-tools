package fr.cobblepulse.tools.wand;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.storage.StoreCoordinates;
import com.cobblemon.mod.common.api.storage.pc.PCStore;
import com.cobblemon.mod.common.battles.BattleRegistry;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.cobblepulse.tools.CobblePulseTools;
import fr.cobblepulse.tools.ModContent;
import fr.cobblepulse.tools.ToolsConfig;
import fr.cobblepulse.tools.net.ToolsNet;
import fr.cobblepulse.tools.perm.Perms;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Baguette d'edition : ouverture de l'editeur sur un Pokemon (clic droit sur l'entite, ou /cptools editer pour l'equipe
 * et le PC d'un joueur), puis application des modifications envoyees par l'ecran client. A chaque action : permission
 * re-verifiee, Pokemon retrouve par son UUID (jamais par une reference gardee du client), refus en plein combat.
 */
public final class WandService {
    /** Pokemon en cours d'edition par un membre du staff. */
    public record Session(int token, UUID pokemonUuid, UUID entityUuid, ResourceKey<Level> dimension, UUID ownerUuid, String ownerName) {}

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_USE = new ConcurrentHashMap<>();
    private static final AtomicInteger TOKENS = new AtomicInteger();
    private static MinecraftServer server;

    private WandService() {}

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            SESSIONS.clear();
        });
        // avant l'interaction du Pokemon (sinon clic droit = menu d'interaction de Cobblemon)
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player.isSpectator()) return InteractionResult.PASS;
            ItemStack st = player.getItemInHand(hand);
            if (ModContent.EDITOR_WAND == null || !st.is(ModContent.EDITOR_WAND)) return InteractionResult.PASS;
            if (!(entity instanceof PokemonEntity pe)) return InteractionResult.PASS;
            if (world.isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            long t = world.getGameTime();
            Long last = LAST_USE.put(sp.getUUID(), t);
            if (last != null && t - last >= 0 && t - last < 5) return InteractionResult.SUCCESS;
            openForEntity(sp, pe);
            return InteractionResult.SUCCESS;
        });
    }

    public static MinecraftServer server() {
        return server;
    }

    public static void forget(UUID admin) {
        SESSIONS.remove(admin);
        LAST_USE.remove(admin);
    }

    static void msg(ServerPlayer p, String text, ChatFormatting color) {
        p.sendSystemMessage(Component.literal("[Tools] ").withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(text).withStyle(color)));
    }

    public static void openForEntity(ServerPlayer admin, PokemonEntity pe) {
        if (!Perms.isAdmin(admin)) {
            msg(admin, "Cette baguette est réservée au staff (" + ToolsConfig.get().admin_permission + ").", ChatFormatting.RED);
            return;
        }
        Pokemon pk = pe.getPokemon();
        UUID owner = pk.getOwnerUUID();
        open(admin, pk, pe, owner, ownerName(owner));
    }

    /** Ouverture depuis l'equipe ou le PC d'un joueur en ligne (commande). */
    public static void openForStore(ServerPlayer admin, ServerPlayer owner, Pokemon pk) {
        open(admin, pk, pk.getEntity(), owner.getUUID(), owner.getGameProfile().getName());
    }

    private static String ownerName(UUID owner) {
        if (owner == null || server == null) return "";
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) return p.getGameProfile().getName();
        try {
            return server.getProfileCache() == null ? owner.toString()
                    : server.getProfileCache().get(owner).map(com.mojang.authlib.GameProfile::getName).orElse(owner.toString());
        } catch (Throwable t) {
            return owner.toString();
        }
    }

    private static void open(ServerPlayer admin, Pokemon pk, Entity entity, UUID owner, String ownerName) {
        if (!ToolsNet.hasClient(admin)) {
            msg(admin, "L'éditeur demande le mod CobblePulse Tools côté client.", ChatFormatting.RED);
            return;
        }
        Session s = new Session(TOKENS.incrementAndGet(), pk.getUuid(), entity == null ? null : entity.getUUID(),
                entity == null ? null : entity.level().dimension(), owner, ownerName == null ? "" : ownerName);
        SESSIONS.put(admin.getUUID(), s);
        ToolsNet.send(admin, "editor_open", describe(s, pk));
    }

    public static Session session(UUID admin) {
        return SESSIONS.get(admin);
    }

    /** Fiche du Pokemon + contexte (proprietaire, emplacement, combat). */
    static JsonObject describe(Session s, Pokemon pk) {
        JsonObject o = PokemonData.toJson(pk);
        o.addProperty("token", s.token());
        o.addProperty("kind", kind(pk, s));
        o.addProperty("owner", s.ownerName());
        o.addProperty("battling", inBattle(pk, s));
        return o;
    }

    static String kind(Pokemon pk, Session s) {
        if (s.ownerUuid() == null) return "wild";
        if (pk.getEntity() != null) return "out";
        try {
            StoreCoordinates<?> c = pk.getStoreCoordinates().get();
            if (c != null && c.getStore() instanceof PCStore) return "pc";
        } catch (Throwable ignored) {
        }
        return "party";
    }

    static boolean inBattle(Pokemon pk, Session s) {
        PokemonEntity e = pk.getEntity();
        if (e != null && e.isBattling()) return true;
        return s.ownerUuid() != null && BattleRegistry.getBattleByParticipatingPlayerId(s.ownerUuid()) != null;
    }

    /** Retrouve le Pokemon edite : equipe / PC du proprietaire en ligne, sinon l'entite dans le monde. */
    public static Pokemon resolve(Session s) {
        if (server == null || s == null) return null;
        if (s.ownerUuid() != null) {
            ServerPlayer owner = server.getPlayerList().getPlayer(s.ownerUuid());
            if (owner != null) {
                try {
                    for (Pokemon p : Cobblemon.INSTANCE.getStorage().getParty(owner)) if (p.getUuid().equals(s.pokemonUuid())) return p;
                    for (Pokemon p : Cobblemon.INSTANCE.getStorage().getPC(owner)) if (p.getUuid().equals(s.pokemonUuid())) return p;
                } catch (Throwable t) {
                    CobblePulseTools.LOG.warn("[CobblePulse-Tools] Stockage de {} illisible : {}", s.ownerName(), t.toString());
                }
            }
        }
        if (s.entityUuid() != null && s.dimension() != null) {
            ServerLevel lvl = server.getLevel(s.dimension());
            Entity e = lvl == null ? null : lvl.getEntity(s.entityUuid());
            if (e instanceof PokemonEntity pe && pe.isAlive() && pe.getPokemon().getUuid().equals(s.pokemonUuid())) return pe.getPokemon();
        }
        return null;
    }

    public static void handleAction(ServerPlayer admin, String action, JsonObject o) {
        Session s = SESSIONS.get(admin.getUUID());
        if (s == null) return;
        int token = o.has("token") ? o.get("token").getAsInt() : -1;
        if (token != s.token()) {
            reply(admin, s, null, false, java.util.List.of(), java.util.List.of("Éditeur périmé : rouvre-le."));
            return;
        }
        if ("close".equals(action)) {
            SESSIONS.remove(admin.getUUID());
            return;
        }
        if (!Perms.isAdmin(admin)) {
            SESSIONS.remove(admin.getUUID());
            msg(admin, "Permission retirée : édition annulée.", ChatFormatting.RED);
            return;
        }
        Pokemon pk = resolve(s);
        if (pk == null) {
            reply(admin, s, null, false, java.util.List.of(), java.util.List.of("Pokémon introuvable (capturé, relâché, échangé, disparu ou propriétaire déconnecté)."));
            return;
        }
        switch (action) {
            case "refresh" -> reply(admin, s, pk, true, java.util.List.of(), java.util.List.of());
            case "heal" -> {
                pk.heal();
                log(admin, s, pk, "soigné");
                reply(admin, s, pk, true, java.util.List.of("soigné"), java.util.List.of());
            }
            case "apply" -> {
                if (inBattle(pk, s) && !ToolsConfig.get().allow_edit_in_battle) {
                    reply(admin, s, pk, false, java.util.List.of(), java.util.List.of("Ce Pokémon est en combat : réessaie après le combat."));
                    return;
                }
                JsonObject data = o.has("data") && o.get("data").isJsonObject() ? o.getAsJsonObject("data") : new JsonObject();
                PokemonData.Result r = PokemonData.apply(pk, data);
                PokemonEntity e = pk.getEntity();
                if (e == null && s.entityUuid() != null && s.dimension() != null && server.getLevel(s.dimension()) != null
                        && server.getLevel(s.dimension()).getEntity(s.entityUuid()) instanceof PokemonEntity pe) e = pe;
                if (e != null && !r.changed.isEmpty()) {
                    try {
                        // attributs, taille, vie et IA recalcules pour la nouvelle espece / forme
                        e.setPokemon(pk);
                    } catch (Throwable t) {
                        CobblePulseTools.LOG.warn("[CobblePulse-Tools] Rafraîchissement de l'entité impossible : {}", t.toString());
                    }
                }
                if (!r.changed.isEmpty()) log(admin, s, pk, String.join(", ", r.changed));
                reply(admin, s, pk, r.errors.isEmpty(), r.changed, r.errors);
            }
            default -> CobblePulseTools.LOG.debug("[CobblePulse-Tools] Action inconnue {}", action);
        }
    }

    private static void log(ServerPlayer admin, Session s, Pokemon pk, String what) {
        if (!ToolsConfig.get().log_edits) return;
        CobblePulseTools.LOG.info("[CobblePulse-Tools] {} a modifié {} ({}{}) : {}", admin.getGameProfile().getName(), pk.getSpecies().getName(),
                s.ownerUuid() == null ? "sauvage" : "de " + s.ownerName(), " " + pk.getUuid(), what);
    }

    private static void reply(ServerPlayer admin, Session s, Pokemon pk, boolean ok, java.util.List<String> changed, java.util.List<String> errors) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", ok);
        JsonArray c = new JsonArray();
        changed.forEach(c::add);
        JsonArray e = new JsonArray();
        errors.forEach(e::add);
        o.add("changed", c);
        o.add("errors", e);
        if (pk != null) o.add("data", describe(s, pk));
        ToolsNet.send(admin, "editor_result", o);
    }
}
