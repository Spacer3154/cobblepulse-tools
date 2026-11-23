package fr.cobblepulse.tools.client;

import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokeball.PokeBalls;
import com.cobblemon.mod.common.api.pokemon.Natures;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.moves.Learnset;
import com.cobblemon.mod.common.api.types.tera.TeraType;
import com.cobblemon.mod.common.api.types.tera.TeraTypes;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokeball.PokeBall;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Editeur de Pokemon de la baguette (staff). Quatre onglets : General (espece, forme, niveau, chromatique, genre,
 * nature, menthe, talent, surnom), Statistiques (IV / EV), Capacites (4 emplacements + liste des capacites
 * apprenables ou de toutes), Objets et divers (objet tenu, Ball, Tera, bonheur, Dynamax, Gigamax, echangeable).
 * Le serveur verifie tout et renvoie la fiche a jour apres chaque application.
 */
public final class EditorScreen extends Screen {
    static final String[] STATS = {"hp", "atk", "def", "spa", "spd", "spe"};
    static final String[] STAT_NAMES = {"PV", "Attaque", "Défense", "Atq. Spé.", "Déf. Spé.", "Vitesse"};
    private static final String[] TABS = {"Général", "Statistiques", "Capacités", "Objets & divers"};

    /** Champ a choix : valeur envoyee + libelle affiche. */
    static final class Choice {
        final List<String> values = new ArrayList<>();
        final List<Component> names = new ArrayList<>();
        int index;
        Button button;

        void clear() {
            values.clear();
            names.clear();
            index = 0;
        }

        void add(String value, Component name) {
            values.add(value);
            names.add(name);
        }

        String value() {
            return values.isEmpty() ? "" : values.get(Mth.clamp(index, 0, values.size() - 1));
        }

        Component name() {
            return names.isEmpty() ? Component.literal("—") : names.get(Mth.clamp(index, 0, names.size() - 1));
        }

        void select(String v) {
            int i = values.indexOf(v);
            index = Math.max(0, i);
        }

        void step(int d) {
            if (values.isEmpty()) return;
            index = Math.floorMod(index + d, values.size());
        }
    }

    // ---- donnees ----
    private JsonObject data;
    private int token;
    private Species species;
    private final Choice form = new Choice(), gender = new Choice(), nature = new Choice(), minted = new Choice(),
            ability = new Choice(), ball = new Choice(), tera = new Choice();
    private boolean shiny, gmax, tradeable;
    private String level = "", nickname = "", held = "", friendship = "", dmax = "";
    private final String[] ivs = new String[6], evs = new String[6], moves = new String[4];
    private int tab;
    private final List<String> status = new ArrayList<>();
    private int statusColor = UiKit.MUTED;
    boolean lastOk;
    int results;

    // ---- liste des especes ----
    private final List<Species> allSpecies = new ArrayList<>();
    private final List<Species> filtered = new ArrayList<>();
    private EditBox search;
    private String searchText = "";
    private double listScroll;

    // ---- capacites ----
    private int moveSlot;
    private boolean allMoves;
    private EditBox moveSearch;
    private String moveSearchText = "";
    private final List<String[]> moveList = new ArrayList<>(); // {id, etiquette}
    private double moveScroll;
    private final EditBox[] moveBoxes = new EditBox[4];

    // ---- mise en page ----
    private int px, py, pw, ph, cx, cy, cw, ch;
    private ModelWidget model;
    private EditBox heldBox;

    public EditorScreen(JsonObject o) {
        super(Component.literal("Éditeur de Pokémon"));
        for (Species s : PokemonSpecies.getSpecies()) {
            if (!s.getImplemented() && "cobblemon".equals(s.getResourceIdentifier().getNamespace())) continue;
            allSpecies.add(s);
        }
        allSpecies.sort(Comparator.comparingInt(Species::getNationalPokedexNumber).thenComparing(s -> s.getResourceIdentifier().toString()));
        List<Nature> natures = new ArrayList<>(Natures.all());
        natures.sort(Comparator.comparing(n -> Component.translatable(n.getDisplayName()).getString()));
        minted.add("", Component.literal("Aucune"));
        for (Nature n : natures) {
            nature.add(n.getName().toString(), Component.translatable(n.getDisplayName()));
            minted.add(n.getName().toString(), Component.translatable(n.getDisplayName()));
        }
        for (PokeBall b : PokeBalls.all()) ball.add(b.getName().toString(), b.item().getDescription());
        for (TeraType t : TeraTypes.INSTANCE) tera.add(t.getId().toString(), t.getDisplayName());
        load(o);
    }

    static String speciesName(Species s) {
        if (s == null) return "?";
        String t = s.getTranslatedName().getString();
        return t.startsWith("cobblemon.") || t.contains(".species.") ? s.getName() : t;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o == null ? null : o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    /** Remplit l'editeur avec la fiche envoyee par le serveur. */
    private void load(JsonObject o) {
        data = o;
        token = o.has("token") ? o.get("token").getAsInt() : 0;
        ResourceLocation id = ResourceLocation.tryParse(str(o, "species"));
        Species s = id == null ? null : PokemonSpecies.getByIdentifier(id);
        selectSpecies(s, false);
        form.select(str(o, "form"));
        refreshForForm(false);
        gender.select(str(o, "gender"));
        ability.select(str(o, "ability"));
        if (!ability.value().equalsIgnoreCase(str(o, "ability"))) addCurrentAbility(str(o, "ability"));
        level = str(o, "level");
        shiny = o.has("shiny") && o.get("shiny").getAsBoolean();
        nature.select(str(o, "nature"));
        minted.select(str(o, "minted"));
        nickname = str(o, "nickname");
        JsonObject iv = o.getAsJsonObject("ivs");
        JsonObject ev = o.getAsJsonObject("evs");
        for (int i = 0; i < 6; i++) {
            ivs[i] = iv == null ? "0" : str(iv, STATS[i]);
            evs[i] = ev == null ? "0" : str(ev, STATS[i]);
        }
        JsonArray mv = o.getAsJsonArray("moves");
        for (int i = 0; i < 4; i++) moves[i] = mv != null && i < mv.size() ? mv.get(i).getAsString() : "";
        held = str(o, "held");
        ball.select(str(o, "ball"));
        tera.select(str(o, "tera"));
        friendship = str(o, "friendship");
        dmax = str(o, "dmax");
        gmax = o.has("gmax") && o.get("gmax").getAsBoolean();
        tradeable = !o.has("tradeable") || o.get("tradeable").getAsBoolean();
        if (minecraft != null) rebuild();
    }

    private void addCurrentAbility(String name) {
        if (name.isEmpty()) return;
        AbilityTemplate t = Abilities.get(name);
        Component label = t == null ? Component.literal(name) : Component.translatable(t.getDisplayName());
        ability.add(name, label.copy().append(" (forcé)"));
        ability.index = ability.values.size() - 1;
    }

    // ------------------------------------------------------------------ espece, forme, talents

    private void selectSpecies(Species s, boolean userChange) {
        String prevGender = gender.value();
        String prevAbility = ability.value();
        species = s;
        form.clear();
        form.add("", Component.literal("Forme de base"));
        if (s != null) {
            for (FormData d : s.getForms()) {
                String n = d.getName() == null ? d.formOnlyShowdownId() : d.getName();
                form.add(d.formOnlyShowdownId(), Component.literal(n));
            }
        }
        gender.clear();
        float ratio = s == null ? -1 : s.getMaleRatio();
        if (ratio < 0) gender.add("genderless", Component.literal("Asexué"));
        if (ratio > 0) gender.add("male", Component.literal("♂ Mâle"));
        if (ratio >= 0 && ratio < 1) gender.add("female", Component.literal("♀ Femelle"));
        if (userChange) gender.select(prevGender);
        refreshForForm(userChange);
        if (userChange) {
            int i = ability.values.indexOf(prevAbility);
            ability.index = Math.max(0, i);
        }
    }

    private FormData currentForm() {
        if (species == null) return null;
        String f = form.value();
        if (!f.isEmpty()) for (FormData d : species.getForms()) if (d.formOnlyShowdownId().equals(f)) return d;
        return species.getStandardForm();
    }

    /** Talents et capacites apprenables de la forme choisie. */
    private void refreshForForm(boolean keepAbility) {
        String prev = ability.value();
        ability.clear();
        FormData f = currentForm();
        if (f != null) {
            Set<String> seen = new HashSet<>();
            for (PotentialAbility a : f.getAbilities()) {
                String n = a.getTemplate().getName();
                if (!seen.add(n)) continue;
                boolean hidden = a.getPriority() == Priority.LOW;
                ability.add(n, Component.translatable(a.getTemplate().getDisplayName()).append(hidden ? " (caché)" : ""));
            }
        }
        if (keepAbility) {
            int i = ability.values.indexOf(prev);
            if (i >= 0) ability.index = i;
        }
        rebuildMoveList();
        rebuildModel();
    }

    private void rebuildModel() {
        model = null;
        if (species == null || minecraft == null || tab != 0 || !showPreview()) return;
        Set<String> aspects = new HashSet<>();
        if (shiny) aspects.add("shiny");
        String g = gender.value();
        if (g.equals("male") || g.equals("female")) aspects.add(g);
        FormData f = currentForm();
        if (f != null) aspects.addAll(f.getAspects());
        try {
            model = new ModelWidget(px + 158, cy + 4, 110, 110, new RenderablePokemon(species, aspects, ItemStack.EMPTY), 1.6f, 325f, -10.0, false, false, 15);
        } catch (Throwable t) {
            model = null;
        }
    }

    private boolean showPreview() {
        return pw >= 520;
    }

    private void refilter() {
        filtered.clear();
        String q = UiKit.norm(searchText.trim());
        for (Species s : allSpecies) {
            if (q.isEmpty() || UiKit.norm(speciesName(s)).contains(q) || s.getResourceIdentifier().toString().contains(q)
                    || (q.chars().allMatch(Character::isDigit) && String.valueOf(s.getNationalPokedexNumber()).equals(q.replaceFirst("^0+(?=.)", "")))) {
                filtered.add(s);
            }
        }
        listScroll = 0;
    }

    // ------------------------------------------------------------------ capacites

    private static String moveId(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void rebuildMoveList() {
        moveList.clear();
        String q = UiKit.norm(moveSearchText.trim());
        Map<String, String> entries = new LinkedHashMap<>();
        if (allMoves) {
            List<MoveTemplate> all = new ArrayList<>(Moves.all());
            all.sort(Comparator.comparing(m -> m.getDisplayName().getString()));
            for (MoveTemplate m : all) entries.putIfAbsent(m.getName(), "");
        } else {
            FormData f = currentForm();
            Learnset ls = f == null ? null : f.getMoves();
            if (ls != null) {
                List<Map.Entry<Integer, List<MoveTemplate>>> lv = new ArrayList<>(ls.getLevelUpMoves().entrySet());
                lv.sort(Map.Entry.comparingByKey());
                for (var e : lv) for (MoveTemplate m : e.getValue()) entries.putIfAbsent(m.getName(), "Nv. " + e.getKey());
                for (MoveTemplate m : ls.getEggMoves()) entries.putIfAbsent(m.getName(), "Œuf");
                for (MoveTemplate m : ls.getTmMoves()) entries.putIfAbsent(m.getName(), "CT");
                for (MoveTemplate m : ls.getTutorMoves()) entries.putIfAbsent(m.getName(), "Tuteur");
                for (MoveTemplate m : ls.getEvolutionMoves()) entries.putIfAbsent(m.getName(), "Évolution");
                for (MoveTemplate m : ls.getLegacyMoves()) entries.putIfAbsent(m.getName(), "Ancienne");
                for (MoveTemplate m : ls.getSpecialMoves()) entries.putIfAbsent(m.getName(), "Spéciale");
            }
        }
        for (var e : entries.entrySet()) {
            MoveTemplate m = Moves.getByName(e.getKey());
            String label = m == null ? e.getKey() : m.getDisplayName().getString();
            if (!q.isEmpty() && !UiKit.norm(label).contains(q) && !e.getKey().contains(q)) continue;
            moveList.add(new String[]{e.getKey(), label, e.getValue()});
        }
        moveScroll = 0;
    }

    private void putMove(String id) {
        for (int i = 0; i < 4; i++) {
            if (i != moveSlot && moveId(moves[i]).equals(id)) {
                setStatus(List.of("Déjà en emplacement " + (i + 1) + "."), UiKit.WARN);
                return;
            }
        }
        moves[moveSlot] = id;
        if (moveBoxes[moveSlot] != null) moveBoxes[moveSlot].setValue(id);
        moveSlot = (moveSlot + 1) % 4;
    }

    // ------------------------------------------------------------------ mise en page

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        for (Choice c : new Choice[]{form, gender, nature, minted, ability, ball, tera}) c.button = null;
        for (int i = 0; i < 4; i++) moveBoxes[i] = null;
        heldBox = null;
        pw = Math.min(width - 16, 580);
        ph = Math.min(height - 12, 290);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cx = px + 8;
        cy = py + 44;
        cw = pw - 16;
        ch = ph - 44 - 52;
        int tx = px + 8;
        for (int i = 0; i < TABS.length; i++) {
            final int t = i;
            int w = font.width(TABS[i]) + 16;
            Button b = addRenderableWidget(Button.builder(Component.literal(TABS[i]), x -> setTab(t)).bounds(tx, py + 20, w, 18).build());
            b.active = tab != i;
            tx += w + 3;
        }
        switch (tab) {
            case 0 -> buildGeneral();
            case 1 -> buildStats();
            case 2 -> buildMoves();
            default -> buildMisc();
        }
        // Showdown : import (remplace le Pokemon edite) et export dans le presse-papiers
        int sx = px + pw - 8;
        sx -= 66;
        addRenderableWidget(Button.builder(Component.literal("Export SD"), b -> exportShowdown()).bounds(sx, py + 20, 66, 18).build());
        sx -= 70;
        addRenderableWidget(Button.builder(Component.literal("Import SD"), b -> minecraft.setScreen(new ShowdownScreen(this,
                "Importer un set Showdown (remplace ce Pokémon)", "Colle un set Showdown…", this::importShowdown))).bounds(sx, py + 20, 66, 18).build());
        int by = py + ph - 24;
        addRenderableWidget(Button.builder(Component.literal("Soigner"), b -> action("heal")).bounds(px + 8, by, 70, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Recharger"), b -> action("refresh")).bounds(px + 82, by, 74, 18).build());
        if (!str(data, "owner_uuid").isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("Équipe"), b -> {
                JsonObject o = new JsonObject();
                o.addProperty("owner", str(data, "owner_uuid"));
                ToolsClient.send("party_open", o);
            }).bounds(px + 160, by, 64, 18).build());
        }
        addRenderableWidget(Button.builder(Component.literal("§aAppliquer"), b -> apply()).bounds(px + pw - 8 - 170, by, 84, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(px + pw - 8 - 82, by, 82, 18).build());
        rebuildModel();
    }

    private EditBox field(int x, int y, int w, String value, String hint, java.util.function.Consumer<String> responder) {
        EditBox b = addRenderableWidget(new EditBox(font, x, y + 1, w, 16, Component.empty()));
        b.setMaxLength(64);
        b.setValue(value == null ? "" : value);
        b.setHint(Component.literal(hint).withColor(UiKit.MUTED & 0xFFFFFF));
        b.setResponder(responder);
        return b;
    }

    private Button cycle(Choice c, int x, int y, int w, Runnable after) {
        c.button = addRenderableWidget(Button.builder(c.name(), b -> {
            c.step(hasShiftDown() ? -1 : 1);
            if (after != null) after.run();
            refreshLabels();
        }).bounds(x, y, w, 18).build());
        return c.button;
    }

    private Button toggle(int x, int y, int w, java.util.function.BooleanSupplier get, Runnable flip, String on, String off) {
        Button[] ref = new Button[1];
        ref[0] = addRenderableWidget(Button.builder(Component.literal(get.getAsBoolean() ? on : off), b -> {
            flip.run();
            b.setMessage(Component.literal(get.getAsBoolean() ? on : off));
        }).bounds(x, y, w, 18).build());
        return ref[0];
    }

    private void refreshLabels() {
        for (Choice c : new Choice[]{form, gender, nature, minted, ability, ball, tera}) if (c.button != null) c.button.setMessage(c.name());
    }

    private int fieldX() {
        return showPreview() ? px + 284 + 62 : px + 156 + 62;
    }

    private void buildGeneral() {
        search = addRenderableWidget(new EditBox(font, cx, cy, 140, 16, Component.literal("Espèce")));
        search.setHint(Component.literal("Espèce ou n°…").withColor(UiKit.MUTED & 0xFFFFFF));
        search.setValue(searchText);
        search.setResponder(v -> {
            searchText = v;
            refilter();
        });
        refilter();
        int fx = fieldX();
        int fw = px + pw - 8 - fx;
        int y = cy;
        cycle(form, fx, y, fw, () -> {
            refreshForForm(true);
            rebuildModel();
        });
        y += 20;
        field(fx, y, fw, level, "1 à " + str(data, "max_level"), v -> level = v);
        y += 20;
        toggle(fx, y, fw, () -> shiny, () -> {
            shiny = !shiny;
            rebuildModel();
        }, "§e★ Chromatique", "☆ Normal");
        y += 20;
        cycle(gender, fx, y, fw, this::rebuildModel);
        y += 20;
        cycle(nature, fx, y, fw, null);
        y += 20;
        cycle(minted, fx, y, fw, null);
        y += 20;
        cycle(ability, fx, y, fw, null);
        y += 20;
        field(fx, y, fw, nickname, "aucun", v -> nickname = v);
    }

    private void buildStats() {
        int x0 = cx + 90;
        for (int i = 0; i < 6; i++) {
            final int k = i;
            int y = cy + 16 + i * 20;
            field(x0, y, 40, ivs[i], "0-31", v -> ivs[k] = v);
            field(x0 + 56, y, 44, evs[i], "0-252", v -> evs[k] = v);
        }
        int bx = cx + 330;
        int bw = Math.min(150, px + pw - 8 - bx);
        addRenderableWidget(Button.builder(Component.literal("IV : tous à 31"), b -> setAllIv("31")).bounds(bx, cy + 16, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("IV : tous à 0"), b -> setAllIv("0")).bounds(bx, cy + 36, bw, 18).build());
        addRenderableWidget(Button.builder(Component.literal("EV : tout à 0"), b -> {
            for (int i = 0; i < 6; i++) evs[i] = "0";
            rebuild();
        }).bounds(bx, cy + 56, bw, 18).build());
    }

    private void setAllIv(String v) {
        for (int i = 0; i < 6; i++) ivs[i] = v;
        rebuild();
    }

    private void buildMoves() {
        int lx = cx;
        for (int i = 0; i < 4; i++) {
            final int k = i;
            int y = cy + 14 + i * 24;
            addRenderableWidget(Button.builder(Component.literal(moveSlot == i ? "§e▶" + (i + 1) : String.valueOf(i + 1)), b -> {
                moveSlot = k;
                rebuild();
            }).bounds(lx, y, 22, 18).build());
            moveBoxes[i] = field(lx + 26, y, 120, moves[i], "vide", v -> moves[k] = v);
        }
        int rx = cx + 280;
        int rw = px + pw - 8 - rx;
        moveSearch = addRenderableWidget(new EditBox(font, rx, cy, rw - 84, 16, Component.literal("Capacité")));
        moveSearch.setHint(Component.literal("Rechercher…").withColor(UiKit.MUTED & 0xFFFFFF));
        moveSearch.setValue(moveSearchText);
        moveSearch.setResponder(v -> {
            moveSearchText = v;
            rebuildMoveList();
        });
        addRenderableWidget(Button.builder(Component.literal(allMoves ? "Toutes" : "Apprenables"), b -> {
            allMoves = !allMoves;
            rebuildMoveList();
            b.setMessage(Component.literal(allMoves ? "Toutes" : "Apprenables"));
        }).bounds(rx + rw - 80, cy - 1, 80, 18).build());
        rebuildMoveList();
    }

    private void buildMisc() {
        int fx = cx + 110;
        int fw = Math.min(220, px + pw - 8 - fx);
        int y = cy;
        heldBox = field(fx, y, fw, held, "aucun (ex. cobblemon:leftovers)", v -> held = v);
        y += 20;
        cycle(ball, fx, y, fw, null);
        y += 20;
        cycle(tera, fx, y, fw, null);
        y += 20;
        field(fx, y, fw, friendship, "0 à 255", v -> friendship = v);
        y += 20;
        field(fx, y, fw, dmax, "0 à 10", v -> dmax = v);
        y += 20;
        toggle(fx, y, fw, () -> gmax, () -> gmax = !gmax, "§cFacteur Gigamax", "Sans Gigamax");
        y += 20;
        toggle(fx, y, fw, () -> tradeable, () -> tradeable = !tradeable, "Échangeable", "§cNon échangeable");
    }

    public void setTab(int t) {
        tab = Mth.clamp(t, 0, TABS.length - 1);
        rebuild();
    }

    // ------------------------------------------------------------------ actions

    private void action(String a) {
        JsonObject o = new JsonObject();
        o.addProperty("token", token);
        ToolsClient.send(a, o);
    }

    /** Fiche complete envoyee au serveur (il ne touche que ce qui change). */
    JsonObject collect() {
        JsonObject d = new JsonObject();
        if (species != null) d.addProperty("species", species.getResourceIdentifier().toString());
        d.addProperty("form", form.value());
        putInt(d, "level", level);
        d.addProperty("shiny", shiny);
        if (!gender.value().isEmpty()) d.addProperty("gender", gender.value());
        d.addProperty("nature", nature.value());
        d.addProperty("minted", minted.value());
        if (!ability.value().isEmpty()) d.addProperty("ability", ability.value());
        JsonObject iv = new JsonObject();
        JsonObject ev = new JsonObject();
        for (int i = 0; i < 6; i++) {
            putInt(iv, STATS[i], ivs[i]);
            putInt(ev, STATS[i], evs[i]);
        }
        d.add("ivs", iv);
        d.add("evs", ev);
        JsonArray mv = new JsonArray();
        for (String m : moves) mv.add(moveId(m));
        d.add("moves", mv);
        d.addProperty("held", held.trim());
        d.addProperty("ball", ball.value());
        d.addProperty("nickname", nickname.trim());
        d.addProperty("tera", tera.value());
        putInt(d, "friendship", friendship);
        putInt(d, "dmax", dmax);
        d.addProperty("gmax", gmax);
        d.addProperty("tradeable", tradeable);
        return d;
    }

    /** Nombre invalide : envoye tel quel (le serveur le refuse avec un message clair). */
    private static void putInt(JsonObject o, String k, String v) {
        try {
            o.addProperty(k, Integer.parseInt(v.trim()));
        } catch (Exception e) {
            o.addProperty(k, -1);
        }
    }

    String exportText() {
        return str(data, "showdown");
    }

    void exportShowdown() {
        String t = exportText();
        if (t.isEmpty()) {
            setStatus(List.of("Export indisponible."), UiKit.ERROR);
            return;
        }
        minecraft.keyboardHandler.setClipboard(t);
        setStatus(List.of("Set Showdown copié dans le presse-papiers."), UiKit.OK);
    }

    void importShowdown(String text) {
        JsonObject o = new JsonObject();
        o.addProperty("token", token);
        o.addProperty("text", text);
        setStatus(List.of("Import…"), UiKit.MUTED);
        ToolsClient.send("import", o);
    }

    public void apply() {
        JsonObject o = new JsonObject();
        o.addProperty("token", token);
        o.add("data", collect());
        setStatus(List.of("Envoi…"), UiKit.MUTED);
        ToolsClient.send("apply", o);
    }

    void onResult(JsonObject o) {
        results++;
        lastOk = o.has("ok") && o.get("ok").getAsBoolean();
        List<String> lines = new ArrayList<>();
        JsonArray changed = o.getAsJsonArray("changed");
        JsonArray errors = o.getAsJsonArray("errors");
        if (changed != null && !changed.isEmpty()) {
            List<String> c = new ArrayList<>();
            for (JsonElement e : changed) c.add(e.getAsString());
            lines.add("✔ " + String.join(", ", c));
        }
        if (errors != null) for (JsonElement e : errors) lines.add("✖ " + e.getAsString());
        if (lines.isEmpty()) lines.add("Aucun changement.");
        if (o.has("data") && o.get("data").isJsonObject()) load(o.getAsJsonObject("data"));
        setStatus(lines, errors != null && !errors.isEmpty() ? (changed != null && !changed.isEmpty() ? UiKit.WARN : UiKit.ERROR) : UiKit.OK);
    }

    private void setStatus(List<String> lines, int color) {
        status.clear();
        status.addAll(lines);
        statusColor = color;
    }

    @Override
    public void onClose() {
        action("close");
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        UiKit.panel(g, px, py, pw, ph, UiKit.PANEL, UiKit.ACCENT);
        String kind = switch (str(data, "kind")) {
            case "wild" -> "sauvage";
            case "party" -> "équipe de " + str(data, "owner");
            case "pc" -> "PC de " + str(data, "owner");
            case "out" -> "sorti par " + str(data, "owner");
            default -> "";
        };
        String nick = str(data, "nickname");
        String title = "Éditeur · " + (nick.isEmpty() ? speciesName(species) : nick + " (" + speciesName(species) + ")") + " · " + kind;
        g.drawString(font, font.plainSubstrByWidth(title, pw - 16), px + 8, py + 7, UiKit.GOLD, false);
        if (data != null && data.has("battling") && data.get("battling").getAsBoolean()) {
            String w = "En combat : modifications refusées";
            g.drawString(font, w, px + pw - 8 - font.width(w), py + 7, UiKit.WARN, false);
        }
        switch (tab) {
            case 0 -> renderGeneral(g, mx, my);
            case 1 -> renderStats(g);
            case 2 -> renderMoves(g, mx, my);
            default -> renderMisc(g);
        }
        int sy = py + ph - 48;
        g.fill(px + 8, sy - 3, px + pw - 8, sy - 2, UiKit.BORDER);
        for (int i = 0; i < Math.min(2, status.size()); i++) {
            String s = status.get(i);
            if (i == 1 && status.size() > 2) s += "  (+" + (status.size() - 2) + ")";
            g.drawString(font, font.plainSubstrByWidth(s, pw - 16), px + 8, sy + i * 10, i == 0 ? statusColor : UiKit.ERROR, false);
        }
        super.render(g, mx, my, pt);
        if (tab == 0) renderSpeciesList(g, mx, my);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    private void renderGeneral(GuiGraphics g, int mx, int my) {
        int fx = fieldX() - 62;
        String[] labels = {"Forme", "Niveau", "Chromatique", "Genre", "Nature", "Menthe", "Talent", "Surnom"};
        for (int i = 0; i < labels.length; i++) g.drawString(font, labels[i], fx, cy + 5 + i * 20, UiKit.MUTED, false);
        if (showPreview()) {
            UiKit.panel(g, px + 154, cy, 118, 118, UiKit.CARD, UiKit.BORDER);
            if (model != null) {
                try {
                    model.render(g, mx, my, 0);
                } catch (Throwable t) {
                    model = null;
                }
            }
            g.drawCenteredString(font, speciesName(species), px + 213, cy + 124, UiKit.GOLD);
            g.drawCenteredString(font, "PV " + str(data, "hp") + " / " + str(data, "max_hp"), px + 213, cy + 136, UiKit.MUTED);
            String ot = str(data, "ot");
            if (!ot.isEmpty()) g.drawCenteredString(font, font.plainSubstrByWidth("DO : " + ot, 116), px + 213, cy + 148, UiKit.MUTED);
        }
    }

    private void renderSpeciesList(GuiGraphics g, int mx, int my) {
        int lx = cx, ly = cy + 20, lw = 140, lh = ch - 20;
        UiKit.panel(g, lx, ly, lw, lh, UiKit.CARD, UiKit.BORDER);
        int rows = lh / 18;
        int start = (int) Mth.clamp(listScroll, 0, Math.max(0, filtered.size() - rows));
        g.enableScissor(lx + 1, ly + 1, lx + lw - 1, ly + lh - 1);
        for (int i = 0; i < rows && start + i < filtered.size(); i++) {
            Species s = filtered.get(start + i);
            int ry = ly + 1 + i * 18;
            boolean hov = UiKit.in(mx, my, lx, ry, lw, 18);
            if (s == species || hov) g.fill(lx + 1, ry, lx + lw - 1, ry + 18, s == species ? UiKit.CARD_ON : 0x40FFFFFF);
            try {
                g.renderItem(PokemonItem.Companion.from(s, new String[0]), lx + 3, ry + 1);
            } catch (Throwable ignored) {
            }
            g.drawString(font, font.plainSubstrByWidth(speciesName(s), lw - 26), lx + 22, ry + 5, s == species ? UiKit.GOLD : UiKit.TEXT, false);
        }
        g.disableScissor();
    }

    private void renderStats(GuiGraphics g) {
        int x0 = cx + 90;
        g.drawString(font, "IV", x0 + 2, cy + 3, UiKit.ACCENT, false);
        g.drawString(font, "EV", x0 + 58, cy + 3, UiKit.ACCENT, false);
        int total = 0;
        for (int i = 0; i < 6; i++) {
            int y = cy + 16 + i * 20;
            g.drawString(font, STAT_NAMES[i], cx, y + 5, UiKit.MUTED, false);
            int ev = parse(evs[i]);
            total += Math.max(0, ev);
            int bx = x0 + 108, bw = 110;
            g.fill(bx, y + 6, bx + bw, y + 11, UiKit.CARD);
            g.fill(bx, y + 6, bx + (int) (bw * Mth.clamp(ev / 252.0, 0, 1)), y + 11, ev > 252 || ev < 0 ? UiKit.ERROR : UiKit.ACCENT);
            int iv = parse(ivs[i]);
            if (iv < 0 || iv > 31) g.drawString(font, "!", x0 - 8, y + 5, UiKit.ERROR, false);
        }
        g.drawString(font, "Total EV : " + total + " / 510", x0, cy + 16 + 6 * 20 + 4, total > 510 ? UiKit.ERROR : UiKit.TEXT, false);
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return -1;
        }
    }

    private void renderMoves(GuiGraphics g, int mx, int my) {
        g.drawString(font, "Emplacement " + (moveSlot + 1) + " · clic dans la liste", cx, cy + 2, UiKit.MUTED, false);
        for (int i = 0; i < 4; i++) {
            int y = cy + 14 + i * 24;
            String id = moveId(moves[i]);
            MoveTemplate m = id.isEmpty() ? null : Moves.getByName(id);
            String label = id.isEmpty() ? "—" : m == null ? "inconnue" : m.getDisplayName().getString() + " · " + m.getPp() + " PP";
            g.drawString(font, font.plainSubstrByWidth(label, 120), cx + 152, y + 5, id.isEmpty() ? UiKit.MUTED : m == null ? UiKit.ERROR : UiKit.TEXT, false);
        }
        int rx = cx + 280, ry = cy + 20, rw = px + pw - 8 - rx, rh = ch - 20;
        UiKit.panel(g, rx, ry, rw, rh, UiKit.CARD, UiKit.BORDER);
        int rows = rh / 12;
        int start = (int) Mth.clamp(moveScroll, 0, Math.max(0, moveList.size() - rows));
        g.enableScissor(rx + 1, ry + 1, rx + rw - 1, ry + rh - 1);
        for (int i = 0; i < rows && start + i < moveList.size(); i++) {
            String[] e = moveList.get(start + i);
            int y = ry + 2 + i * 12;
            if (UiKit.in(mx, my, rx, y - 1, rw, 12)) g.fill(rx + 1, y - 1, rx + rw - 1, y + 11, 0x40FFFFFF);
            boolean used = false;
            for (String mv : moves) used |= moveId(mv).equals(e[0]);
            g.drawString(font, font.plainSubstrByWidth(e[1], rw - 70), rx + 4, y + 1, used ? UiKit.GOLD : UiKit.TEXT, false);
            g.drawString(font, e[2], rx + rw - 4 - font.width(e[2]), y + 1, UiKit.MUTED, false);
        }
        g.disableScissor();
        if (moveList.isEmpty()) g.drawString(font, "Aucune capacité", rx + 4, ry + 4, UiKit.MUTED, false);
    }

    private void renderMisc(GuiGraphics g) {
        String[] labels = {"Objet tenu", "Poké Ball", "Type Téra", "Bonheur", "Niveau Dynamax", "Gigamax", "Échange"};
        for (int i = 0; i < labels.length; i++) g.drawString(font, labels[i], cx, cy + 5 + i * 20, UiKit.MUTED, false);
        int fx = cx + 110;
        int fw = Math.min(220, px + pw - 8 - fx);
        String h = held.trim();
        if (!h.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(h.contains(":") ? h : "cobblemon:" + h);
            boolean ok = rl != null && BuiltInRegistries.ITEM.containsKey(rl);
            if (ok) g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(rl)), fx + fw + 6, cy);
            else g.drawString(font, "objet inconnu", fx + fw + 6, cy + 5, UiKit.ERROR, false);
        }
        int iy = cy + 7 * 20 + 6;
        g.drawString(font, font.plainSubstrByWidth("UUID : " + str(data, "uuid"), pw - 16), cx, iy, UiKit.MUTED, false);
        String ot = str(data, "ot");
        g.drawString(font, "Dresseur d'origine : " + (ot.isEmpty() ? "—" : ot), cx, iy + 11, UiKit.MUTED, false);
    }

    // ------------------------------------------------------------------ souris

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (tab == 0) {
            int lx = cx, ly = cy + 20, lw = 140, lh = ch - 20;
            if (UiKit.in(mx, my, lx, ly, lw, lh)) {
                int i = (int) Mth.clamp(listScroll, 0, Math.max(0, filtered.size() - lh / 18)) + (int) ((my - ly - 1) / 18);
                if (i >= 0 && i < filtered.size()) {
                    selectSpecies(filtered.get(i), true);
                    rebuild();
                    return true;
                }
            }
        } else if (tab == 2) {
            int rx = cx + 280, ry = cy + 20, rw = px + pw - 8 - rx, rh = ch - 20;
            if (UiKit.in(mx, my, rx, ry, rw, rh)) {
                int i = (int) Mth.clamp(moveScroll, 0, Math.max(0, moveList.size() - rh / 12)) + (int) ((my - ry - 1) / 12);
                if (i >= 0 && i < moveList.size()) {
                    putMove(moveList.get(i)[0]);
                    rebuild();
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab == 0 && UiKit.in(mx, my, cx, cy + 20, 140, ch - 20)) {
            listScroll = Math.max(0, listScroll - sy * 3);
            return true;
        }
        if (tab == 2 && UiKit.in(mx, my, cx + 280, cy + 20, px + pw - 8 - (cx + 280), ch - 20)) {
            moveScroll = Math.max(0, moveScroll - sy * 3);
            return true;
        }
        // molette sur un bouton a choix : valeur precedente / suivante
        for (Choice c : new Choice[]{form, gender, nature, minted, ability, ball, tera}) {
            if (c.button != null && c.button.visible && c.button.isMouseOver(mx, my)) {
                c.step(sy > 0 ? -1 : 1);
                if (c == form) refreshForForm(true);
                if (c == form || c == gender) rebuildModel();
                refreshLabels();
                return true;
            }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    // ------------------------------------------------------------------ auto-test

    String kind() {
        return str(data, "kind");
    }

    String speciesId() {
        return species == null ? "" : species.getResourceIdentifier().toString();
    }

    void chooseSpecies(String id) {
        Species s = PokemonSpecies.getByIdentifier(ResourceLocation.parse(id));
        if (s != null) {
            selectSpecies(s, true);
            rebuild();
        }
    }

    void setLevelText(String v) {
        level = v;
        rebuild();
    }

    void setShiny(boolean v) {
        shiny = v;
        rebuild();
    }

    List<String> status() {
        return status;
    }

    int learnableCount() {
        return moveList.size();
    }
}
