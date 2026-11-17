package com.enderio.core.common.registries;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class FluidDeferredRegister {
    private final String namespace;
    private final DeferredRegister<FluidType> fluidTypes;
    private final DeferredRegister<Fluid> fluids;
    private final DeferredRegister<Block> blocks;
    private final DeferredRegister<Item> items;

    public static FluidDeferredRegister create(String namespace) {
        return new FluidDeferredRegister(namespace);
    }

    protected FluidDeferredRegister(String namespace) {
        this.namespace = namespace;
        fluidTypes = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, namespace);
        fluids = DeferredRegister.create(Registries.FLUID, namespace);
        blocks = DeferredRegister.create(Registries.BLOCK, namespace);
        items = DeferredRegister.create(Registries.ITEM, namespace);
    }

    public Builder builder(String name) {
        return new Builder(name);
    }

    public DeferredRegister<FluidType> fluidTypesRegister() {
        return fluidTypes;
    }

    public DeferredRegister<Fluid> fluidsRegister() {
        return fluids;
    }

    public DeferredRegister<Item> itemsRegister() {
        return items;
    }

    public DeferredRegister<Block> blocksRegister() {
        return blocks;
    }

    public void register(IEventBus bus) {
        fluidTypes.register(bus);
        fluids.register(bus);
        blocks.register(bus);
        items.register(bus);
    }

    public class Builder {
        private final String name;

        private Function<FluidType.Properties, FluidType> fluidTypeFactory;
        private Function<ForgeFlowingFluid.Properties, ForgeFlowingFluid> sourceFluidFactory;
        private Function<ForgeFlowingFluid.Properties, ForgeFlowingFluid> flowingFluidFactory;
        private FluidType.Properties fluidTypeProperties;
        private BiFunction<FlowingFluid, BlockBehaviour.Properties, ? extends LiquidBlock> blockFactory;
        private BlockBehaviour.Properties blockProperties;

        @Nullable
        private Function<Fluid, BucketItem> bucketFactory;

        private int tintColor = 0xFFFFFFFF;

        private Builder(String name) {
            this.name = name;

            fluidTypeProperties = FluidType.Properties.create();

            fluidTypeFactory = (properties) -> new FluidType(properties) {
                // TODO: Handle deprecation...
                @Override
                public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        @Override
                        public ResourceLocation getStillTexture() {
                            return new ResourceLocation(namespace, "block/" + name + "_still");
                        }

                        @Override
                        public ResourceLocation getFlowingTexture() {
                            return new ResourceLocation(namespace, "block/" + name + "_flowing");
                        }

                        @Override
                        public int getTintColor() {
                            return tintColor;
                        }
                    });
                }
            };

            sourceFluidFactory = ForgeFlowingFluid.Source::new;
            flowingFluidFactory = ForgeFlowingFluid.Flowing::new;

            blockFactory = (fluid, properties) -> new LiquidBlock(() -> fluid, properties);
            blockProperties = BlockBehaviour.Properties.copy(Blocks.WATER);
        }

        public Builder fluidTypeFactory(Function<FluidType.Properties, FluidType> fluidTypeFactory) {
            this.fluidTypeFactory = fluidTypeFactory;
            return this;
        }

        public Builder customFluid(
            Function<ForgeFlowingFluid.Properties, ForgeFlowingFluid> sourceFluidFactory,
            Function<ForgeFlowingFluid.Properties, ForgeFlowingFluid> flowingFluidFactory) {
            this.sourceFluidFactory = sourceFluidFactory;
            this.flowingFluidFactory = flowingFluidFactory;
            return this;
        }

        public Builder fluidProperties(UnaryOperator<FluidType.Properties> fluidProperties) {
            this.fluidTypeProperties = fluidProperties.apply(this.fluidTypeProperties);
            return this;
        }

        public Builder defaultBucket() {
            this.bucketFactory = (fluid) -> new BucketItem(fluid, new Item.Properties()
                .craftRemainder(Items.BUCKET)
                .stacksTo(1));
            return this;
        }

        public Builder bucketFactory(Function<Fluid, BucketItem> bucketFactory) {
            this.bucketFactory = bucketFactory;
            return this;
        }

        public Builder noBucket() {
            this.bucketFactory = null;
            return this;
        }

        public Builder blockProperties(UnaryOperator<BlockBehaviour.Properties> blockProperties) {
            this.blockProperties = blockProperties.apply(this.blockProperties);
            return this;
        }

        public Builder tintColor(int tintColor) {
            this.tintColor = tintColor;
            return this;
        }

        public FluidDeferredHolders register() {
            // Create the holder instance now so that we can reference it in the factories below.
            var fluid = new FluidDeferredHolders();

            // Register everything.
            fluid.type(fluidTypes.register(name, () -> fluidTypeFactory.apply(fluidTypeProperties)));
            fluid.source(fluids.register("fluid_" + name + "_still", () -> sourceFluidFactory.apply(fluid.createProperties())));
            fluid.flowing(fluids.register("fluid_" + name + "_flowing", () -> flowingFluidFactory.apply(fluid.createProperties())));
            fluid.block(blocks.register(name, () -> blockFactory.apply(fluid.flowing().get(), blockProperties)));

            if (bucketFactory != null) {
                fluid.bucket(items.register(name + "_bucket", () -> bucketFactory.apply(fluid.source().get())));
            }

            return fluid;
        }
    }
}
