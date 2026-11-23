package fr.cobblepulse.tools.wand;

import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Import / export au format Showdown (Pokemon Showdown, Pokepaste). Un set importe devient une fiche JSON de
 * {@link PokemonData#apply} : memes verifications que l'editeur. Comme Showdown : niveau absent = 100, EV absents = 0,
 * IV absents = 31, pas de ligne « Shiny: Yes » = non chromatique, pas de surnom = surnom retire.
 */
public final class ShowdownSets {
    private static final String[] SD_STATS = {"hp", "atk", "def", "spa", "spd", "spe"};
    private static final String[] SD_LABELS = {"HP", "Atk", "Def", "SpA", "SpD", "Spe"};

    private ShowdownSets() {}

    /** Identifiant Showdown : minuscules, lettres et chiffres seulement. */
    public static String toId(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** Un set analyse : fiche JSON pour PokemonData.apply (vide si l'espece est introuvable) + erreurs de lecture. */
    public record Parsed(JsonObject data, List<String> errors, String label) {
        public boolean ok() {
            return data != null && data.has("species");
        }
    }

    /** Decoupe une equipe collee (sets separes par des lignes vides, titres « === … === » ignores). */
    public static List<String> splitTeam(String text) {
        List<String> sets = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String raw : (text == null ? "" : text).replace("\r", "").split("\n")) {
            String l = raw.strip();
            if (l.startsWith("===")) continue;
            if (l.isEmpty()) {
                if (!cur.toString().isBlank()) sets.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(l).append('\n');
        }
        if (!cur.toString().isBlank()) sets.add(cur.toString());
        return sets;
    }

    /** Espece + forme depuis un nom Showdown (« Raichu-Alola », « Mr. Mime », « Urshifu-Rapid-Strike »…). */
    static Object[] findSpecies(String name) {
        String id = toId(name);
        if (id.isEmpty()) return null;
        Species byName = null;
        for (Species s : PokemonSpecies.getSpecies()) {
            for (FormData f : s.getForms()) {
                if (toId(f.showdownId()).equals(id)) return new Object[]{s, f.formOnlyShowdownId()};
            }
            if (toId(s.showdownId()).equals(id) || toId(s.getStandardForm().showdownId()).equals(id)) return new Object[]{s, ""};
            if (byName == null && toId(s.getName()).equals(id)) byName = s;
        }
        if (byName != null) return new Object[]{byName, ""};
        // « Espece-Forme » dont la forme n'a pas d'identifiant Showdown propre
        int dash = name.indexOf('-');
        if (dash > 0) {
            Object[] base = findSpecies(name.substring(0, dash));
            if (base != null) {
                Species s = (Species) base[0];
                String formId = toId(name.substring(dash + 1));
                for (FormData f : s.getForms()) {
                    if (toId(f.formOnlyShowdownId()).equals(formId) || toId(f.getName()).equals(formId)) return new Object[]{s, f.formOnlyShowdownId()};
                }
            }
        }
        return null;
    }

    static String findItem(String name) {
        String id = toId(name);
        if (id.isEmpty()) return null;
        String other = null;
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            if (!toId(rl.getPath()).equals(id)) continue;
            if (rl.getNamespace().equals("cobblemon")) return rl.toString();
            if (other == null) other = rl.toString();
        }
        if (other != null) return other;
        for (var e : BuiltInRegistries.ITEM.entrySet()) {
            if (toId(e.getValue().getDescription().getString()).equals(id)) return e.getKey().location().toString();
        }
        return null;
    }

    private static int statIndex(String label) {
        String l = toId(label);
        for (int i = 0; i < 6; i++) if (toId(SD_LABELS[i]).equals(l) || SD_STATS[i].equals(l)) return i;
        return switch (l) {
            case "spatk", "specialattack" -> 3;
            case "spdef", "specialdefense", "specialdefence" -> 4;
            case "speed" -> 5;
            case "attack" -> 1;
            case "defense", "defence" -> 2;
            default -> -1;
        };
    }

    private static void spread(String body, JsonObject out, List<String> errors, String what) {
        for (String part : body.split("/")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] t = p.split("\\s+", 2);
            int i = t.length == 2 ? statIndex(t[1]) : -1;
            try {
                if (i < 0) throw new NumberFormatException();
                out.addProperty(SD_STATS[i], Integer.parseInt(t[0]));
            } catch (NumberFormatException e) {
                errors.add(what + " illisible : « " + p + " »");
            }
        }
    }

    /** Lit un set Showdown. */
    public static Parsed parse(String text) {
        List<String> errors = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (String l : (text == null ? "" : text).replace("\r", "").split("\n")) if (!l.isBlank() && !l.strip().startsWith("===")) lines.add(l.strip());
        if (lines.isEmpty()) {
            errors.add("Set vide.");
            return new Parsed(new JsonObject(), errors, "");
        }
        JsonObject d = new JsonObject();
        // 1re ligne : Surnom (Espece) (M) @ Objet
        String first = lines.get(0);
        String item = null;
        int at = first.lastIndexOf(" @ ");
        if (at < 0 && first.contains("@")) at = first.lastIndexOf('@');
        if (at >= 0) {
            item = first.substring(at + (first.startsWith(" @ ", at) ? 3 : 1)).trim();
            first = first.substring(0, at).trim();
        }
        String gender = null;
        if (first.endsWith("(M)") || first.endsWith("(F)")) {
            gender = first.endsWith("(M)") ? "male" : "female";
            first = first.substring(0, first.length() - 3).trim();
        }
        String nickname = "";
        String speciesName = first;
        int open = first.lastIndexOf('(');
        if (open > 0 && first.endsWith(")")) {
            speciesName = first.substring(open + 1, first.length() - 1).trim();
            nickname = first.substring(0, open).trim();
        }
        Object[] sp = findSpecies(speciesName);
        if (sp == null) {
            errors.add("Espèce inconnue : « " + speciesName + " »");
            return new Parsed(new JsonObject(), errors, speciesName);
        }
        Species species = (Species) sp[0];
        d.addProperty("species", species.getResourceIdentifier().toString());
        d.addProperty("form", (String) sp[1]);
        d.addProperty("nickname", nickname);
        if (gender != null) d.addProperty("gender", gender);
        if (item != null) {
            String id = findItem(item);
            if (id == null) errors.add("Objet inconnu : « " + item + " »");
            else d.addProperty("held", id);
        } else d.addProperty("held", "");
        int level = 100;
        boolean shiny = false;
        JsonObject evs = new JsonObject();
        JsonObject ivs = new JsonObject();
        for (String s : SD_STATS) {
            evs.addProperty(s, 0);
            ivs.addProperty(s, 31);
        }
        JsonArray moves = new JsonArray();
        for (int i = 1; i < lines.size(); i++) {
            String l = lines.get(i);
            String low = l.toLowerCase(Locale.ROOT);
            if (l.startsWith("-") || l.startsWith("~")) {
                String m = l.substring(1).trim().replaceAll("\\[.*?]", "").trim();
                String mid = toId(m);
                MoveTemplate tpl = Moves.getByName(mid);
                if (tpl == null) {
                    for (MoveTemplate t : Moves.all()) if (toId(t.getDisplayName().getString()).equals(mid)) tpl = t;
                }
                if (tpl == null) errors.add("Capacité inconnue : « " + m + " »");
                else if (moves.size() < 4) moves.add(tpl.getName());
            } else if (low.startsWith("ability:")) {
                d.addProperty("ability", toId(l.substring(8)));
            } else if (low.startsWith("level:")) {
                try {
                    level = Integer.parseInt(l.substring(6).trim());
                } catch (NumberFormatException e) {
                    errors.add("Niveau illisible : « " + l + " »");
                }
            } else if (low.startsWith("shiny:")) {
                shiny = low.substring(6).trim().startsWith("y");
            } else if (low.startsWith("happiness:") || low.startsWith("friendship:")) {
                try {
                    d.addProperty("friendship", Integer.parseInt(l.substring(l.indexOf(':') + 1).trim()));
                } catch (NumberFormatException e) {
                    errors.add("Bonheur illisible : « " + l + " »");
                }
            } else if (low.startsWith("dynamax level:")) {
                try {
                    d.addProperty("dmax", Integer.parseInt(l.substring(l.indexOf(':') + 1).trim()));
                } catch (NumberFormatException e) {
                    errors.add("Niveau Dynamax illisible : « " + l + " »");
                }
            } else if (low.startsWith("gigantamax:")) {
                d.addProperty("gmax", low.substring(11).trim().startsWith("y"));
            } else if (low.startsWith("tera type:")) {
                String t = toId(l.substring(10));
                TeraType tt = TeraTypes.get(t);
                if (tt == null) errors.add("Type Téra inconnu : « " + l.substring(10).trim() + " »");
                else d.addProperty("tera", tt.getId().toString());
            } else if (low.startsWith("evs:")) {
                spread(l.substring(4), evs, errors, "EV");
            } else if (low.startsWith("ivs:")) {
                spread(l.substring(4), ivs, errors, "IV");
            } else if (low.endsWith(" nature")) {
                String n = toId(l.substring(0, l.length() - 7));
                Nature nat = Natures.getNature(n);
                if (nat == null) errors.add("Nature inconnue : « " + l + " »");
                else d.addProperty("nature", nat.getName().toString());
            } else if (low.startsWith("ball:") || low.startsWith("pokeball:") || low.startsWith("poké ball:")) {
                String b = toId(l.substring(l.indexOf(':') + 1));
                d.addProperty("ball", "cobblemon:" + (b.endsWith("ball") ? b.substring(0, b.length() - 4) + "_ball" : b));
            } else if (low.startsWith("gender:")) {
                String g = toId(l.substring(7));
                d.addProperty("gender", g.startsWith("m") ? "male" : g.startsWith("f") ? "female" : "genderless");
            } else if (!low.startsWith("hidden power:") && !low.startsWith("pokemon")) {
                errors.add("Ligne ignorée : « " + l + " »");
            }
        }
        d.addProperty("level", level);
        d.addProperty("shiny", shiny);
        d.add("evs", evs);
        d.add("ivs", ivs);
        if (!moves.isEmpty()) d.add("moves", moves);
        return new Parsed(d, errors, species.getName());
    }

    private static String en(String key, String fallback) {
        try {
            String v = Language.getInstance().getOrDefault(key, fallback);
            return v == null || v.equals(key) ? fallback : v;
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static String pretty(String path) {
        StringBuilder b = new StringBuilder();
        for (String part : path.split("[_\\-]")) {
            if (part.isEmpty()) continue;
            if (!b.isEmpty()) b.append(' ');
            b.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return b.toString();
    }

    /** Export au format Showdown (noms anglais, comme Showdown les attend). */
    public static String export(Pokemon pk) {
        StringBuilder b = new StringBuilder();
        String species = pk.getSpecies().getName();
        if (pk.getForm() != null && pk.getForm() != pk.getSpecies().getStandardForm() && pk.getForm().getName() != null) {
            species += "-" + pk.getForm().getName();
        }
        String nick = pk.getNickname() == null ? "" : pk.getNickname().getString();
        b.append(nick.isEmpty() || nick.equals(species) ? species : nick + " (" + species + ")");
        if (pk.getGender() == Gender.MALE) b.append(" (M)");
        else if (pk.getGender() == Gender.FEMALE) b.append(" (F)");
        ItemStack held = pk.heldItem();
        if (held != null && !held.isEmpty()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(held.getItem());
            b.append(" @ ").append(en(held.getItem().getDescriptionId(), pretty(id.getPath())));
        }
        b.append('\n');
        AbilityTemplate ab = pk.getAbility().getTemplate();
        b.append("Ability: ").append(en(ab.getDisplayName(), pretty(ab.getName()))).append('\n');
        if (pk.getLevel() != 100) b.append("Level: ").append(pk.getLevel()).append('\n');
        if (pk.getShiny()) b.append("Shiny: Yes\n");
        if (pk.getFriendship() != 255) b.append("Happiness: ").append(pk.getFriendship()).append('\n');
        if (pk.getDmaxLevel() != 10) b.append("Dynamax Level: ").append(pk.getDmaxLevel()).append('\n');
        if (pk.getGmaxFactor()) b.append("Gigantamax: Yes\n");
        b.append("Tera Type: ").append(pretty(pk.getTeraType().getId().getPath())).append('\n');
        List<String> ev = new ArrayList<>();
        List<String> iv = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            int e = pk.getEvs().getOrDefault(PokemonData.stat(SD_STATS[i]));
            int v = pk.getIvs().getOrDefault(PokemonData.stat(SD_STATS[i]));
            if (e > 0) ev.add(e + " " + SD_LABELS[i]);
            if (v != 31) iv.add(v + " " + SD_LABELS[i]);
        }
        if (!ev.isEmpty()) b.append("EVs: ").append(String.join(" / ", ev)).append('\n');
        b.append(pretty(pk.getNature().getName().getPath())).append(" Nature\n");
        if (!iv.isEmpty()) b.append("IVs: ").append(String.join(" / ", iv)).append('\n');
        for (Move m : pk.getMoveSet().getMoves()) {
            String shown = m.getTemplate().getDisplayName().getString();
            b.append("- ").append(shown.isEmpty() || shown.startsWith("cobblemon.") ? pretty(m.getName()) : shown).append('\n');
        }
        return b.toString().trim();
    }
}
