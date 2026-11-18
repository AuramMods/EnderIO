package porting.itemupgradeprobe;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

@Mod("port_item_upgrade_probe")
public class ItemUpgradeProbe {
    private final int[] moved = new int[4];
    private final int[] remaining = new int[4];
    private ServerLevel level;
    private int priorBatches;

    public ItemUpgradeProbe() {
        MinecraftForge.EVENT_BUS.addListener(this::register);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, this::onLevelTick);
    }

    private void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("port_extract_upgrades").requires(source -> source.hasPermission(4))
            .then(Commands.literal("seed")
                .then(Commands.argument("prior_batches", IntegerArgumentType.integer(0, 3))
                    .executes(context -> seed(context.getSource(), IntegerArgumentType.getInteger(context, "prior_batches")))))
            .then(Commands.literal("check").executes(context -> check(context.getSource()))));
    }

    private int seed(CommandSourceStack source, int batches) {
        var serverLevel = source.getLevel();
        for (int tier = 1; tier <= 4; tier++) {
            if (!barrel(serverLevel, 1214, tier).isEmpty() || count(barrel(serverLevel, 1217, tier)) != rate(tier) * batches) {
                source.sendFailure(Component.literal("Tier " + tier + " inventories differ before the next batch"));
                return 0;
            }
        }
        priorBatches = batches;
        Arrays.fill(moved, 0);
        Arrays.fill(remaining, 0);
        for (int tier = 1; tier <= 4; tier++) {
            var input = barrel(serverLevel, 1214, tier);
            input.setItem(0, new ItemStack(Items.DIAMOND, 64));
            input.setItem(1, new ItemStack(Items.DIAMOND, 64));
        }
        level = serverLevel;
        source.sendSuccess(() -> Component.literal("Extraction upgrade batches seeded"), false);
        return 1;
    }

    private void onLevelTick(TickEvent.LevelTickEvent event) {
        if (level == null || event.level != level || event.phase != TickEvent.Phase.END) return;
        for (int tier = 1; tier <= 4; tier++) {
            int index = tier - 1;
            if (moved[index] != 0) continue;
            int delta = count(barrel(level, 1217, tier)) - rate(tier) * priorBatches;
            if (delta <= 0) continue;
            var input = barrel(level, 1214, tier);
            moved[index] = delta;
            remaining[index] = count(input);
            // Pause each route after its first actual delivery so later ticks cannot hide its budget.
            input.clearContent();
            input.setChanged();
        }
    }

    private int check(CommandSourceStack source) {
        for (int tier = 1; tier <= 4; tier++) {
            int index = tier - 1;
            if (moved[index] != rate(tier) || moved[index] + remaining[index] != 128) {
                source.sendFailure(Component.literal("First transfer moved " + Arrays.toString(moved)
                    + " and left " + Arrays.toString(remaining) + " at the sources"));
                return 0;
            }
        }
        level = null;
        source.sendSuccess(() -> Component.literal("Native extraction upgrade rates verified: " + Arrays.toString(moved)), false);
        return 1;
    }

    private static int rate(int tier) {
        return 4 << tier;
    }

    private static BarrelBlockEntity barrel(ServerLevel level, int x, int tier) {
        return (BarrelBlockEntity) level.getBlockEntity(new BlockPos(x, 80, tier * 3 - 2));
    }

    private static int count(BarrelBlockEntity barrel) {
        int count = 0;
        for (int slot = 0; slot < barrel.getContainerSize(); slot++) {
            var stack = barrel.getItem(slot);
            if (stack.is(Items.DIAMOND)) count += stack.getCount();
        }
        return count;
    }
}
