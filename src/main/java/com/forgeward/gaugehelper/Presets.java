package com.forgeward.gaugehelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Saved gauge setups, in config/forgeward_gaugehelper_presets.json on this PC. Create's FactoryPanelBehaviour stubs
 * readFromClipboard and writeToClipboard to return false (6.0.10, javap), so the clipboard can't carry a gauge's setup.
 * A preset holds what the screen's own packet sends: the gauge's filter and target count don't travel in it.
 */
final class Presets {
    static final int MAX = 64;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static final class Input {
        String item;
        int count;
        Input(String item, int count) { this.item = item; this.count = count; }
    }

    static final class Preset {
        String name;
        String output;
        int outputCount;
        String address;
        int promiseExpiry;
        List<Input> inputs = new ArrayList<>();

        boolean sameSetup(Preset o) {
            if (!Objects.equals(output, o.output) || outputCount != o.outputCount || promiseExpiry != o.promiseExpiry
                    || !Objects.equals(address, o.address) || inputs.size() != o.inputs.size()) return false;
            for (int n = 0; n < inputs.size(); n++) {
                Input a = inputs.get(n), b = o.inputs.get(n);
                if (!Objects.equals(a.item, b.item) || a.count != b.count) return false;
            }
            return true;
        }
    }

    private static List<Preset> all;
    private static boolean loadFailed;

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("forgeward_gaugehelper_presets.json");
    }

    static List<Preset> all() {
        if (all != null) return all;
        List<Preset> read = new ArrayList<>();
        Path f = file();
        if (Files.exists(f)) {
            try {
                List<Preset> parsed = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), new TypeToken<List<Preset>>() {}.getType());
                if (parsed != null) for (Preset p : parsed) if (p != null) read.add(clean(p));
            } catch (Exception e) {
                // the player's file stays: the next save copies it to .bad first
                loadFailed = true;
                GaugeHelper.LOG.error("[gaugehelper] presets file unreadable, starting empty: {}", e.toString());
            }
        }
        all = read;
        return all;
    }

    private static Preset clean(Preset p) {
        if (p.inputs == null) p.inputs = new ArrayList<>();
        p.inputs.removeIf(Objects::isNull);
        if (p.name == null) p.name = "?";
        if (p.address == null) p.address = "";
        return p;
    }

    /** True when the list reached the disk. */
    static boolean save() {
        Path f = file();
        Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
        try {
            if (loadFailed && Files.exists(f)) {
                Files.copy(f, f.resolveSibling(f.getFileName() + ".bad"), StandardCopyOption.REPLACE_EXISTING);
                loadFailed = false;
            }
            Files.writeString(tmp, GSON.toJson(all()), StandardCharsets.UTF_8);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            GaugeHelper.LOG.error("[gaugehelper] presets not saved: {}", e.toString());
            return false;
        }
    }

    private Presets() {}
}
