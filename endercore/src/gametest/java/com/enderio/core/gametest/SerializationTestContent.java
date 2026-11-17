package com.enderio.core.gametest;

import com.enderio.core.common.network.codec.PacketCodecs;
import com.enderio.core.common.registries.ItemDeferredHolder;
import com.enderio.core.common.serialization.CodecIngredientSerializer;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.crafting.AbstractIngredient;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;
import org.jetbrains.annotations.Nullable;

public final class SerializationTestContent {
    public static final ItemDeferredHolder<Item> ITEM = ItemDataTestContent.ITEMS.registerItem("serialized_energy", properties -> new Item(properties) {
        @Override
        public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) { return new SavedEnergy(); }
    });

    private static final class SavedEnergy implements ICapabilitySerializable<Tag> {
        private final EnergyStorage storage = new EnergyStorage(1000);
        private final LazyOptional<EnergyStorage> optional = LazyOptional.of(() -> storage);
        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side) {
            return capability == ForgeCapabilities.ENERGY ? optional.cast() : LazyOptional.empty();
        }
        @Override
        public Tag serializeNBT() { return storage.serializeNBT(); }
        @Override
        public void deserializeNBT(Tag tag) { storage.deserializeNBT(tag); }
    }

    public static void register(IEventBus bus) { bus.addListener(SerializationTestContent::registerIngredients); }

    private static void registerIngredients(RegisterEvent event) {
        if (event.getRegistryKey().equals(ForgeRegistries.Keys.RECIPE_SERIALIZERS)) {
            CraftingHelper.register(ItemDataTestContent.id("label_ingredient"), LabelIngredient.SERIALIZER);
        }
    }

    public static final class LabelIngredient extends AbstractIngredient {
        public static final CodecIngredientSerializer<LabelIngredient> SERIALIZER = new CodecIngredientSerializer<>(
            Codec.STRING.fieldOf("label").xmap(LabelIngredient::new, value -> value.label).codec(),
            PacketCodecs.STRING_UTF8.map(LabelIngredient::new, value -> value.label));
        private final String label;
        public LabelIngredient(String label) { this.label = label; }
        @Override
        public boolean test(@Nullable ItemStack stack) {
            return stack != null && stack.is(Items.STONE) && stack.hasTag() && stack.getTag().getString("label").equals(label);
        }
        @Override
        public ItemStack[] getItems() {
            var stack = new ItemStack(Items.STONE);
            stack.getOrCreateTag().putString("label", label);
            return new ItemStack[] {stack};
        }
        @Override
        public boolean isEmpty() { return false; }
        @Override
        public boolean isSimple() { return false; }
        @Override
        public CodecIngredientSerializer<LabelIngredient> getSerializer() { return SERIALIZER; }
        @Override
        public JsonElement toJson() { return SERIALIZER.toJson(this); }
    }
}
