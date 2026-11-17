package com.enderio.enderio.content.broken_spawner;

import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.api.EnderIOAPI;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

public class BrokenSpawnerLootModifier extends LootModifier {
    private static final TagKey<Item> TOOL_BLACKLIST = TagKey.create(Registries.ITEM, EnderIOAPI.rl("blacklists/broken_spawner"));
    public static final Codec<BrokenSpawnerLootModifier> CODEC = RecordCodecBuilder.create(inst -> codecStart(inst).apply(inst, BrokenSpawnerLootModifier::new));

    /**
     * Constructs a LootModifier.
     *
     * @param conditionsIn the ILootConditions that need to be matched before the loot is modified.
     */
    public BrokenSpawnerLootModifier(LootItemCondition[] conditionsIn) {
        super(conditionsIn);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        if (!(context.getParamOrNull(LootContextParams.BLOCK_ENTITY) instanceof SpawnerBlockEntity spawnerBlockEntity)) {
            return generatedLoot;
        }
        ItemStack tool = context.getParamOrNull(LootContextParams.TOOL);
        if (tool != null && tool.is(TOOL_BLACKLIST)) return generatedLoot;
        if (context.getRandom().nextFloat() >= BaseConfig.COMMON.BLOCKS.BROKEN_SPAWNER_DROP_CHANCE.get()) return generatedLoot;

        // Native 1.20.1 exposes the current spawn entry through save(); do not
        // instantiate its display mob or access private version-dependent fields.
        CompoundTag entityTag = spawnerBlockEntity.getSpawner().save(new CompoundTag())
            .getCompound("SpawnData").getCompound("entity");
        ResourceLocation type = ResourceLocation.tryParse(entityTag.getString(Entity.ID_TAG));
        if (type != null && BuiltInRegistries.ENTITY_TYPE.containsKey(type)) {
            var item = BuiltInRegistries.ITEM.getOptional(EnderIOAPI.rl("broken_spawner"))
                .orElseThrow(() -> new IllegalStateException("Broken spawner item is not registered"));
            var brokenSpawner = new ItemStack(item);
            Soul.ITEM_DATA.set(brokenSpawner, Soul.of(type));
            generatedLoot.add(brokenSpawner);
        }

        return generatedLoot;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
