package com.enderio.core.gametest;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.api.capacitor.ICapacitorExtension;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import com.enderio.enderio.api.io.IOMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;

public final class MachineTestContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "endercore_tests");
    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "endercore_tests");
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "endercore_tests");
    public static final RegistryObject<Item> CAPACITOR_EXTENSION = ITEMS.register("capacitor_extension", () -> new Item(new Item.Properties()) {
        @Override public ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt) {
            return new ICapabilityProvider() {
                private final LazyOptional<ICapacitorExtension> extension = LazyOptional.of(() -> (item, level) -> {});
                @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                    return capability == ICapacitorExtension.CAPABILITY ? extension.cast() : LazyOptional.empty();
                }
            };
        }
    });
    public static final RegistryObject<Block> BLOCK = BLOCKS.register("task_host", HostBlock::new);
    public static final RegistryObject<BlockEntityType<HostEntity>> TYPE = TYPES.register("task_host",
        () -> BlockEntityType.Builder.of(HostEntity::new, BLOCK.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        bus.addListener((RegisterCapabilitiesEvent event) -> event.register(ICapacitorExtension.class));
        TYPES.register(bus);
    }

    private static final class HostBlock extends BaseEntityBlock {
        HostBlock() { super(BlockBehaviour.Properties.of()); }
        @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HostEntity(pos, state); }
    }

    public static final class HostEntity extends EnderBlockEntity {
        public HostEntity(BlockPos pos, BlockState state) { super(TYPE.get(), pos, state); }
    }

    static final class IO implements IOConfigurable {
        private final EnumMap<Direction, IOMode> modes = new EnumMap<>(Direction.class);
        @Override public IOMode getIOMode(Direction side) { return modes.getOrDefault(side, IOMode.NONE); }
        @Override public boolean isIOConfigMutable() { return true; }
        @Override public boolean shouldRenderIOConfigOverlay() { return true; }
        @Override public void setIOMode(Direction side, IOMode mode) { modes.put(side, mode); }
        @Override public boolean supportsIOMode(Direction side, IOMode mode) { return true; }
    }
}
