package com.forgeward.gaugehelper;

import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which Create-family recipes make an item, with their chances. Self-contained on purpose: this mod depends on Create
 * alone, not on another mod's recipe index.
 */
final class GaugeIndex {
    record Out(Item item, int count, double chance) {
        double expected() { return count * chance; }
    }

    /** One ingredient group. An optional one (a sequenced assembly's step item) need not be wired to the gauge. */
    record In(Ingredient ingredient, int count, boolean required) {}

    record Entry(ResourceLocation id, List<In> inputs, List<Out> outputs) {
        double expected(Item item) {
            double sum = 0;
            for (Out o : outputs) if (o.item == item) sum += o.expected();
            return sum;
        }

        boolean hasChance() {
            for (Out o : outputs) if (o.chance < 1) return true;
            return false;
        }
    }

    private static Map<Item, List<Entry>> byOutput = Map.of();
    static int recipes, withChance, skipped;

    static void rebuild(RecipeManager manager) {
        Map<Item, List<Entry>> next = new HashMap<>();
        int all = 0, chance = 0, bad = 0;
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            try {
                Entry e = null;
                if (holder.value() instanceof ProcessingRecipe<?, ?> pr) e = processing(holder.id(), pr);
                else if (holder.value() instanceof SequencedAssemblyRecipe sa) e = sequenced(holder.id(), sa);
                if (e == null || e.outputs.isEmpty()) continue;
                all++;
                if (e.hasChance()) chance++;
                Set<Item> seen = new LinkedHashSet<>();
                for (Out o : e.outputs) seen.add(o.item);
                for (Item item : seen) next.computeIfAbsent(item, k -> new ArrayList<>()).add(e);
            } catch (Exception | LinkageError ex) {
                bad++;
            }
        }
        for (List<Entry> list : next.values()) list.sort((a, b) -> a.id.toString().compareTo(b.id.toString()));
        byOutput = next;
        recipes = all;
        withChance = chance;
        skipped = bad;
    }

    private static Entry processing(ResourceLocation id, ProcessingRecipe<?, ?> pr) {
        List<Out> out = new ArrayList<>();
        for (ProcessingOutput o : pr.getRollableResults()) {
            ItemStack s = o.getStack();
            if (!s.isEmpty()) out.add(new Out(s.getItem(), s.getCount(), o.getChance()));
        }
        List<In> in = new ArrayList<>();
        for (Ingredient i : pr.getIngredients()) add(in, i, true);
        return new Entry(id, in, out);
    }

    /** The result pool is weighted and exactly one result comes out, so a result's chance is its share of the weights. */
    private static Entry sequenced(ResourceLocation id, SequencedAssemblyRecipe sa) {
        double total = 0;
        for (ProcessingOutput o : sa.resultPool) total += o.getChance();
        if (total <= 0) total = 1;
        List<Out> out = new ArrayList<>();
        for (ProcessingOutput o : sa.resultPool) {
            ItemStack s = o.getStack();
            if (!s.isEmpty()) out.add(new Out(s.getItem(), s.getCount(), o.getChance() / total));
        }
        List<In> in = new ArrayList<>();
        add(in, sa.getIngredient(), true);
        ItemStack transitional = sa.getTransitionalItem();
        for (SequencedRecipe<?> step : sa.getSequence()) {
            for (Ingredient i : step.getRecipe().getIngredients()) {
                if (i.test(transitional)) continue;
                for (int loop = 0; loop < Math.max(1, sa.getLoops()); loop++) add(in, i, false);
            }
        }
        return new Entry(id, in, out);
    }

    private static void add(List<In> into, Ingredient ingredient, boolean required) {
        if (ingredient.isEmpty()) return;
        for (int n = 0; n < into.size(); n++) {
            In have = into.get(n);
            if (have.ingredient.equals(ingredient)) {
                into.set(n, new In(ingredient, have.count + 1, have.required || required));
                return;
            }
        }
        into.add(new In(ingredient, 1, required));
    }

    static List<Entry> makers(Item item) {
        return byOutput.getOrDefault(item, List.of());
    }

    /**
     * Recipes that fit what is wired to the gauge. With inputs wired, every wired item must feed the recipe and every required
     * ingredient must be wired: a partial match is not a match, and no match means the helper says nothing. With none wired
     * every recipe for the item stays in, and the panel only speaks once there is one, or the player picked one.
     */
    static List<Entry> candidates(Item output, List<ItemStack> wired) {
        List<Entry> fit = new ArrayList<>();
        for (Entry e : makers(output)) if (wired.isEmpty() || fits(e, wired)) fit.add(e);
        return fit;
    }

    private static boolean fits(Entry e, List<ItemStack> wired) {
        for (ItemStack stack : wired) {
            boolean used = false;
            for (In in : e.inputs) if (in.ingredient.test(stack)) { used = true; break; }
            if (!used) return false;
        }
        for (In in : e.inputs) {
            if (!in.required) continue;
            boolean met = false;
            for (ItemStack stack : wired) if (in.ingredient.test(stack)) { met = true; break; }
            if (!met) return false;
        }
        return true;
    }

    private GaugeIndex() {}
}
