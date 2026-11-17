package com.enderio.core.common.recipes;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.serialization.JsonCodec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Reload-aware fluid recipe predicates backed by native Forge fluids and Minecraft tags. */
public final class FluidIngredient implements Predicate<FluidStack> {
    public static final Codec<FluidIngredient> CODEC = JsonCodec.of(FluidIngredient::fromJson, FluidIngredient::toJson);
    public static final PacketCodec<FluidIngredient> STREAM_CODEC = PacketCodec.of(
        (buffer, value) -> buffer.writeUtf(value.toJson().toString()),
        buffer -> fromJson(JsonParser.parseString(buffer.readUtf())));
    private enum Kind { FLUID, TAG, COMPOUND, INTERSECTION, DIFFERENCE }
    private final Kind kind;
    private final ResourceLocation id;
    private final List<FluidIngredient> children;

    private FluidIngredient(Kind kind, ResourceLocation id, List<FluidIngredient> children) {
        this.kind = kind;
        this.id = id;
        this.children = List.copyOf(children);
    }
    public static FluidIngredient of(Fluid fluid) {
        var id = BuiltInRegistries.FLUID.getKey(fluid);
        if (fluid == Fluids.EMPTY || id == null || BuiltInRegistries.FLUID.get(id) != fluid) throw new IllegalArgumentException("Unregistered or empty fluid ingredient");
        return new FluidIngredient(Kind.FLUID, id, List.of());
    }
    public static FluidIngredient tag(TagKey<Fluid> tag) {
        if (!tag.isFor(Registries.FLUID)) throw new IllegalArgumentException("Wrong tag registry");
        return new FluidIngredient(Kind.TAG, tag.location(), List.of());
    }
    public static FluidIngredient anyOf(FluidIngredient... children) { return combine(Kind.COMPOUND, List.of(children)); }
    public static FluidIngredient allOf(FluidIngredient... children) { return combine(Kind.INTERSECTION, List.of(children)); }
    public static FluidIngredient difference(FluidIngredient base, FluidIngredient excluded) { return combine(Kind.DIFFERENCE, List.of(base, excluded)); }
    private static FluidIngredient combine(Kind kind, List<FluidIngredient> children) {
        if (children.isEmpty() || children.size() > 256) throw new IllegalArgumentException("Expected 1-256 fluid ingredients");
        return new FluidIngredient(kind, null, children);
    }
    @Override
    public boolean test(FluidStack stack) {
        if (stack.isEmpty()) return false;
        return switch (kind) {
            case FLUID -> BuiltInRegistries.FLUID.getKey(stack.getFluid()).equals(id);
            case TAG -> stack.getFluid().builtInRegistryHolder().is(TagKey.create(Registries.FLUID, id));
            case COMPOUND -> children.stream().anyMatch(child -> child.test(stack));
            case INTERSECTION -> children.stream().allMatch(child -> child.test(stack));
            case DIFFERENCE -> children.get(0).test(stack) && !children.get(1).test(stack);
        };
    }
    public FluidStack[] getStacks() {
        // Never cache expanded tags: datapack reload can change their membership.
        return BuiltInRegistries.FLUID.stream().filter(fluid -> fluid != Fluids.EMPTY)
            .map(fluid -> new FluidStack(fluid, 1)).filter(this).toArray(FluidStack[]::new);
    }
    public static FluidIngredient fromJson(JsonElement json) { return fromJson(json, 0); }
    private static FluidIngredient fromJson(JsonElement element, int depth) {
        if (depth > 32) throw new JsonSyntaxException("Fluid ingredient nesting exceeds 32");
        if (element.isJsonArray()) return readChildren(Kind.COMPOUND, element.getAsJsonArray(), depth);
        JsonObject json = GsonHelper.convertToJsonObject(element, "fluid ingredient");
        if (json.has("type")) {
            String type = GsonHelper.getAsString(json, "type");
            return switch (type) {
                case "endercore:compound", "neoforge:compound" -> readChildren(Kind.COMPOUND, GsonHelper.getAsJsonArray(json, "ingredients"), depth);
                case "endercore:intersection", "neoforge:intersection" -> readChildren(Kind.INTERSECTION, GsonHelper.getAsJsonArray(json, "children"), depth);
                case "endercore:difference", "neoforge:difference" -> difference(fromJson(json.get("base"), depth + 1), fromJson(json.get("subtracted"), depth + 1));
                default -> throw new JsonSyntaxException("Unknown fluid ingredient type: " + type);
            };
        }
        if (json.has("fluid") && json.has("tag")) throw new JsonSyntaxException("Specify a fluid or tag, not both");
        if (json.has("fluid")) {
            var id = ResourceLocation.tryParse(GsonHelper.getAsString(json, "fluid"));
            if (id == null || !BuiltInRegistries.FLUID.containsKey(id)) throw new JsonSyntaxException("Unknown fluid ingredient");
            return of(BuiltInRegistries.FLUID.get(id));
        }
        if (json.has("tag")) return tag(TagKey.create(Registries.FLUID, ResourceLocation.parse(GsonHelper.getAsString(json, "tag"))));
        throw new JsonSyntaxException("Expected a fluid, tag or compound fluid ingredient");
    }
    private static FluidIngredient readChildren(Kind kind, JsonArray json, int depth) {
        if (json.size() == 0 || json.size() > 256) throw new JsonSyntaxException("Expected 1-256 fluid ingredients");
        var children = new java.util.ArrayList<FluidIngredient>();
        for (var child : json) children.add(fromJson(child, depth + 1));
        return combine(kind, children);
    }
    public JsonObject toJson() {
        var json = new JsonObject();
        switch (kind) {
            case FLUID -> json.addProperty("fluid", id.toString());
            case TAG -> json.addProperty("tag", id.toString());
            case COMPOUND, INTERSECTION -> {
                json.addProperty("type", kind == Kind.COMPOUND ? "endercore:compound" : "endercore:intersection");
                var entries = new JsonArray();
                children.forEach(child -> entries.add(child.toJson()));
                json.add(kind == Kind.COMPOUND ? "ingredients" : "children", entries);
            }
            case DIFFERENCE -> {
                json.addProperty("type", "endercore:difference");
                json.add("base", children.get(0).toJson());
                json.add("subtracted", children.get(1).toJson());
            }
        }
        return json;
    }
    @Override
    public boolean equals(Object other) { return other instanceof FluidIngredient value && kind == value.kind && Objects.equals(id, value.id) && children.equals(value.children); }
    @Override
    public int hashCode() { return Objects.hash(kind, id, children); }
}
