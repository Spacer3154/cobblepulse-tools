package fr.cobblepulse.tools.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Fenetre de collage d'un set (ou d'une equipe) au format Showdown, par-dessus l'editeur ou la vue d'equipe. */
final class ShowdownScreen extends Screen {
    private final Screen parent;
    private final String hint;
    private final Consumer<String> onImport;
    private MultiLineEditBox box;
    private String text = "";

    ShowdownScreen(Screen parent, String title, String hint, Consumer<String> onImport) {
        super(Component.literal(title));
        this.parent = parent;
        this.hint = hint;
        this.onImport = onImport;
    }

    @Override
    protected void init() {
        if (parent != null) parent.init(minecraft, width, height);
        int w = Math.min(width - 40, 360);
        int h = Math.min(height - 80, 190);
        int x = (width - w) / 2;
        int y = (height - h) / 2;
        box = addRenderableWidget(new MultiLineEditBox(font, x, y, w, h, Component.literal(hint), Component.literal("Showdown")));
        box.setCharacterLimit(6000);
        box.setValue(text);
        box.setValueListener(v -> text = v);
        setInitialFocus(box);
        addRenderableWidget(Button.builder(Component.literal("§aImporter"), b -> submit()).bounds(x + w - 170, y + h + 6, 82, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Annuler"), b -> onClose()).bounds(x + w - 84, y + h + 6, 84, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Coller"), b -> {
            String clip = minecraft.keyboardHandler.getClipboard();
            if (clip != null) {
                text = clip;
                box.setValue(clip);
            }
        }).bounds(x, y + h + 6, 70, 18).build());
    }

    void setText(String v) {
        text = v;
        if (box != null) box.setValue(v);
    }

    void submit() {
        String t = text;
        minecraft.setScreen(parent);
        onImport.accept(t);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        if (parent != null) parent.render(g, -1, -1, pt);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, width, height, 0xAA000000);
        g.drawCenteredString(font, getTitle(), width / 2, box.getY() - 14, UiKit.GOLD);
        super.render(g, mx, my, pt);
        g.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
