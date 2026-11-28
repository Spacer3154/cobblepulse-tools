package fr.cobblepulse.tools;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** config/cobblepulse/tools.json (recharge par /cptools reload). */
public final class ToolsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ToolsConfig current = new ToolsConfig();

    /** Vitesse donnee par une Movement Plate (blocs par tick avant frottement ; 0.6 = environ 6,5 blocs/s). */
    public double movement_plate_speed = 0.6;
    /** Recentrage de l'entite sur l'axe de la plaque (0 = aucun, 1 = immediat). */
    public double movement_plate_centering = 0.3;
    /** Ascenseur : distance maximale (en blocs) entre deux ascenseurs d'une meme colonne (Pixelmon : 10). */
    public int elevator_max_distance = 10;
    /** Ascenseur : son de teleportation a l'arrivee. */
    public boolean elevator_sound = true;
    /** Noeud LuckPerms de la baguette et des commandes /cptools. */
    public String admin_permission = "cobblepulse.tools.admin";
    /** Sans LuckPerms (ou noeud non defini) : niveau d'operateur requis. */
    public int op_level_fallback = 2;
    /** Ecrit chaque modification de Pokemon dans le journal du serveur. */
    public boolean log_edits = true;
    /** Autorise l'edition d'un Pokemon en plein combat (deconseille : le combat garde l'ancienne version). */
    public boolean allow_edit_in_battle = false;

    public static ToolsConfig get() {
        return current;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblepulse").resolve("tools.json");
    }

    public static void load() {
        Path p = path();
        ToolsConfig c = null;
        try {
            if (Files.exists(p)) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    c = GSON.fromJson(r, ToolsConfig.class);
                }
            }
        } catch (Exception e) {
            CobblePulseTools.LOG.error("[CobblePulse-Tools] tools.json illisible, valeurs par défaut utilisées : {}", e.toString());
        }
        if (c == null) c = new ToolsConfig();
        c.normalize();
        current = c;
        try {
            Files.createDirectories(p.getParent());
            try (Writer w = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                GSON.toJson(c, w);
            }
        } catch (Exception e) {
            CobblePulseTools.LOG.warn("[CobblePulse-Tools] Impossible d'écrire {} : {}", p, e.toString());
        }
    }

    private void normalize() {
        if (!(movement_plate_speed > 0)) movement_plate_speed = 0.6;
        movement_plate_speed = Math.min(3.0, movement_plate_speed);
        movement_plate_centering = Math.max(0, Math.min(1, movement_plate_centering));
        if (admin_permission == null || admin_permission.isBlank()) admin_permission = "cobblepulse.tools.admin";
        op_level_fallback = Math.max(0, Math.min(4, op_level_fallback));
        elevator_max_distance = Math.max(2, Math.min(384, elevator_max_distance));
    }

    /** Valeurs envoyees au client (la poussee du joueur est calculee chez lui). */
    public static void applyFromServer(double speed, double centering, int elevatorMax) {
        ToolsConfig c = new ToolsConfig();
        c.movement_plate_speed = speed;
        c.movement_plate_centering = centering;
        c.admin_permission = current.admin_permission;
        c.op_level_fallback = current.op_level_fallback;
        c.log_edits = current.log_edits;
        c.allow_edit_in_battle = current.allow_edit_in_battle;
        c.elevator_max_distance = elevatorMax;
        c.elevator_sound = current.elevator_sound;
        c.normalize();
        current = c;
    }
}
