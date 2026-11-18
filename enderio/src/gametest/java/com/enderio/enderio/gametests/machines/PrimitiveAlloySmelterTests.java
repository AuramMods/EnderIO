package com.enderio.enderio.gametests.machines;

import com.enderio.enderio.EnderIO;
import com.enderio.enderio.content.machines.alloy.PrimitiveAlloySmelterBlockEntity;
import com.enderio.enderio.gametests.util.EnderGameTestHelper;
import com.enderio.enderio.gametests.util.FixtureBuilder;
import com.enderio.enderio.gametests.util.GameplayTest;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIORecipes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import java.util.Arrays;

public class PrimitiveAlloySmelterTests {
    @GameTest(timeoutTicks = 240)
    public static void nativeFuelRemainderAndAlloyOutput(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1)
            .set(0, 0, 0, EIOBlocks.PRIMITIVE_ALLOY_SMELTER.get().defaultBlockState()));

        test.onGameTest(EnderGameTestHelper.class, helper -> {
            var machine = helper.getBlockEntity(0, 1, 0, PrimitiveAlloySmelterBlockEntity.class);
            var inv = machine.getInventory();
            helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(!machine.getCapability(ForgeCapabilities.ENERGY).isPresent(), "primitive machine has no FE capability");
                    var input = PrimitiveAlloySmelterBlockEntity.INPUTS.get(0);
                    helper.assertTrue(input.insertItem(inv, new ItemStack(Items.RAW_IRON), true).is(Items.RAW_IRON),
                        "vanilla smelting input rejected");
                    helper.assertTrue(input.insertItem(inv, new ItemStack(Items.GLASS), true).isEmpty(), "legacy glass input accepted");
                    helper.insertIntoContainer(0, 1, 0, Items.LAVA_BUCKET, 1);
                    helper.insertIntoContainer(0, 1, 0, Items.GLASS, 1);
                })
                .thenWaitUntil(() -> helper.assertContainerHasExactly(0, 1, 0, Items.BUCKET, 1))
                .thenWaitUntil(() -> helper.assertContainerHasExactly(0, 1, 0,
                    BuiltInRegistries.ITEM.get(EnderIO.rl("clear_glass")), 1))
                .thenExecute(() -> {
                    helper.assertContainerHasExactly(0, 1, 0, Items.GLASS, 0);
                    helper.assertContainerHasExactly(0, 1, 0, Items.BUCKET, 1);
                })
                .thenSucceed();
        });
    }

    @GameTest
    public static void legacyRecipeResourcesResolveIngredients(GameplayTest test) {
        test.registerGameTestTemplate(() -> FixtureBuilder.withSize(1, 1, 1));
        test.onGameTest(EnderGameTestHelper.class, helper -> helper.startSequence()
            .thenExecute(() -> {
                var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(EIORecipes.PRIMITIVE_ALLOY_SMELTING.type().get());
                helper.assertValueEqual(recipes.size(), 47, "number of shipped historical alloys");
                for (var recipe : recipes) {
                    for (var sized : recipe.inputs()) {
                        helper.assertTrue(Arrays.stream(sized.ingredient().getItems()).anyMatch(stack -> !stack.is(Items.BARRIER) && !stack.isEmpty()),
                            "resolved ingredient in " + recipe.getId());
                    }
                    helper.assertTrue(!recipe.output().isEmpty(), "resolved output in " + recipe.getId());
                }
            })
            .thenSucceed());
    }
}
