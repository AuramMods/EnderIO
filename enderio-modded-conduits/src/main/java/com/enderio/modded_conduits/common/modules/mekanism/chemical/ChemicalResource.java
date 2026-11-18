package com.enderio.modded_conduits.common.modules.mekanism.chemical;

import com.enderio.core.common.network.codec.PacketCodec;
import com.enderio.core.common.serialization.JsonCodec;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import io.netty.handler.codec.DecoderException;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.Chemical;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.ChemicalType;
import mekanism.api.chemical.gas.Gas;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfuseType;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.merged.BoxedChemicalStack;
import mekanism.api.chemical.pigment.Pigment;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.Slurry;
import mekanism.api.chemical.slurry.SlurryStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.IForgeRegistry;

/** An owned native Mekanism stack retaining its 1.20.1 chemical family. */
public final class ChemicalResource {
    public static final ChemicalResource EMPTY = new ChemicalResource(null);

    public static final Codec<ChemicalResource> OPTIONAL_CODEC = JsonCodec.of(element -> {
        var json = element.getAsJsonObject();
        if (json.size() == 0) return EMPTY;
        if (!json.has("chemical_type")) {
            throw new IllegalArgumentException("Chemical family is required (gas, infusion, pigment or slurry)");
        }
        return fromRegistry(ChemicalType.fromString(json.get("chemical_type").getAsString()),
            ResourceLocation.parse(json.get("id").getAsString()), new java.math.BigDecimal(json.get("amount").getAsString()).longValueExact());
    }, stack -> {
        var json = new JsonObject();
        if (!stack.isEmpty()) {
            json.addProperty("chemical_type", stack.family().getSerializedName());
            json.addProperty("id", stack.getChemical().getRegistryName().toString());
            json.addProperty("amount", stack.getAmount());
        }
        return json;
    });

    public static final PacketCodec<ChemicalResource> OPTIONAL_STREAM_CODEC = PacketCodec.of((buffer, stack) -> {
        buffer.writeBoolean(!stack.isEmpty());
        if (!stack.isEmpty()) {
            buffer.writeUtf(stack.family().getSerializedName(), 16);
            buffer.writeResourceLocation(stack.getChemical().getRegistryName());
            buffer.writeVarLong(stack.getAmount());
        }
    }, buffer -> {
        if (!buffer.readBoolean()) return EMPTY;
        try {
            return fromRegistry(ChemicalType.fromString(buffer.readUtf(16)), buffer.readResourceLocation(), buffer.readVarLong());
        } catch (IllegalArgumentException error) {
            throw new DecoderException("Invalid chemical stack", error);
        }
    });

    // Mod construction may register codecs before Mekanism creates its four chemical registries.
    // An untyped empty value needs no native stack until a caller requests one.
    private final @org.jetbrains.annotations.Nullable BoxedChemicalStack stack;

    private BoxedChemicalStack boxed() { return stack == null ? BoxedChemicalStack.EMPTY : stack; }

    private ChemicalResource(BoxedChemicalStack stack) {
        this.stack = stack;
    }

    public ChemicalResource(Chemical<?> chemical, long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative chemical amount");
        ChemicalStack<?> nativeStack;
        if (chemical instanceof Gas gas) nativeStack = new GasStack(gas, amount);
        else if (chemical instanceof InfuseType infusion) nativeStack = new InfusionStack(infusion, amount);
        else if (chemical instanceof Pigment pigment) nativeStack = new PigmentStack(pigment, amount);
        else if (chemical instanceof Slurry slurry) nativeStack = new SlurryStack(slurry, amount);
        else throw new IllegalArgumentException("Unsupported chemical family: " + chemical.getClass().getName());
        this.stack = BoxedChemicalStack.box(nativeStack);
    }

    public static ChemicalResource fromNative(ChemicalStack<?> stack) {
        return stack.isEmpty() ? EMPTY : new ChemicalResource(BoxedChemicalStack.box(stack.copy()));
    }

    public static ChemicalResource fromRegistry(ChemicalType family, ResourceLocation id, long amount) {
        if (family == null) throw new IllegalArgumentException("Unknown chemical family");
        if (amount <= 0) throw new IllegalArgumentException("Nonempty chemical amount must be positive");
        IForgeRegistry<? extends Chemical<?>> registry = switch (family) {
            case GAS -> MekanismAPI.gasRegistry();
            case INFUSION -> MekanismAPI.infuseTypeRegistry();
            case PIGMENT -> MekanismAPI.pigmentRegistry();
            case SLURRY -> MekanismAPI.slurryRegistry();
        };
        if (!registry.containsKey(id)) throw new IllegalArgumentException("Unknown " + family.getSerializedName() + ": " + id);
        var result = new ChemicalResource(registry.getValue(id), amount);
        if (result.isEmpty()) throw new IllegalArgumentException("Empty chemical ID in a nonempty stack: " + id);
        return result;
    }

    public ChemicalType family() { return boxed().getChemicalType(); }
    public Chemical<?> getChemical() { return boxed().getChemicalStack().getType(); }
    public long getAmount() { return stack == null ? 0 : stack.getChemicalStack().getAmount(); }
    public boolean isEmpty() { return stack == null || stack.isEmpty(); }
    public Component getTextComponent() { return boxed().getTextComponent(); }
    public int getChemicalTint() { return boxed().getChemicalStack().getChemicalTint(); }
    public ChemicalStack<?> nativeCopy() { return boxed().getChemicalStack().copy(); }
    public ChemicalResource copy() { return isEmpty() ? EMPTY : new ChemicalResource(stack.copy()); }

    public ChemicalResource copyWithAmount(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative chemical amount");
        return isEmpty() || amount == 0 ? EMPTY : new ChemicalResource(getChemical(), amount);
    }

    public void setAmount(long amount) {
        if (amount < 0) throw new IllegalArgumentException("Negative chemical amount");
        if (isEmpty()) {
            if (amount != 0) throw new IllegalArgumentException("Cannot fill an empty chemical without a type");
            return;
        }
        stack.getChemicalStack().setAmount(amount);
    }

    public static boolean isSameChemical(ChemicalResource left, ChemicalResource right) {
        if (left.isEmpty() || right.isEmpty()) return left.isEmpty() && right.isEmpty();
        return left.stack.getType().equals(right.stack.getType());
    }

    @Override public boolean equals(Object other) {
        return other instanceof ChemicalResource resource && getAmount() == resource.getAmount() && isSameChemical(this, resource);
    }
    @Override public int hashCode() { return isEmpty() ? 0 : stack.hashCode(); }
}
