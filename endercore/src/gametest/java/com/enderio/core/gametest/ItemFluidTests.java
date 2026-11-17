package com.enderio.core.gametest;

import com.enderio.core.common.capability.NbtFluidHandlerItemStack;
import com.enderio.core.common.capability.StrictFluidHandlerItemStack;
import com.enderio.core.common.item.ItemDataKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import static com.enderio.core.gametest.ItemDataTestContent.*;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class ItemFluidTests {
    private static final ItemDataKey<FluidStack> FLUID = new ItemDataKey<>(id("fluid"), FluidStack.CODEC, false);

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void transfersAndSimulationUseTheSamePersistentData(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        var handler = new NbtFluidHandlerItemStack(FLUID, stack, 1000);
        var water = new FluidStack(Fluids.WATER, 1200);
        water.getOrCreateTag().putString("source", "test");
        helper.assertTrue(handler.fill(water, FluidAction.SIMULATE) == 1000 && !stack.hasTag(), "Fill simulation mutated state");
        helper.assertTrue(handler.fill(water, FluidAction.EXECUTE) == 1000, "Fill exceeded capacity");
        helper.assertTrue(FLUID.get(stack).getAmount() == 1000, "Capability and item state disagreed");
        var before = stack.save(new CompoundTag());
        helper.assertTrue(handler.drain(400, FluidAction.SIMULATE).getAmount() == 400 && before.equals(stack.save(new CompoundTag())), "Drain simulation mutated state");
        helper.assertTrue(handler.drain(400, FluidAction.EXECUTE).getAmount() == 400 && FLUID.get(stack).getAmount() == 600, "Drain accounting failed");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 500), FluidAction.EXECUTE) == 0, "Mixed incompatible fluids");
        helper.assertTrue(water.getAmount() == 1200, "Fill modified the caller's fluid");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fluidCopiesAndNativeSaveLoadKeepTagsIndependent(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        var handler = new NbtFluidHandlerItemStack(FLUID, stack, 1000);
        var fluid = new FluidStack(Fluids.WATER, 500);
        fluid.getOrCreateTag().putInt("variant", 7);
        handler.setFluid(fluid);
        fluid.setAmount(1);
        var exposed = handler.getFluid();
        exposed.getOrCreateTag().putInt("variant", 99);
        helper.assertTrue(handler.getFluid().getAmount() == 500 && handler.getFluid().getTag().getInt("variant") == 7, "Mutable fluid data escaped");
        var copied = new NbtFluidHandlerItemStack(FLUID, stack.copy(), 1000);
        copied.drain(200, FluidAction.EXECUTE);
        helper.assertTrue(handler.getFluid().getAmount() == 500, "Copied fluid handler shared state");
        var loaded = ItemStack.of(copied.getContainer().save(new CompoundTag()));
        helper.assertTrue(new NbtFluidHandlerItemStack(FLUID, loaded, 1000).getFluid().isFluidStackIdentical(copied.getFluid()), "Native persistence lost fluid tag/amount");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void drainingEmptyPreservesUnrelatedDataAndStackRules(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        stack.getOrCreateTag().putString("unrelated", "keep");
        var handler = new NbtFluidHandlerItemStack(FLUID, stack, 1000);
        handler.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE);
        helper.assertTrue(handler.drain(1000, FluidAction.EXECUTE).getAmount() == 100 && !FLUID.has(stack), "Empty fluid was not removed");
        helper.assertTrue(stack.getTag().getString("unrelated").equals("keep"), "Draining erased unrelated item data");
        stack.setCount(2);
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 100), FluidAction.EXECUTE) == 0, "Multi-item stack accepted a single-item transfer");
        stack.setCount(0);
        helper.assertTrue(handler.drain(100, FluidAction.EXECUTE).isEmpty(), "Empty stack produced fluid");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void strictFluidRulesPreserveEquivalentFuelConversion(GameTestHelper helper) {
        var stack = BUILDER_ITEM.toStack();
        var handler = new StrictFluidHandlerItemStack(FLUID, stack, 1000, fluid -> fluid == Fluids.WATER || fluid == Fluids.LAVA);
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 200), FluidAction.EXECUTE) == 200, "Valid fuel rejected");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 300), FluidAction.SIMULATE) == 300 && handler.getFluid().getAmount() == 200,
            "Equivalent fuel simulation mutated state");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 300), FluidAction.EXECUTE) == 300 && handler.getFluid().getFluid() == Fluids.WATER,
            "Equivalent fuel changed stored fluid identity");
        helper.assertTrue(handler.getFluid().getAmount() == 500, "Equivalent fuel conversion lost volume");
        var waterOnly = new StrictFluidHandlerItemStack(FLUID, stack, 1000, Fluids.WATER);
        helper.assertTrue(!waterOnly.isFluidValid(0, new FluidStack(Fluids.LAVA, 1)) && waterOnly.fill(new FluidStack(Fluids.LAVA, 10), FluidAction.EXECUTE) == 0,
            "Disallowed fuel was accepted");
        helper.succeed();
    }
}
