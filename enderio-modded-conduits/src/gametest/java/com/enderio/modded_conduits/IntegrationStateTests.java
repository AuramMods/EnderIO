package com.enderio.modded_conduits;

import appeng.api.networking.IManagedGridNode;
import com.direwolf20.laserio.common.items.filters.BaseFilter;
import com.direwolf20.laserio.common.items.filters.FilterBasic;
import com.direwolf20.laserio.setup.Registration;
import com.enderio.modded_conduits.common.modules.appeng.ConduitInWorldGridNodeHost;
import com.enderio.modded_conduits.common.modules.appeng.MEConduit;
import com.enderio.modded_conduits.common.modules.appeng.MEConduitNodeData;
import com.enderio.modded_conduits.common.modules.mekanism.MekanismModule;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalHandlerAdapter;
import com.enderio.modded_conduits.common.modules.mekanism.chemical.ChemicalResource;
import com.enderio.modded_conduits.common.modules.refinedstorage.RSConduitNodeData;
import mekanism.api.chemical.ChemicalType;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.infuse.InfusionStack;
import mekanism.api.chemical.pigment.PigmentStack;
import mekanism.api.chemical.slurry.SlurryStack;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registries.MekanismBlocks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;



@GameTestHolder("enderio_modded_conduits_tests")
@PrefixGameTestTemplate(false)
public class IntegrationStateTests {
    private static void fail(String message) { throw new net.minecraft.gametest.framework.GameTestAssertException(message); }
    private static void assertTrue(boolean value) { if (!value) fail("Expected true"); }
    private static void assertFalse(boolean value) { if (value) fail("Expected false"); }
    private static void assertNull(Object value) { if (value != null) fail("Expected null: " + value); }
    private static void assertNotNull(Object value) { if (value == null) fail("Unexpected null"); }
    private static void assertSame(Object expected, Object actual) { if (expected != actual) fail("Expected the same object"); }
    private static void assertNotSame(Object expected, Object actual) { if (expected == actual) fail("Unexpected shared object"); }
    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) fail("Expected " + expected + ", got " + actual);
    }

    private static CompoundTag savedNode() {
        var node = new CompoundTag();
        var grid = new CompoundTag();
        grid.putInt("player", 48);
        grid.putLong("grid", 1234567890123L);
        node.put("conduit", grid);
        var root = new CompoundTag();
        root.put("main_node", node);
        return root;
    }

    private static MEConduitNodeData read(CompoundTag tag) {
        return MEConduitNodeData.CODEC.codec().parse(NbtOps.INSTANCE, tag).getOrThrow(false, fail -> fail(fail));
    }

    private static CompoundTag write(MEConduitNodeData data) {
        return (CompoundTag) MEConduitNodeData.CODEC.codec().encodeStart(NbtOps.INSTANCE, data)
            .getOrThrow(false, fail -> fail(fail));
    }

    private static IManagedGridNode managed(CompoundTag persisted, AtomicReference<CompoundTag> loaded) {
        return (IManagedGridNode) Proxy.newProxyInstance(IManagedGridNode.class.getClassLoader(),
            new Class<?>[] { IManagedGridNode.class }, (proxy, method, args) -> {
                if (method.getName().equals("saveToNBT")) { ((CompoundTag) args[0]).merge(persisted.copy()); return null; }
                if (method.getName().equals("loadFromNBT")) { loaded.set(((CompoundTag) args[0]).copy()); return null; }
                throw new AssertionError("Unexpected native grid method: " + method.getName());
            });
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void unloadedMeStateSurvivesRepeatedSerializationWithoutSharingNbt(GameTestHelper helper) {
        var source = savedNode();
        var expected = source.copy();
        var data = read(source);
        source.getCompound("main_node").getCompound("conduit").putInt("player", -1);
        assertEquals(expected, write(data));
        var encoded = write(data);
        encoded.getCompound("main_node").getCompound("conduit").putInt("player", 99);
        assertEquals(expected, write(read(write(data))));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void detachingMeNodeRetainsLatestNativeStateUntilNextInitialization(GameTestHelper helper) {
        var data = read(savedNode());
        var persisted = savedNode().getCompound("main_node");
        var loaded = new AtomicReference<CompoundTag>();
        data.setMainNode(managed(persisted, loaded), true);
        data.loadMainNode();
        assertEquals(persisted, loaded.get());
        persisted.getCompound("conduit").putInt("player", 75);
        data.clearMainNode();
        assertNull(data.getMainNode());
        var expected = new CompoundTag();
        expected.put("main_node", persisted.copy());
        assertEquals(expected, write(data));
        data.setMainNode(managed(new CompoundTag(), loaded), false);
        data.loadMainNode();
        assertEquals(persisted, loaded.get());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void legacyMeCopyAndMigrationRetainPendingStateIndependently(GameTestHelper helper) {
        var source = savedNode().getCompound("main_node");
        var original = new ConduitInWorldGridNodeHost(source);
        var copy = original.deepCopy();
        assertNotSame(original, copy);
        source.getCompound("conduit").putInt("player", -1);
        var expected = savedNode();
        assertEquals(expected, write((MEConduitNodeData) original.toNodeData()));
        assertEquals(expected, write((MEConduitNodeData) copy.toNodeData()));
        original.setMainNode(managed(new CompoundTag(), new AtomicReference<>()), true);
        original.clearMainNode();
        assertEquals(expected, write((MEConduitNodeData) copy.toNodeData()));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void nativeAeColorsRoundTripAndUnknownColorsAreRejected(GameTestHelper helper) {
        for (var color : appeng.api.util.AEColor.values()) {
            var tag = StringTag.valueOf(color.registryPrefix);
            assertSame(color, MEConduit.COLOR_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(false, fail -> fail(fail)));
            assertEquals(tag, MEConduit.COLOR_CODEC.encodeStart(NbtOps.INSTANCE, color).getOrThrow(false, fail -> fail(fail)));
        }
        assertTrue(MEConduit.COLOR_CODEC.parse(NbtOps.INSTANCE, StringTag.valueOf("unknown")).error().isPresent());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void rsPendingNativeStateSurvivesCopyAndLegacyEmptyStateLoads(GameTestHelper helper) {
        var source = savedNode();
        var expected = source.copy();
        var data = RSConduitNodeData.CODEC.codec().parse(NbtOps.INSTANCE, source).getOrThrow(false, fail -> fail(fail));
        source.getCompound("main_node").getCompound("conduit").putInt("player", -1);
        assertEquals(expected, RSConduitNodeData.CODEC.codec().encodeStart(NbtOps.INSTANCE, data).getOrThrow(false, fail -> fail(fail)));
        assertFalse(data.isAccessible());
        assertNull(data.getProxy());
        assertTrue(RSConduitNodeData.CODEC.codec().parse(NbtOps.INSTANCE, new CompoundTag()).result().isPresent());
        var malformed = new CompoundTag();
        malformed.putString("main_node", "invalid");
        assertTrue(RSConduitNodeData.CODEC.codec().parse(NbtOps.INSTANCE, malformed).error().isPresent());
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void rsNativeFactoryRetainsOwnerAndNodeSidesAreSnapshotBased(GameTestHelper helper) {
        var level = helper.getLevel();
        var node = new com.enderio.modded_conduits.common.modules.refinedstorage.ConduitRSNode(level, helper.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)));
        var owner = java.util.UUID.randomUUID();
        node.setOwner(owner);
        var sides = java.util.EnumSet.of(net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.UP);
        node.attach(sides);
        sides.clear();
        assertTrue(node.isActive());
        assertTrue(node.canConduct(net.minecraft.core.Direction.EAST));
        assertTrue(node.canReceive(net.minecraft.core.Direction.UP));
        assertFalse(node.canReceive(net.minecraft.core.Direction.WEST));
        assertEquals(0, node.getEnergyUsage());
        var factory = com.refinedmods.refinedstorage.apiimpl.API.instance().getNetworkNodeRegistry().get(node.getId());
        assertNotNull(factory);
        var restored = factory.create(node.write(new CompoundTag()), level, node.getPos());
        assertEquals(owner, restored.getOwner());
        assertFalse(restored.isActive());
        node.detach();
        assertFalse(node.isActive());
        for (var side : net.minecraft.core.Direction.values()) {
            assertFalse(node.canConduct(side));
            assertFalse(node.canReceive(side));
        }
        helper.succeed();
    }
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void laserIoBookRecipeMatchesAndProducesItsNamedGuide(GameTestHelper helper) {
        var recipe = helper.getLevel().getRecipeManager()
            .byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("laserio", "my_book_recipe_shapeless"))
            .orElseThrow(() -> new net.minecraft.gametest.framework.GameTestAssertException("LaserIO book recipe was not loaded"));
        var crafting = (net.minecraft.world.item.crafting.CraftingRecipe) recipe;
        var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override public net.minecraft.world.item.ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
                return net.minecraft.world.item.ItemStack.EMPTY;
            }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        };
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 2, 1);
        grid.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOOK));
        grid.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS
            .getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("laserio", "logic_chip"))));
        assertTrue(crafting.matches(grid, helper.getLevel()));
        var result = crafting.assemble(grid, helper.getLevel().registryAccess());
        assertEquals(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("patchouli", "guide_book"),
            net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(result.getItem()));
        assertEquals("laserio:laseriobook", result.getOrCreateTag().getString("patchouli:book"));
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void laserIoChemicalFilterReadsPersistedNativeTankSamples(GameTestHelper helper) {
        var filter = new ItemStack(Registration.Filter_Basic.get());
        var slots = FilterBasic.getInventory(filter);
        var samples = java.util.List.of(
            ChemicalResource.fromRegistry(ChemicalType.GAS, ResourceLocation.parse("mekanism:oxygen"), 1450),
            ChemicalResource.fromRegistry(ChemicalType.INFUSION, ResourceLocation.parse("mekanism:redstone"), 1450),
            ChemicalResource.fromRegistry(ChemicalType.PIGMENT, ResourceLocation.parse("mekanism:red"), 1450),
            ChemicalResource.fromRegistry(ChemicalType.SLURRY, ResourceLocation.parse("mekanism:dirty_iron"), 1450));
        for (int i = 0; i < samples.size(); i++) {
            var tank = new ItemStack(MekanismBlocks.BASIC_CHEMICAL_TANK.getBlock());
            var sample = samples.get(i);
            switch (sample.family()) {
                case GAS -> tank.getCapability(Capabilities.GAS_HANDLER).orElseThrow(IllegalStateException::new)
                    .setChemicalInTank(0, (GasStack) sample.nativeCopy());
                case INFUSION -> tank.getCapability(Capabilities.INFUSION_HANDLER).orElseThrow(IllegalStateException::new)
                    .setChemicalInTank(0, (InfusionStack) sample.nativeCopy());
                case PIGMENT -> tank.getCapability(Capabilities.PIGMENT_HANDLER).orElseThrow(IllegalStateException::new)
                    .setChemicalInTank(0, (PigmentStack) sample.nativeCopy());
                case SLURRY -> tank.getCapability(Capabilities.SLURRY_HANDLER).orElseThrow(IllegalStateException::new)
                    .setChemicalInTank(0, (SlurryStack) sample.nativeCopy());
            }
            var adapter = ChemicalHandlerAdapter.find(cap -> tank.getCapability(cap).orElse(null));
            helper.assertTrue(adapter != null && java.util.stream.IntStream.range(0, adapter.getChemicalTanks())
                .mapToObj(adapter::getChemicalInTank)
                .anyMatch(held -> ChemicalResource.isSameChemical(held, sample) && held.getAmount() == sample.getAmount()),
                "Native sample tank should contain " + sample.family());
            slots.setStackInSlot(i * 2 + 1, tank);
        }
        FilterBasic.setInventory(filter, slots);
        BaseFilter.setAllowList(filter, true);
        var loaded = ItemStack.of(filter.save(new CompoundTag()));
        var capability = loaded.getCapability(MekanismModule.Capabilities.CHEMICAL_FILTER).orElseThrow(
            () -> new net.minecraft.gametest.framework.GameTestAssertException("Basic Filter has no chemical capability"));
        for (var sample : samples) {
            var offered = sample.copyWithAmount(4321);
            var passed = capability.test(null, offered);
            helper.assertTrue(ChemicalResource.isSameChemical(offered, passed) && passed.getAmount() == 4321,
                "Persisted " + sample.family() + " sample should allow the full observed batch");
        }
        var hydrogen = ChemicalResource.fromRegistry(ChemicalType.GAS, ResourceLocation.parse("mekanism:hydrogen"), 2345);
        helper.assertTrue(capability.test(null, hydrogen).isEmpty(), "The gas absent from the allowlist should be rejected");
        BaseFilter.setAllowList(loaded, false);
        helper.assertTrue(capability.test(null, hydrogen).getAmount() == 2345, "Deny mode should keep the unmatched batch");
        helper.assertTrue(capability.test(null, samples.get(0)).isEmpty(), "Deny mode should reject the saved oxygen sample");
        helper.succeed();
    }

}
