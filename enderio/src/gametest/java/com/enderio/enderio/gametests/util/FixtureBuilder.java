package com.enderio.enderio.gametests.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Builds fixtures through Minecraft's public structure-NBT loader. */
public final class FixtureBuilder {
    private final int width, height, depth;
    private final Map<BlockPos, BlockState> states = new LinkedHashMap<>();

    private FixtureBuilder(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) for (int z = 0; z < depth; z++) {
            set(x, y, z, Blocks.AIR.defaultBlockState());
        }
    }

    public static FixtureBuilder withSize(int width, int height, int depth) {
        return new FixtureBuilder(width, height, depth);
    }

    public FixtureBuilder set(int x, int y, int z, BlockState state) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
            throw new IllegalArgumentException("Fixture block outside bounds: " + x + "," + y + "," + z);
        }
        states.put(new BlockPos(x, y, z), state);
        return this;
    }

    public static Supplier<StructureTemplate> lazy(int width, int height, int depth, UnaryOperator<FixtureBuilder> configure) {
        return () -> configure.apply(withSize(width, height, depth)).build();
    }

    public StructureTemplate build() {
        var tag = new CompoundTag();
        tag.put("size", coordinates(width, height, depth));
        var paletteStates = new ArrayList<BlockState>();
        var blocks = new ListTag();
        states.forEach((pos, state) -> {
            int index = paletteStates.indexOf(state);
            if (index < 0) { index = paletteStates.size(); paletteStates.add(state); }
            var block = new CompoundTag();
            block.put("pos", coordinates(pos.getX(), pos.getY(), pos.getZ()));
            block.putInt("state", index);
            blocks.add(block);
        });
        var palette = new ListTag();
        paletteStates.forEach(state -> palette.add(NbtUtils.writeBlockState(state)));
        tag.put("palette", palette);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        var template = new StructureTemplate();
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
        return template;
    }

    private static ListTag coordinates(int x, int y, int z) {
        var list = new ListTag();
        list.add(IntTag.valueOf(x)); list.add(IntTag.valueOf(y)); list.add(IntTag.valueOf(z));
        return list;
    }
}
