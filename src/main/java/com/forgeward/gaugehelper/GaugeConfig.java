package com.forgeward.gaugehelper;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config (config/forgeward_gaugehelper-client.toml). Every helper is off until the player turns it on. */
final class GaugeConfig {
    static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue CHANCE, SUGGEST, PRESETS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        CHANCE = b.comment("On the gauge screen, for a recipe with a chance output: how many to send, on average, for 1, plus any byproducts.")
                .define("chanceHelper", false);
        SUGGEST = b.comment("On the gauge screen: the expected yield per send and a suggested input count. Display only; nothing is filled in for you.")
                .define("suggestedAmount", false);
        PRESETS = b.comment("On the gauge screen: save a gauge's address, output and input amounts, and fill them into another gauge's screen.")
                .define("presets", false);
        SPEC = b.build();
    }

    static boolean chance() { try { return CHANCE.get(); } catch (Exception e) { return false; } }
    static boolean suggest() { try { return SUGGEST.get(); } catch (Exception e) { return false; } }
    static boolean presets() { try { return PRESETS.get(); } catch (Exception e) { return false; } }

    private GaugeConfig() {}
}
