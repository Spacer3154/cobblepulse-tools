package fr.cobblepulse.tools.wand;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Fiche d'un Pokemon pour l'editeur (Pokemon -> JSON) et application des modifications (JSON -> Pokemon). Seuls les
 * champs qui changent sont touches ; chaque valeur est re-verifiee ici, jamais crue sur parole du client.
 */
public final class PokemonData {
    public static final String[] STAT_IDS = {"hp", "atk", "def", "spa", "spd", "spe"};
    public static final int EV_TOTAL_MAX = 510;

    private PokemonData() {}

    public static Stat stat(String id) {
        return switch (id) {
            case "hp" -> Stats.HP;
            case "atk" -> Stats.ATTACK;
            case "def" -> Stats.DEFENCE;
            case "spa" -> Stats.SPECIAL_ATTACK;
            case "spd" -> Stats.SPECIAL_DEFENCE;
            default -> Stats.SPEED;
        };
    }

    /** Niveau maximal accepte : celui de Cobblemon, ou le niveau actuel s'il est plus haut (Boss de Utils). */
    public static int maxLevel(Pokemon pk) {
        int max = 100;
        try {
            max = Cobblemon.INSTANCE.getConfig().getMaxPokemonLevel();
        } catch (Throwable ignored) {
        }
        return Math.max(max, pk == null ? 0 : pk.getLevel());
    }

    /** Identifiant de forme : "" pour la forme de base, sinon l'identifiant Showdown de la forme. */
    public static String formId(Pokemon pk) {
        FormData f = pk.getForm();
        if (f == null || f == pk.getSpecies().getStandardForm()) return "";
        return f.formOnlyShowdownId();
    }

    public static String gender(Gender g) {
        return g == null ? "genderless" : g.name().toLowerCase(Locale.ROOT);
    }

    public static JsonObject toJson(Pokemon pk) {
        JsonObject o = new JsonObject();
        o.addProperty("uuid", pk.getUuid().toString());
        o.addProperty("species", pk.getSpecies().getResourceIdentifier().toString());
        o.addProperty("form", formId(pk));
        o.addProperty("level", pk.getLevel());
        o.addProperty("max_level", maxLevel(pk));
        o.addProperty("shiny", pk.getShiny());
        o.addProperty("gender", gender(pk.getGender()));
        o.addProperty("nature", pk.getNature().getName().toString());
        o.addProperty("minted", pk.getMintedNature() == null ? "" : pk.getMintedNature().getName().toString());
        o.addProperty("ability", pk.getAbility().getName());
        JsonObject ivs = new JsonObject();
        JsonObject evs = new JsonObject();
        for (String s : STAT_IDS) {
            ivs.addProperty(s, pk.getIvs().getOrDefault(stat(s)));
            evs.addProperty(s, pk.getEvs().getOrDefault(stat(s)));
        }
        o.add("ivs", ivs);
        o.add("evs", evs);
        JsonArray moves = new JsonArray();
        for (int i = 0; i < 4; i++) {
            Move m = pk.getMoveSet().get(i);
            moves.add(m == null ? "" : m.getName());
        }
        o.add("moves", moves);
        ItemStack held = pk.heldItem();
        o.addProperty("held", held == null || held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString());
        o.addProperty("ball", pk.getCaughtBall().getName().toString());
        o.addProperty("nickname", pk.getNickname() == null ? "" : pk.getNickname().getString());
        o.addProperty("tera", pk.getTeraType().getId().toString());
        o.addProperty("friendship", pk.getFriendship());
        o.addProperty("dmax", pk.getDmaxLevel());
        o.addProperty("gmax", pk.getGmaxFactor());
        o.addProperty("tradeable", pk.getTradeable());
        o.addProperty("hp", pk.getCurrentHealth());
        o.addProperty("max_hp", pk.getMaxHealth());
        o.addProperty("ot", pk.getOriginalTrainerName() == null ? "" : pk.getOriginalTrainerName());
        return o;
    }

    // ---- lecture defensive du JSON client ----

    private static boolean has(JsonObject o, String k) {
        return o != null && o.has(k) && !o.get(k).isJsonNull();
    }

    private static String str(JsonObject o, String k) {
        try {
            return has(o, k) ? o.get(k).getAsString().trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer num(JsonObject o, String k) {
        try {
            return has(o, k) ? o.get(k).getAsInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean bool(JsonObject o, String k) {
        try {
            return has(o, k) ? o.get(k).getAsBoolean() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static ResourceLocation rl(String raw, String defNs) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return ResourceLocation.tryParse(v.contains(":") ? v : defNs + ":" + v);
    }

    public static Species findSpecies(String raw) {
        ResourceLocation id = rl(raw, "cobblemon");
        Species s = id == null ? null : PokemonSpecies.getByIdentifier(id);
        if (s == null && raw != null && !raw.isBlank()) {
            String n = raw.trim().toLowerCase(Locale.ROOT);
            if (n.contains(":")) n = n.substring(n.indexOf(':') + 1);
            if (n.matches("[a-z0-9_./-]+")) {
                try {
                    s = PokemonSpecies.getByName(n);
                } catch (Exception ignored) {
                }
            }
        }
        return s;
    }

    /** Resultat d'une application : champs modifies, erreurs (champ ignore), avertissements. */
    public static final class Result {
        public final List<String> changed = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
        public boolean visualChange;
    }

    /** Applique la fiche {@code o} au Pokemon. */
    public static Result apply(Pokemon pk, JsonObject o) {
        Result r = new Result();
        // Espece puis forme (elles conditionnent talent, genre et capacites)
        String sp = str(o, "species");
        if (sp != null && !sp.isEmpty() && !sp.equals(pk.getSpecies().getResourceIdentifier().toString())) {
            Species s = findSpecies(sp);
            if (s == null) r.errors.add("Espèce inconnue : " + sp);
            else {
                pk.setSpecies(s);
                r.changed.add("espèce → " + s.getName());
                r.visualChange = true;
            }
        }
        String form = str(o, "form");
        if (form != null && !form.equals(formId(pk))) {
            if (applyForm(pk, form, r)) r.visualChange = true;
        }
        Integer lv = num(o, "level");
        if (lv != null && lv != pk.getLevel()) {
            int max = maxLevel(pk);
            if (lv < 1 || lv > max) r.errors.add("Niveau " + lv + " hors limites (1 à " + max + ")");
            else {
                pk.setLevel(lv);
                r.changed.add("niveau → " + lv);
            }
        }
        Boolean shiny = bool(o, "shiny");
        if (shiny != null && shiny != pk.getShiny()) {
            pk.setShiny(shiny);
            r.changed.add(shiny ? "chromatique" : "non chromatique");
            r.visualChange = true;
        }
        String g = str(o, "gender");
        if (g != null && !g.isEmpty() && !g.equals(gender(pk.getGender()))) applyGender(pk, g, r);
        String nat = str(o, "nature");
        if (nat != null && !nat.isEmpty() && !nat.equals(pk.getNature().getName().toString())) {
            Nature n = nature(nat);
            if (n == null) r.errors.add("Nature inconnue : " + nat);
            else {
                pk.setNature(n);
                r.changed.add("nature → " + n.getName().getPath());
            }
        }
        String minted = str(o, "minted");
        String curMinted = pk.getMintedNature() == null ? "" : pk.getMintedNature().getName().toString();
        if (minted != null && !minted.equals(curMinted)) {
            if (minted.isEmpty()) {
                pk.setMintedNature(null);
                r.changed.add("menthe retirée");
            } else {
                Nature n = nature(minted);
                if (n == null) r.errors.add("Nature (menthe) inconnue : " + minted);
                else {
                    pk.setMintedNature(n);
                    r.changed.add("menthe → " + n.getName().getPath());
                }
            }
        }
        String ab = str(o, "ability");
        if (ab != null && !ab.isEmpty() && !ab.equalsIgnoreCase(pk.getAbility().getName())) applyAbility(pk, ab, r);
        applyStats(pk, o, r);
        applyMoves(pk, o, r);
        String held = str(o, "held");
        if (held != null) {
            ItemStack cur = pk.heldItem();
            String curId = cur == null || cur.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(cur.getItem()).toString();
            ResourceLocation id = rl(held, "cobblemon");
            String want = id == null ? "" : id.toString();
            if (!want.equals(curId)) {
                if (want.isEmpty()) {
                    pk.swapHeldItem(ItemStack.EMPTY, false, false);
                    r.changed.add("objet tenu retiré");
                } else {
                    Item it = BuiltInRegistries.ITEM.containsKey(id) ? BuiltInRegistries.ITEM.get(id) : Items.AIR;
                    if (it == Items.AIR) r.errors.add("Objet inconnu : " + held);
                    else {
                        pk.swapHeldItem(new ItemStack(it), false, false);
                        r.changed.add("objet tenu → " + want);
                    }
                }
            }
        }
        String ball = str(o, "ball");
        if (ball != null && !ball.isEmpty() && !ball.equals(pk.getCaughtBall().getName().toString())) {
            ResourceLocation id = rl(ball, "cobblemon");
            PokeBall b = id == null ? null : PokeBalls.getPokeBall(id);
            if (b == null) r.errors.add("Poké Ball inconnue : " + ball);
            else {
                pk.setCaughtBall(b);
                r.changed.add("Ball → " + id.getPath());
                r.visualChange = true;
            }
        }
        String nick = str(o, "nickname");
        String curNick = pk.getNickname() == null ? "" : pk.getNickname().getString();
        if (nick != null && !nick.equals(curNick)) {
            if (nick.length() > 32) r.errors.add("Surnom trop long (32 caractères max)");
            else {
                pk.setNickname(nick.isEmpty() ? null : Component.literal(nick));
                r.changed.add(nick.isEmpty() ? "surnom retiré" : "surnom → " + nick);
            }
        }
        String tera = str(o, "tera");
        if (tera != null && !tera.isEmpty() && !tera.equals(pk.getTeraType().getId().toString())) {
            ResourceLocation id = rl(tera, "cobblemon");
            TeraType t = id == null ? null : TeraTypes.get(id);
            if (t == null) t = TeraTypes.get(tera.contains(":") ? tera.substring(tera.indexOf(':') + 1) : tera);
            if (t == null) r.errors.add("Type Téracristal inconnu : " + tera);
            else {
                pk.setTeraType(t);
                r.changed.add("Téra → " + t.getId().getPath());
            }
        }
        Integer fr = num(o, "friendship");
        if (fr != null && fr != pk.getFriendship()) {
            if (fr < 0 || fr > 255) r.errors.add("Bonheur " + fr + " hors limites (0 à 255)");
            else {
                pk.setFriendship(fr, true);
                if (pk.getFriendship() != fr) r.errors.add("Bonheur " + fr + " refusé par Cobblemon");
                else r.changed.add("bonheur → " + fr);
            }
        }
        Integer dmax = num(o, "dmax");
        if (dmax != null && dmax != pk.getDmaxLevel()) {
            if (dmax < 0 || dmax > 10) r.errors.add("Niveau Dynamax " + dmax + " hors limites (0 à 10)");
            else {
                pk.setDmaxLevel(dmax);
                if (pk.getDmaxLevel() != dmax) r.errors.add("Niveau Dynamax " + dmax + " refusé par Cobblemon");
                else r.changed.add("Dynamax → " + dmax);
            }
        }
        Boolean gmax = bool(o, "gmax");
        if (gmax != null && gmax != pk.getGmaxFactor()) {
            pk.setGmaxFactor(gmax);
            if (pk.getGmaxFactor() != gmax) r.errors.add("Facteur Gigamax impossible pour " + pk.getSpecies().getName());
            else r.changed.add(gmax ? "facteur Gigamax" : "sans facteur Gigamax");
        }
        Boolean trade = bool(o, "tradeable");
        if (trade != null && trade != pk.getTradeable()) {
            pk.setTradeable(trade);
            r.changed.add(trade ? "échangeable" : "non échangeable");
        }
        return r;
    }

    private static Nature nature(String raw) {
        ResourceLocation id = rl(raw, "cobblemon");
        Nature n = id == null ? null : Natures.getNature(id);
        if (n == null) n = Natures.getNature(raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw);
        return n;
    }

    private static boolean applyForm(Pokemon pk, String form, Result r) {
        Species s = pk.getSpecies();
        FormData target = null;
        if (form.isEmpty()) target = s.getStandardForm();
        else for (FormData f : s.getForms()) if (f.formOnlyShowdownId().equals(form)) target = f;
        if (target == null) {
            r.errors.add("Forme inconnue pour " + s.getName() + " : " + form);
            return false;
        }
        try {
            // aspects forces de l'ancienne forme retires, ceux de la nouvelle ajoutes
            Set<String> forced = new HashSet<>(pk.getForcedAspects());
            for (FormData f : s.getForms()) forced.removeAll(f.getAspects());
            forced.addAll(target.getAspects());
            pk.setForcedAspects(forced);
            pk.updateAspects();
            if (pk.getForm() != target) pk.setForm(target);
            r.changed.add("forme → " + (form.isEmpty() ? "base" : form));
            return true;
        } catch (Throwable t) {
            r.errors.add("Forme " + form + " impossible : " + t.getMessage());
            return false;
        }
    }

    private static void applyGender(Pokemon pk, String g, Result r) {
        Gender want;
        try {
            want = Gender.valueOf(g.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            r.errors.add("Genre inconnu : " + g);
            return;
        }
        float ratio = pk.getSpecies().getMaleRatio();
        boolean ok = switch (want) {
            case GENDERLESS -> ratio < 0;
            case MALE -> ratio > 0;
            case FEMALE -> ratio >= 0 && ratio < 1;
        };
        if (!ok) {
            r.errors.add("Genre " + g + " impossible pour " + pk.getSpecies().getName());
            return;
        }
        pk.setGender(want);
        r.changed.add("genre → " + g);
        r.visualChange = true;
    }

    private static void applyAbility(Pokemon pk, String raw, Result r) {
        String key = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        AbilityTemplate tpl = Abilities.get(key);
        if (tpl == null) {
            r.errors.add("Talent inconnu : " + raw);
            return;
        }
        Priority prio = null;
        for (PotentialAbility a : pk.getForm().getAbilities()) {
            if (a.getTemplate() == tpl) {
                prio = a.getPriority();
                break;
            }
        }
        // talent hors de la liste de l'espece : force (garde a l'evolution)
        pk.updateAbility(tpl.create(prio == null, prio == null ? Priority.LOWEST : prio));
        r.changed.add("talent → " + tpl.getName() + (prio == null ? " (forcé)" : ""));
    }

    private static void applyStats(Pokemon pk, JsonObject o, Result r) {
        JsonObject ivs = has(o, "ivs") && o.get("ivs").isJsonObject() ? o.getAsJsonObject("ivs") : null;
        JsonObject evs = has(o, "evs") && o.get("evs").isJsonObject() ? o.getAsJsonObject("evs") : null;
        if (ivs != null) {
            List<String> ch = new ArrayList<>();
            for (String s : STAT_IDS) {
                Integer v = num(ivs, s);
                if (v == null || v == pk.getIvs().getOrDefault(stat(s))) continue;
                if (v < 0 || v > 31) {
                    r.errors.add("IV " + s + " = " + v + " hors limites (0 à 31)");
                    continue;
                }
                pk.setIV(stat(s), v);
                ch.add(s + "=" + v);
            }
            if (!ch.isEmpty()) r.changed.add("IV " + String.join(" ", ch));
        }
        if (evs != null) {
            int[] want = new int[6];
            int total = 0;
            boolean diff = false;
            for (int i = 0; i < 6; i++) {
                Integer v = num(evs, STAT_IDS[i]);
                want[i] = v == null ? pk.getEvs().getOrDefault(stat(STAT_IDS[i])) : v;
                if (want[i] < 0 || want[i] > 252) {
                    r.errors.add("EV " + STAT_IDS[i] + " = " + want[i] + " hors limites (0 à 252) : EV inchangés");
                    return;
                }
                total += want[i];
                if (want[i] != pk.getEvs().getOrDefault(stat(STAT_IDS[i]))) diff = true;
            }
            if (!diff) return;
            if (total > EV_TOTAL_MAX) {
                r.errors.add("Total d'EV " + total + " > " + EV_TOTAL_MAX + " : EV inchangés");
                return;
            }
            List<String> ch = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                if (want[i] == pk.getEvs().getOrDefault(stat(STAT_IDS[i]))) continue;
                pk.setEV(stat(STAT_IDS[i]), want[i]);
                ch.add(STAT_IDS[i] + "=" + want[i]);
            }
            r.changed.add("EV " + String.join(" ", ch));
        }
    }

    private static void applyMoves(Pokemon pk, JsonObject o, Result r) {
        if (!has(o, "moves") || !o.get("moves").isJsonArray()) return;
        JsonArray arr = o.getAsJsonArray("moves");
        String[] want = new String[4];
        for (int i = 0; i < 4; i++) {
            String v = "";
            if (i < arr.size()) {
                JsonElement e = arr.get(i);
                v = e == null || e.isJsonNull() ? "" : e.getAsString();
            }
            want[i] = v.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        }
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            if (!want[i].isEmpty() && !seen.add(want[i])) {
                r.errors.add("Capacité en double : " + want[i] + " (capacités inchangées)");
                return;
            }
        }
        boolean any = false;
        for (String w : want) any |= !w.isEmpty();
        if (!any) {
            r.errors.add("Au moins une capacité est nécessaire (capacités inchangées)");
            return;
        }
        List<String> ch = new ArrayList<>();
        // capacites vides tassees vers la fin, comme dans les jeux
        List<String> packed = new ArrayList<>();
        for (String w : want) if (!w.isEmpty()) packed.add(w);
        for (int i = 0; i < 4; i++) {
            String w = i < packed.size() ? packed.get(i) : "";
            Move cur = pk.getMoveSet().get(i);
            String curName = cur == null ? "" : cur.getName();
            if (Objects.equals(w, curName)) continue;
            if (w.isEmpty()) {
                pk.getMoveSet().setMove(i, null);
                ch.add((i + 1) + ":—");
                continue;
            }
            MoveTemplate tpl = Moves.getByName(w);
            if (tpl == null) {
                r.errors.add("Capacité inconnue : " + w);
                continue;
            }
            pk.getMoveSet().setMove(i, tpl.create());
            ch.add((i + 1) + ":" + w);
        }
        if (!ch.isEmpty()) r.changed.add("capacités " + String.join(" ", ch));
    }
}
