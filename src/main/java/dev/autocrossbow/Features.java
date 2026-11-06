package dev.autocrossbow;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** On/off switches shared between the menu and the tools, saved between sessions. */
public final class Features {
    private Features() {}

    public static boolean iceGhost = false;
    public static boolean swordTech = false;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("alphapvpmenu.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (InputStream in = Files.newInputStream(f)) {
            Properties p = new Properties();
            p.load(in);
            iceGhost = Boolean.parseBoolean(p.getProperty("iceGhost", "false"));
            swordTech = Boolean.parseBoolean(p.getProperty("swordTech", "false"));
        } catch (IOException ignored) {
            // keep defaults
        }
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("iceGhost", Boolean.toString(iceGhost));
        p.setProperty("swordTech", Boolean.toString(swordTech));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Alpha's PvP Menu");
        } catch (IOException ignored) {
            // not fatal
        }
    }
}
