package com.enderio.core.gametest;

import com.enderio.core.common.menu.MenuRecipeTransfer;
import com.enderio.enderio.foundation.network.packets.ServerboundTransferItemsPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundUpdateCrafterTemplatePacket;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class MenuRecipeTransferTests {
    private static final class Menu extends AbstractContainerMenu {
        final SimpleContainer input = new SimpleContainer(1);
        boolean valid = true;
        boolean accepts = true;
        boolean pickup = true;
        int capacity = 4;
        Menu(Player player) {
            super(null, 37);
            addSlot(new Slot(input, 0, 0, 0) {
                public boolean mayPlace(ItemStack stack) { return accepts; }
                public int getMaxStackSize() { return capacity; }
            });
            for (int i = 0; i < 3; i++) addSlot(new Slot(player.getInventory(), i, 0, 0) {
                public boolean mayPickup(Player player) { return pickup; }
            });
        }
        public boolean stillValid(Player player) { return valid; }
        public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
    }

    private static ServerPlayer player(GameTestHelper helper) {
        return new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(UUID.randomUUID(), "TransferFixture"));
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void transferFillsEmptyInputsAndConservesItemsAcrossSources(GameTestHelper helper) {
        var player = player(helper);
        var menu = new Menu(player);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 2));
        player.getInventory().setItem(1, new ItemStack(Items.IRON_INGOT, 8));
        var ingredients = List.of(Ingredient.of(Items.IRON_INGOT));
        MenuRecipeTransfer.transfer(player, menu, ingredients, 0, 1, false);
        helper.assertTrue(menu.input.getItem(0).getCount() == 1 && player.getInventory().getItem(0).getCount() == 1,
            "Single transfer failed to fill an empty input or changed counts");
        MenuRecipeTransfer.transfer(player, menu, ingredients, 0, 1, true);
        helper.assertTrue(menu.input.getItem(0).getCount() == 4 && player.getInventory().getItem(0).isEmpty()
            && player.getInventory().getItem(1).getCount() == 6, "Maximum transfer ignored the slot limit or lost/duplicated items");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void transferRespectsPermissionsInvalidRangesAndNbtIdentity(GameTestHelper helper) {
        var player = player(helper);
        var menu = new Menu(player);
        var supply = new ItemStack(Items.IRON_INGOT, 8);
        supply.getOrCreateTag().putString("variant", "source");
        player.getInventory().setItem(0, supply);
        var ingredients = List.of(Ingredient.of(Items.IRON_INGOT));
        menu.accepts = false;
        MenuRecipeTransfer.transfer(player, menu, ingredients, 0, 1, true);
        menu.accepts = true;
        menu.pickup = false;
        MenuRecipeTransfer.transfer(player, menu, ingredients, 0, 1, true);
        menu.pickup = true;
        menu.valid = false;
        MenuRecipeTransfer.transfer(player, menu, ingredients, 0, 1, true);
        menu.valid = true;
        menu.capacity = 0;
        MenuRecipeTransfer.transfer(player, menu, ingredients, 0, 1, false);
        menu.capacity = 4;
        MenuRecipeTransfer.transfer(player, menu, ingredients, -1, 0, true);
        MenuRecipeTransfer.transfer(player, menu, List.of(), 0, 1, true);
        helper.assertTrue(menu.input.isEmpty() && supply.getCount() == 8, "Invalid or forbidden transfer changed inventory");
        var different = new ItemStack(Items.IRON_INGOT, 1);
        different.getOrCreateTag().putString("variant", "target");
        menu.input.setItem(0, different);
        MenuRecipeTransfer.transfer(player, menu, ingredients, 0, 1, true);
        helper.assertTrue(menu.input.getItem(0).getCount() == 1 && supply.getCount() == 8, "Transfer merged different NBT variants");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void actualRecipePacketsPreserveMenuIdentityAndBoundInputCounts(GameTestHelper helper) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var packet = new ServerboundTransferItemsPacket(37, List.of(Ingredient.of(Items.IRON_INGOT)), 0, 1, true);
            ServerboundTransferItemsPacket.STREAM_CODEC.encode(buffer, packet);
            var decoded = ServerboundTransferItemsPacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(decoded.containerId() == 37 && decoded.startslot() == 0 && decoded.endslot() == 1
                && decoded.maxTransfer() && decoded.stacks().get(0).test(new ItemStack(Items.IRON_INGOT)), "Recipe transfer packet changed");
            var ghost = new ServerboundUpdateCrafterTemplatePacket(42, List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND)));
            ServerboundUpdateCrafterTemplatePacket.STREAM_CODEC.encode(buffer, ghost);
            var copy = ServerboundUpdateCrafterTemplatePacket.STREAM_CODEC.decode(buffer);
            helper.assertTrue(copy.containerId() == 42 && copy.recipeInputs().get(0).isEmpty()
                && copy.recipeInputs().get(1).is(Items.DIAMOND), "Crafter packet lost menu identity/empty slots");
            buffer.clear();
            buffer.writeInt(42);
            buffer.writeVarInt(10);
            boolean rejected = false;
            try { ServerboundUpdateCrafterTemplatePacket.STREAM_CODEC.decode(buffer); }
            catch (DecoderException expected) { rejected = true; }
            helper.assertTrue(rejected, "Oversized crafter template was decoded");
        } finally { buffer.release(); }
        helper.succeed();
    }
}
