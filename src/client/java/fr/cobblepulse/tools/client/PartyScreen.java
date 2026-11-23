package fr.cobblepulse.tools.client;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Equipe d'un joueur vue par la baguette (clic droit sur un joueur, ou dans le vide pour la sienne) : six emplacements,
 * Editer pour un Pokemon, Importer (Showdown) pour un emplacement vide, Importer une equipe pour remplir les libres.
 */
public final class PartyScreen extends Screen {
    private JsonObject data;
    private final ModelWidget[] models = new ModelWidget[6];
    private final List<String> status = new ArrayList<>();
    private int statusColor = UiKit.MUTED;
    private int px, py, pw, ph, cw, chh;
    int results;

    public PartyScreen(JsonObject o) {
        super(Component.literal("Équipe"));
        data = o;
    }

    void update(JsonObject o) {
        data = o;
        results++;
        status.clear();
        JsonArray c = o.getAsJsonArray("changed");
        JsonArray e = o.getAsJsonArray("errors");
        if (c != null && !c.isEmpty()) {
            List<String> l = new ArrayList<>();
            for (JsonElement x : c) l.add(x.getAsString());
            status.add("✔ " + String.join(", ", l));
        }
        if (e != null) for (JsonElement x : e) status.add("✖ " + x.getAsString());
        statusColor = e != null && !e.isEmpty() ? (c != null && !c.isEmpty() ? UiKit.WARN : UiKit.ERROR) : UiKit.OK;
        if (minecraft != null) rebuildWidgets();
    }

    private String ownerUuid() {
        return data.get("owner_uuid").getAsString();
    }

    JsonObject slot(int i) {
        JsonArray s = data.getAsJsonArray("slots");
        return s != null && i < s.size() && s.get(i).isJsonObject() ? s.get(i).getAsJsonObject() : null;
    }

    private void send(String action, int slot, String text) {
        JsonObject o = new JsonObject();
        o.addProperty("owner", ownerUuid());
        o.addProperty("slot", slot);
        if (text != null) o.addProperty("text", text);
        ToolsClient.send(action, o);
    }

    void edit(int slot) {
        send("party_edit", slot, null);
    }

    void importText(int slot, String text) {
        send("party_import", slot, text);
    }

    @Override
    protected void init() {
        pw = Math.min(width - 16, 520);
        ph = Math.min(height - 12, 270);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        cw = (pw - 16 - 2 * 6) / 3;
        chh = (ph - 26 - 54 - 6) / 2;
        for (int i = 0; i < 6; i++) {
            final int k = i;
            int x = px + 8 + (i % 3) * (cw + 6);
            int y = py + 24 + (i / 3) * (chh + 6);
            JsonObject s = slot(i);
            models[i] = null;
            if (s != null) {
                addRenderableWidget(Button.builder(Component.literal("Éditer"), b -> edit(k)).bounds(x + cw - 64, y + chh - 22, 58, 18).build());
                Species sp = PokemonSpecies.getByIdentifier(ResourceLocation.parse(s.get("species").getAsString()));
                if (sp != null && chh >= 70) {
                    Set<String> aspects = new HashSet<>();
                    for (JsonElement a : s.getAsJsonArray("aspects")) aspects.add(a.getAsString());
                    try {
                        int m = Math.min(chh - 26, 64);
                        models[i] = new ModelWidget(x + 4, y + 18, m, m, new RenderablePokemon(sp, aspects, ItemStack.EMPTY), 1.0f, 325f, -10.0, false, false, 15);
                    } catch (Throwable t) {
                        models[i] = null;
                    }
                }
            } else {
                addRenderableWidget(Button.builder(Component.literal("Importer"), b -> minecraft.setScreen(new ShowdownScreen(this,
                        "Importer dans l'emplacement " + (k + 1), "Colle un set Showdown…", t -> importText(k, t)))).bounds(x + cw - 70, y + chh - 22, 64, 18).build());
            }
        }
        int by = py + ph - 24;
        addRenderableWidget(Button.builder(Component.literal("Importer une équipe (Showdown)"), b -> minecraft.setScreen(new ShowdownScreen(this,
                "Importer une équipe (emplacements libres)", "Colle une équipe Showdown…", t -> importText(-1, t)))).bounds(px + 8, by, 190, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Actualiser"), b -> send("party_open", -1, null)).bounds(px + 202, by, 80, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(px + pw - 88, by, 80, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderTransparentBackground(g);
        UiKit.panel(g, px, py, pw, ph, UiKit.PANEL, UiKit.ACCENT);
        boolean self = data.has("self") && data.get("self").getAsBoolean();
        g.drawString(font, self ? "Mon équipe" : "Équipe de " + data.get("owner").getAsString(), px + 8, py + 8, UiKit.GOLD, false);
        for (int i = 0; i < 6; i++) {
            int x = px + 8 + (i % 3) * (cw + 6);
            int y = py + 24 + (i / 3) * (chh + 6);
            JsonObject s = slot(i);
            UiKit.panel(g, x, y, cw, chh, s == null ? 0xFF151A22 : UiKit.CARD, UiKit.BORDER);
            g.drawString(font, String.valueOf(i + 1), x + 4, y + 4, UiKit.MUTED, false);
            if (s == null) {
                g.drawString(font, "Vide", x + 16, y + 4, UiKit.MUTED, false);
                continue;
            }
            Species sp = PokemonSpecies.getByIdentifier(ResourceLocation.parse(s.get("species").getAsString()));
            String nick = s.get("nickname").getAsString();
            String name = nick.isEmpty() ? EditorScreen.speciesName(sp) : nick;
            boolean shiny = s.get("shiny").getAsBoolean();
            g.drawString(font, font.plainSubstrByWidth((shiny ? "★ " : "") + name, cw - 20), x + 16, y + 4, shiny ? UiKit.GOLD : UiKit.TEXT, false);
            int tx = x + 8 + Math.min(chh - 26, 64);
            g.drawString(font, "Nv. " + s.get("level").getAsInt(), tx, y + 20, UiKit.TEXT, false);
            String g2 = s.get("gender").getAsString();
            if (!g2.equals("genderless")) g.drawString(font, g2.equals("male") ? "♂" : "♀", tx + 40, y + 20, g2.equals("male") ? 0xFF6FA8FF : 0xFFFF7FB0, false);
            if (s.get("fainted").getAsBoolean()) g.drawString(font, "K.O.", tx, y + 32, UiKit.ERROR, false);
            String held = s.get("held").getAsString();
            if (!held.isEmpty()) {
                ResourceLocation rl = ResourceLocation.tryParse(held);
                if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(rl)), tx, y + 42);
            }
            if (models[i] != null) {
                try {
                    models[i].render(g, mx, my, pt);
                } catch (Throwable t) {
                    models[i] = null;
                }
            }
        }
        int sy = py + ph - 46;
        for (int i = 0; i < Math.min(2, status.size()); i++) {
            String s = status.get(i);
            if (i == 1 && status.size() > 2) s += "  (+" + (status.size() - 2) + ")";
            g.drawString(font, font.plainSubstrByWidth(s, pw - 16), px + 8, sy + i * 10, i == 0 ? statusColor : UiKit.ERROR, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // auto-test
    String owner() {
        return data.get("owner").getAsString();
    }

    List<String> status() {
        return status;
    }
}
