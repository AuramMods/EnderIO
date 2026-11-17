package com.enderio.core.common.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/** Adds a subtable's raw rolls; subsequent global modifiers process the combined output once. */
public final class AddTableLootModifier extends LootModifier {
    public static final Codec<AddTableLootModifier> CODEC = RecordCodecBuilder.create(instance -> codecStart(instance)
        .and(ResourceLocation.CODEC.fieldOf("table").forGetter(modifier -> modifier.table))
        .apply(instance, AddTableLootModifier::new));
    private final ResourceLocation table;

    public AddTableLootModifier(LootItemCondition[] conditions, ResourceLocation table) {
        super(conditions);
        this.table = table;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        context.getLevel().getServer().getLootData().getLootTable(table)
            .getRandomItemsRaw(context, LootTable.createStackSplitter(context.getLevel(), loot::add));
        return loot;
    }

    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
