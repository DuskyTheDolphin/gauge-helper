package com.forgeward.gaugehelper;

import com.forgeward.gaugehelper.mixin.FactoryPanelScreenAccess;
import com.forgeward.gaugehelper.mixin.SimiScreenAccess;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.factoryBoard.FactoryPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The side panel beside one open gauge screen. It draws its own buttons from ScreenEvents instead of adding widgets, because
 * FactoryPanelScreen re-runs init() from its own buttons, which clears widgets without any NeoForge event.
 */
final class Panel {
    private static final int WIDTH = 176, PAD = 4, ROW = 11, BUTTON_H = 11, MIN_ROOM = 100, BODY_ROWS = 12;
    private static final String KEY = "forgeward_gaugehelper.";
    /** Recipe picks last for the session, per gauge slot and item. */
    private static final Map<String, ResourceLocation> PICKS = new HashMap<>();

    private record Button(int x, int y, int w, String label, Runnable action) {
        boolean over(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + BUTTON_H; }
    }

    private record Spec(int dx, int w, String label, Runnable action) {}

    private record Snapshot(String address, int promise, int output, List<Item> items, List<Integer> counts) {}

    /** fit above 0 holds the row at exactly that many lines, so what it says can't move what is under it. */
    private record Row(String text, int colour, List<Spec> specs, int fit) {
        Row(String text, int colour) { this(text, colour, List.of(), 0); }
        Row(String text, int colour, List<Spec> specs) { this(text, colour, specs, 0); }
        Row(String text, int colour, int fit) { this(text, colour, List.of(), fit); }
    }

    private record Line(FormattedCharSequence text, int colour, List<Spec> specs) {}

    private final FactoryPanelScreen screen;
    private final FactoryPanelScreenAccess access;
    private final boolean chance = GaugeConfig.chance(), suggest = GaugeConfig.suggest(), presets = GaugeConfig.presets();
    private final List<Button> buttons = new ArrayList<>();
    private boolean dead;
    private String logged, loggedLayout;
    private int scroll, bodyX, bodyY, bodyW, bodyH;
    private boolean canScroll, reserveBody;

    private List<Presets.Preset> order = List.of();
    private int selected;
    private boolean deleteArmed, applyArmed;
    /** Fields from before each Apply on this screen, newest first. Gone when the screen closes, along with this panel. */
    private final Deque<Snapshot> undo = new ArrayDeque<>();
    private String status;

    Panel(FactoryPanelScreen screen) {
        this.screen = screen;
        this.access = (FactoryPanelScreenAccess) (Object) screen;
        if (!chance && !suggest && !presets) {
            GaugeHelper.LOG.info("[gaugehelper] gauge screen opened: all toggles off, nothing drawn");
            dead = true;
        } else if (presets) reorder(null);
    }

    boolean isFor(Object s) { return s == screen; }

    void render(GuiGraphics g, int mx, int my) {
        if (dead) return;
        try {
            draw(g, mx, my);
        } catch (Exception | LinkageError e) {
            dead = true;
            buttons.clear();
            GaugeHelper.LOG.error("[gaugehelper] panel hidden for this screen: {}", e.toString());
        }
    }

    boolean click(double mx, double my) {
        if (dead) return false;
        for (Button b : buttons) {
            if (!b.over(mx, my)) continue;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
            try {
                b.action.run();
            } catch (Exception | LinkageError e) {
                GaugeHelper.LOG.error("[gaugehelper] button {} failed: {}", b.label, e.toString());
            }
            return true;
        }
        return false;
    }

    // ---- what is on the screen right now

    private Item output() {
        ItemStack filter = access.fwgh$behaviour().getFilter();
        return filter.isEmpty() ? null : filter.getItem();
    }

    private List<BigItemStack> wired() {
        List<BigItemStack> out = new ArrayList<>();
        for (BigItemStack b : access.fwgh$inputs()) if (b != null && !b.stack.isEmpty()) out.add(b);
        return out;
    }

    private int outputCount() {
        BigItemStack o = access.fwgh$output();
        return o == null ? 1 : Math.max(1, o.count);
    }

    private String pickKey(Item item) {
        return access.fwgh$behaviour().getPanelPosition() + "|" + BuiltInRegistries.ITEM.getKey(item);
    }

    // ---- drawing

    /**
     * Three zones, because at Auto GUI scale on 1080p the text alone can fill the screen: the recipe picker stays at the top,
     * the presets with their buttons stay at the bottom, and the helper text between them scrolls with the mouse wheel.
     */
    private void draw(GuiGraphics g, int mx, int my) {
        Font font = Minecraft.getInstance().font;
        SimiScreenAccess win = (SimiScreenAccess) (Object) screen;
        int left = win.fwgh$left(), top = win.fwgh$top();
        int roomLeft = left - 8;
        // Left of the window: Create draws its gauge icon on the right, and its slot tooltips open rightward from the mouse.
        int w = Math.max(MIN_ROOM, Math.min(WIDTH, roomLeft)), x = left - 4 - w;

        List<Row> headRows = new ArrayList<>(), bodyRows = new ArrayList<>(), footRows = new ArrayList<>();
        buttons.clear();
        bodyH = 0;
        reserveBody = false;

        if ((chance || suggest) && !access.fwgh$restocker() && !access.fwgh$craftingActive()) {
            Item item = output();
            if (item != null) helperRows(headRows, bodyRows, item);
        }
        // Crafting-active gauges send their own arrangement, and Apply there has never been tried, so it isn't offered.
        if (presets && !access.fwgh$craftingActive()) presetRows(footRows, w);
        if (headRows.isEmpty() && bodyRows.isEmpty() && footRows.isEmpty()) return;

        // No room beside the window: never draw over it, Create's confirm button lives there. One line below it, if that fits.
        if (roomLeft < MIN_ROOM) {
            buttons.clear();
            canScroll = false;
            int hintY = top + win.fwgh$height() + 2;
            boolean hint = hintY + font.lineHeight <= screen.height;
            if (hint) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 300);
                g.drawCenteredString(font, text("too_narrow"), left + win.fwgh$width() / 2, hintY, 0xFFE0C060);
                g.pose().popPose();
            }
            logLayout("too narrow: screen " + screen.width + "x" + screen.height + ", room " + roomLeft + " of " + MIN_ROOM + ", no panel, hint " + (hint ? "shown" : "off screen"));
            return;
        }

        int textW = w - PAD * 2;
        List<Line> head = wrap(headRows, font, textW), body = wrap(bodyRows, font, textW), foot = wrap(footRows, font, textW);
        int maxRows = Math.max(1, (screen.height - 8 - PAD * 2) / ROW);
        // The text gets a fixed box, so the panel's size and every button hold still while its lines and the status change.
        int viewport = reserveBody ? Math.min(BODY_ROWS, Math.max(0, maxRows - head.size() - foot.size())) : 0;
        int bodyShown = Math.min(body.size(), viewport);
        int height = PAD * 2 + (head.size() + viewport + foot.size()) * ROW;
        int y = Math.max(4, Math.min(top, screen.height - 4 - height));
        scroll = Math.max(0, Math.min(scroll, body.size() - bodyShown));

        logLayout("x " + x + " y " + y + " w " + w + " h " + height + " of screen " + screen.width + "x" + screen.height
                + ", text " + bodyShown + "/" + body.size() + " rows, picker " + head.size() + ", presets " + foot.size());

        g.pose().pushPose();
        // under vanilla's tooltip depth (400): this event fires after Create drew its tooltip, and depth keeps that on top
        g.pose().translate(0, 0, 300);
        g.fill(x, y, x + w, y + height, 0xE0181820);
        g.renderOutline(x, y, w, height, 0xFF5A5A70);

        int cy = y + PAD;
        cy = drawLines(g, font, head, 0, head.size(), x, cy);
        bodyX = x;
        bodyY = cy;
        bodyW = w;
        bodyH = bodyShown * ROW;
        canScroll = bodyShown < body.size();
        drawLines(g, font, body, scroll, bodyShown, x, cy);
        cy += viewport * ROW;
        if (canScroll) {
            int thumbH = Math.max(6, bodyH * bodyShown / body.size());
            int thumbY = bodyY + (bodyH - thumbH) * scroll / Math.max(1, body.size() - bodyShown);
            g.fill(x + w - 3, bodyY, x + w - 1, bodyY + bodyH, 0xFF30303C);
            g.fill(x + w - 3, thumbY, x + w - 1, thumbY + thumbH, 0xFFA0A0B8);
        }
        drawLines(g, font, foot, 0, foot.size(), x, cy);

        for (Button b : buttons) {
            g.fill(b.x, b.y, b.x + b.w, b.y + BUTTON_H, b.over(mx, my) ? 0xFF6A6A90 : 0xFF3A3A50);
            g.drawCenteredString(font, b.label, b.x + b.w / 2, b.y + 2, 0xFFFFFFFF);
        }
        g.pose().popPose();
    }

    private void logLayout(String layout) {
        if (layout.equals(loggedLayout)) return;
        loggedLayout = layout;
        GaugeHelper.LOG.info("[gaugehelper] layout: {}", layout);
    }

    private int drawLines(GuiGraphics g, Font font, List<Line> lines, int from, int count, int x, int y) {
        for (int n = from; n < from + count; n++) {
            Line line = lines.get(n);
            if (line.colour != 0) g.drawString(font, line.text, x + PAD, y + 1, line.colour, false);
            for (Spec s : line.specs) buttons.add(new Button(x + PAD + s.dx, y, s.w, s.label, s.action));
            y += ROW;
        }
        return y;
    }

    private static List<Line> wrap(List<Row> rows, Font font, int width) {
        List<Line> out = new ArrayList<>();
        for (Row r : rows) {
            if (r.text.isEmpty() && r.fit == 0) {
                out.add(new Line(FormattedCharSequence.EMPTY, 0, r.specs));
                continue;
            }
            int added = 0;
            if (!r.text.isEmpty()) {
                for (FormattedCharSequence seq : font.split(Component.literal(r.text), width)) {
                    if (r.fit > 0 && added == r.fit) break;
                    out.add(new Line(seq, r.colour, List.of()));
                    added++;
                }
            }
            for (; added < r.fit; added++) out.add(new Line(FormattedCharSequence.EMPTY, 0, List.of()));
        }
        return out;
    }

    /** True when the wheel moved the helper text. Anywhere else the wheel stays Create's. */
    boolean wheel(double mx, double my, double delta) {
        if (dead || !canScroll || mx < bodyX || mx >= bodyX + bodyW || my < bodyY || my >= bodyY + bodyH) return false;
        scroll += delta > 0 ? -1 : 1;
        return true;
    }

    private void helperRows(List<Row> head, List<Row> body, Item item) {
        List<BigItemStack> wired = wired();
        List<ItemStack> stacks = new ArrayList<>();
        for (BigItemStack b : wired) stacks.add(b.stack);
        List<GaugeIndex.Entry> fit = GaugeIndex.candidates(item, stacks);
        GaugeIndex.Entry entry = null;
        if (fit.size() == 1) entry = fit.get(0);
        else if (fit.size() > 1) {
            ResourceLocation picked = PICKS.get(pickKey(item));
            int at = -1;
            for (int n = 0; n < fit.size(); n++) if (fit.get(n).id().equals(picked)) at = n;
            if (at >= 0) entry = fit.get(at);
            head.add(new Row(at >= 0 ? text("picked", at + 1, fit.size()) : text("pick", fit.size()), 0xFFE0C060));
            int back = at <= 0 ? fit.size() - 1 : at - 1, next = at + 1 >= fit.size() ? 0 : at + 1;
            head.add(new Row("", 0, List.of(
                    new Spec(0, 14, "<", () -> choose(item, fit.get(back))),
                    new Spec(16, 14, ">", () -> choose(item, fit.get(next))))));
        }
        List<String> lines = entry == null ? List.of() : describe(entry, item, outputCount(), wired, chance, suggest);
        if (!lines.isEmpty()) body.add(new Row(entry.id().toString(), 0xFF909090));
        for (String line : lines) body.add(new Row(line, 0xFFFFFFFF));
        // a picker's choice can bring text in, so its box is held from the start
        reserveBody = fit.size() > 1 || !lines.isEmpty();
        logShown(item, wired, fit.size(), entry, lines);
    }

    private void choose(Item item, GaugeIndex.Entry entry) {
        PICKS.put(pickKey(item), entry.id());
        scroll = 0;
    }

    /** One line per change, so a test can read exactly what the player was shown. */
    private void logShown(Item item, List<BigItemStack> wired, int candidates, GaugeIndex.Entry entry, List<String> lines) {
        StringBuilder in = new StringBuilder();
        for (BigItemStack b : wired) in.append(BuiltInRegistries.ITEM.getKey(b.stack.getItem())).append(" x").append(b.count).append(' ');
        String now = BuiltInRegistries.ITEM.getKey(item) + " x" + outputCount() + " from [" + in.toString().trim() + "], " + candidates
                + " candidates, recipe " + (entry == null ? "none (saying nothing)" : entry.id()) + ": " + String.join(" | ", lines);
        if (now.equals(logged)) return;
        logged = now;
        GaugeHelper.LOG.info("[gaugehelper] shown: {}", now);
    }

    // ---- the helper lines

    /** The text for one recipe. An empty wired list means nothing is connected, so the inputs come from the recipe itself. */
    static List<String> describe(GaugeIndex.Entry e, Item item, int target, List<BigItemStack> wired, boolean chance, boolean suggest) {
        List<String> out = new ArrayList<>();
        double perCraft = e.expected(item);
        if (perCraft <= 0) return out;
        boolean average = false;
        List<GaugeIndex.Out> own = new ArrayList<>();
        for (GaugeIndex.Out o : e.outputs()) {
            if (o.item() != item) continue;
            own.add(o);
            if (o.chance() < 1) average = true;
        }

        // what one send holds: the wired inputs, or the recipe's own required ingredients
        List<ItemStack> showItems = new ArrayList<>();
        List<Integer> perCraftIn = new ArrayList<>();
        if (!wired.isEmpty()) {
            for (BigItemStack b : wired) {
                int n = 0;
                for (GaugeIndex.In in : e.inputs()) if (in.ingredient().test(b.stack)) n += in.count();
                showItems.add(b.stack);
                perCraftIn.add(Math.max(1, n));
            }
        } else {
            for (GaugeIndex.In in : e.inputs()) {
                if (!in.required()) continue;
                ItemStack[] options = in.ingredient().getItems();
                showItems.add(options.length == 0 ? new ItemStack(Items.BARRIER) : options[0]);
                perCraftIn.add(in.count());
            }
        }

        if (chance) {
            if (average) {
                int forOne = Odds.crafts(perCraft, 1);
                if (own.size() > 1) out.add(text("mixed", Odds.amount(perCraft)));
                else if (own.get(0).count() > 1) out.add(text("chance_of", Odds.percent(own.get(0).chance()), own.get(0).count(), Odds.amount(perCraft)));
                else if (showItems.size() > 1) out.add(text("chance_sets", Odds.percent(own.get(0).chance()), forOne));
                else out.add(text("chance", Odds.percent(own.get(0).chance()), forOne * (perCraftIn.isEmpty() ? 1 : perCraftIn.get(0))));
            }
            List<String> also = new ArrayList<>();
            for (GaugeIndex.Out o : e.outputs()) {
                if (o.item() != item) also.add(text("also_line", name(o.item()), o.count(), Odds.percent(o.chance())));
            }
            if (!also.isEmpty()) {
                out.add(text("also"));
                out.addAll(also);
            }
        }

        if (suggest) {
            out.add(text(average ? "expected_about" : "expected", Odds.amount(perCraft)));
            int crafts = Odds.crafts(perCraft, target);
            out.add(text(average ? "suggested_about" : "suggested", target));
            boolean overCap = false;
            for (int n = 0; n < showItems.size(); n++) {
                int need = crafts * perCraftIn.get(n);
                if (need > 64) overCap = true;
                out.add(text("suggested_line", showItems.get(n).getHoverName().getString(), need));
            }
            if (overCap) out.add(text("cap"));
            if (!wired.isEmpty()) {
                double sets = Double.MAX_VALUE;
                for (int n = 0; n < wired.size(); n++) sets = Math.min(sets, wired.get(n).count / (double) perCraftIn.get(n));
                out.add(text(average ? "now_about" : "now", Odds.amount(sets * perCraft), name(item)));
            }
        }
        return out;
    }

    private static String name(Item item) {
        return item.getDescription().getString();
    }

    private static String text(String key, Object... args) {
        return Component.translatable(KEY + key, args).getString();
    }

    // ---- presets

    private void presetRows(List<Row> rows, int w) {
        rows.add(new Row(text("presets", order.size()), 0xFFE0C060));
        rows.add(new Row(text("presets_note"), 0xFF909090));
        List<Spec> first = new ArrayList<>(), second = new ArrayList<>();
        first.add(new Spec(0, 30, text("save"), this::save));
        int room = w - PAD * 2;
        // a narrow panel can't hold one row, and a button past its edge would sit on Create's window
        boolean oneRow = room >= 96 + 30;
        if (!order.isEmpty()) {
            first.add(new Spec(32, 12, "<", () -> step(-1)));
            first.add(new Spec(46, 12, ">", () -> step(1)));
            String del = text(deleteArmed ? "sure" : "delete"), fill = text(applyArmed ? "sure" : "apply");
            if (oneRow) {
                first.add(new Spec(60, 34, fill, this::apply));
                first.add(new Spec(96, room - 96, del, this::delete));
            } else {
                second.add(new Spec(0, 34, fill, this::apply));
                second.add(new Spec(36, 30, del, this::delete));
            }
        }
        rows.add(new Row("", 0, first));
        if (!oneRow) rows.add(new Row("", 0, second));
        // its own row, held even when empty, so it never sits where Apply was and a third click can't land on it by accident
        rows.add(new Row("", 0, undo.isEmpty() ? List.of() : List.of(new Spec(0, 34, text("undo"), this::undo))));
        // Both held at two lines, so a new status line never moves the buttons (at 720p a click meant for Save landed on Apply).
        rows.add(new Row(order.isEmpty() ? text("none") : (selected + 1) + ": " + order.get(selected).name, 0xFFFFFFFF, 2));
        rows.add(new Row(status == null ? "" : status, 0xFF90D090, 2));
    }

    /** Presets for this gauge's item come first; the rest follow, since an address or amounts can carry over. */
    private void reorder(Presets.Preset keep) {
        String own = ownId();
        List<Presets.Preset> first = new ArrayList<>(), rest = new ArrayList<>();
        for (Presets.Preset p : Presets.all()) (own != null && own.equals(p.output) ? first : rest).add(p);
        first.addAll(rest);
        order = first;
        int at = keep == null ? -1 : order.indexOf(keep);
        selected = Math.max(at, 0);
    }

    private String ownId() {
        Item item = output();
        return item == null ? null : BuiltInRegistries.ITEM.getKey(item).toString();
    }

    private void step(int by) {
        deleteArmed = false;
        applyArmed = false;
        status = null;
        if (!order.isEmpty()) selected = Math.floorMod(selected + by, order.size());
    }

    private void save() {
        deleteArmed = false;
        applyArmed = false;
        Presets.Preset p = new Presets.Preset();
        Item item = output();
        p.output = ownId();
        p.outputCount = access.fwgh$restocker() ? 0 : outputCount();
        p.address = access.fwgh$address().getValue();
        p.promiseExpiry = access.fwgh$promise().getState();
        for (BigItemStack b : wired()) p.inputs.add(new Presets.Input(BuiltInRegistries.ITEM.getKey(b.stack.getItem()).toString(), b.count));
        for (Presets.Preset have : Presets.all()) {
            if (have.sameSetup(p)) {
                reorder(have);
                status = text("saved");
                GaugeHelper.LOG.info("[gaugehelper] preset already saved: {}, {} total", have.name, Presets.all().size());
                return;
            }
        }
        if (Presets.all().size() >= Presets.MAX) {
            status = text("full");
            return;
        }
        p.name = (item == null ? "Any item" : name(item)) + (p.outputCount > 0 ? " x" + p.outputCount : "") + (p.address.isEmpty() ? "" : " @" + p.address);
        Presets.all().add(p);
        status = text(Presets.save() ? "saved" : "save_failed");
        reorder(p);
        GaugeHelper.LOG.info("[gaugehelper] preset saved: {} ({} inputs), {} total", p.name, p.inputs.size(), Presets.all().size());
    }

    /**
     * Fills the screen's own fields, on the second click. FactoryPanelScreen.removed() sends them on every close, Esc included,
     * so a mistaken Apply can't be cancelled by leaving: hence the Sure? step and the Undo.
     */
    private void apply() {
        deleteArmed = false;
        if (order.isEmpty()) return;
        if (!applyArmed) {
            applyArmed = true;
            status = text("apply_sure");
            return;
        }
        applyArmed = false;
        undo.push(snapshot());
        Presets.Preset p = order.get(selected);
        access.fwgh$address().setValue(p.address);
        access.fwgh$promise().setState(p.promiseExpiry);
        access.fwgh$promise().onChanged();
        BigItemStack output = access.fwgh$output();
        if (!access.fwgh$restocker() && output != null && p.outputCount > 0) output.count = p.outputCount;
        int matched = 0;
        List<Presets.Input> left = new ArrayList<>(p.inputs);
        List<BigItemStack> wired = wired();
        int total = wired.size();
        for (BigItemStack b : wired) {
            String id = BuiltInRegistries.ITEM.getKey(b.stack.getItem()).toString();
            for (Presets.Input in : left) {
                if (id.equals(in.item)) {
                    b.count = Math.max(1, in.count);
                    left.remove(in);
                    matched++;
                    break;
                }
            }
        }
        status = text("applied", matched, total);
        GaugeHelper.LOG.info("[gaugehelper] preset applied: {} ({} of {} inputs matched)", p.name, matched, total);
    }

    private Snapshot snapshot() {
        List<BigItemStack> wired = wired();
        List<Item> items = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (BigItemStack b : wired) {
            items.add(b.stack.getItem());
            counts.add(b.count);
        }
        return new Snapshot(access.fwgh$address().getValue(), access.fwgh$promise().getState(), outputCount(), items, counts);
    }

    /** Puts back the fields from before the latest Apply. Goes by position and item: Create can rebuild its input list. */
    private void undo() {
        if (undo.isEmpty()) return;
        applyArmed = false;
        deleteArmed = false;
        Snapshot s = undo.pop();
        access.fwgh$address().setValue(s.address);
        access.fwgh$promise().setState(s.promise);
        access.fwgh$promise().onChanged();
        BigItemStack output = access.fwgh$output();
        if (!access.fwgh$restocker() && output != null) output.count = s.output;
        List<BigItemStack> wired = wired();
        int restored = 0;
        for (int n = 0; n < wired.size() && n < s.items.size(); n++) {
            BigItemStack b = wired.get(n);
            if (b.stack.getItem() != s.items.get(n)) continue;
            b.count = s.counts.get(n);
            restored++;
        }
        status = text("undone");
        GaugeHelper.LOG.info("[gaugehelper] preset undone: {} of {} inputs restored, {} more to undo", restored, wired.size(), undo.size());
    }

    private void delete() {
        if (order.isEmpty()) return;
        if (applyArmed) {
            applyArmed = false;
            status = null;
        }
        if (!deleteArmed) {
            deleteArmed = true;
            return;
        }
        deleteArmed = false;
        Presets.Preset p = order.get(selected);
        Presets.all().remove(p);
        status = text(Presets.save() ? "deleted" : "save_failed");
        reorder(null);
        GaugeHelper.LOG.info("[gaugehelper] preset deleted: {}, {} left", p.name, Presets.all().size());
    }
}
