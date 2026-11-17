package com.forgeward.gaugehelper;

import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Optional helpers on Create's Factory Gauge screen. Client only; what a gauge sends is never changed. */
@Mod(value = "forgeward_gaugehelper", dist = Dist.CLIENT)
public final class GaugeHelper {
    static final Logger LOG = LoggerFactory.getLogger("forgeward_gaugehelper");

    /** Self-checks on every sync: real chance recipes, printed through the same code the panel uses. */
    private static final String[][] SAMPLES = {
            {"minecraft:clay_ball", "create:crushing/gravel"},
            {"minecraft:clay_ball", "create:splashing/crushed_raw_copper"},
    };

    private static Panel panel;

    public GaugeHelper(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, GaugeConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(ModConfigEvent.Loading.class, e -> report());
        modBus.addListener(ModConfigEvent.Reloading.class, e -> report());
        NeoForge.EVENT_BUS.addListener(RecipesUpdatedEvent.class, GaugeHelper::onRecipes);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, GaugeHelper::onRender);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseButtonPressed.Pre.class, GaugeHelper::onClick);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.MouseScrolled.Pre.class, e -> {
            if (panel != null && panel.isFor(e.getScreen()) && panel.wheel(e.getMouseX(), e.getMouseY(), e.getScrollDeltaY())) e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Closing.class, e -> { if (panel != null && panel.isFor(e.getScreen())) panel = null; });
    }

    private static String onOff(boolean on) { return on ? "on" : "off"; }

    private static void report() {
        LOG.info("[gaugehelper] 1.0.7 ready: chanceHelper={} suggestedAmount={} presets={} ({} saved)",
                onOff(GaugeConfig.chance()), onOff(GaugeConfig.suggest()), onOff(GaugeConfig.presets()), Presets.all().size());
    }

    private static void onRecipes(RecipesUpdatedEvent e) {
        try {
            GaugeIndex.rebuild(e.getRecipeManager());
            LOG.info("[gaugehelper] index: {} processing/sequenced recipes, {} with a chance output, {} skipped",
                    GaugeIndex.recipes, GaugeIndex.withChance, GaugeIndex.skipped);
            for (String[] s : SAMPLES) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(s[0]));
                ResourceLocation id = ResourceLocation.parse(s[1]);
                GaugeIndex.Entry found = null;
                for (GaugeIndex.Entry entry : GaugeIndex.makers(item)) if (entry.id().equals(id)) found = entry;
                if (found == null) LOG.info("[gaugehelper] sample {}: not among the loaded recipes", s[1]);
                else LOG.info("[gaugehelper] sample {}: {}", s[1], String.join(" | ", Panel.describe(found, item, 1, List.of(), true, true)));
            }
        } catch (Exception | LinkageError ex) {
            LOG.error("[gaugehelper] index failed, helper lines stay empty: {}", ex.toString());
        }
    }

    private static void onRender(ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof FactoryPanelScreen screen)) return;
        if (panel == null || !panel.isFor(screen)) panel = new Panel(screen);
        panel.render(e.getGuiGraphics(), e.getMouseX(), e.getMouseY());
    }

    private static void onClick(ScreenEvent.MouseButtonPressed.Pre e) {
        if (panel == null || !panel.isFor(e.getScreen()) || e.getButton() != 0) return;
        if (panel.click(e.getMouseX(), e.getMouseY())) e.setCanceled(true);
    }
}
