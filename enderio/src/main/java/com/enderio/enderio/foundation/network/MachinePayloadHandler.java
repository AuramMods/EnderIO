package com.enderio.enderio.foundation.network;

import com.enderio.enderio.api.io.IOConfigurable;
import com.enderio.enderio.content.enderface.EnderfaceBlockEntity;
import com.enderio.enderio.content.storage.crafter.CrafterMenu;
import com.enderio.core.common.menu.MenuRecipeTransfer;
import com.enderio.enderio.content.machines.vat.VatMenu;
import com.enderio.enderio.content.storage.fluid_tank.FluidTankMenu;
import com.enderio.enderio.content.machines.obelisks.weather.WeatherObeliskMenu;
import net.minecraft.world.InteractionHand;
import com.enderio.enderio.foundation.network.packets.ClientboundFarmStationSoulPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundPoweredSpawnerSoulPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundSolarSoulPacket;
import com.enderio.enderio.foundation.network.packets.ClientboundSoulEngineSoulPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundCycleIOConfigPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundEnderfaceInteractPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundTransferItemsPacket;
import com.enderio.enderio.foundation.network.packets.ServerboundUpdateCrafterTemplatePacket;
import com.enderio.enderio.foundation.souldata.EngineSoul;
import com.enderio.enderio.foundation.souldata.FarmSoul;
import com.enderio.enderio.foundation.souldata.SolarSoul;
import com.enderio.enderio.foundation.souldata.SpawnerSoul;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.player.Player;

public class MachinePayloadHandler {
    public static class Client {
        private static final Client INSTANCE = new Client();

        public static Client getInstance() {
            return INSTANCE;
        }

        public void handlePoweredSpawnerSoul(ClientboundPoweredSpawnerSoulPacket packet, Player player) {
            SpawnerSoul.SPAWNER.map = packet.map();
        }

        public void handleSoulEngineSoul(ClientboundSoulEngineSoulPacket packet, Player player) {
            EngineSoul.ENGINE.map = packet.map();
        }

        public void handleFarmingStationSoul(ClientboundFarmStationSoulPacket packet, Player player) {
            FarmSoul.FARM.map = packet.map();
        }

        public void handleSolarSoul(ClientboundSolarSoulPacket packet, Player player) {
            SolarSoul.SOLAR.map = packet.map();
        }
    }

    public static class Server {
        private static final Server INSTANCE = new Server();

        public static Server getInstance() {
            return INSTANCE;
        }

        public void updateCrafterTemplate(ServerboundUpdateCrafterTemplatePacket packet, Player player) {
            if (!player.isSpectator() && player.containerMenu instanceof CrafterMenu crafterMenu
                    && crafterMenu.containerId == packet.containerId() && crafterMenu.stillValid(player)
                    && packet.recipeInputs().size() == CrafterMenu.INPUT_COUNT) {
                for (int i = 0; i < packet.recipeInputs().size(); i++) {
                    crafterMenu.slots.get(CrafterMenu.INPUTS_INDEX + i).set(packet.recipeInputs().get(i).copyWithCount(1));
                }
            }
        }

        public void handleCycleIOConfigPacket(ServerboundCycleIOConfigPacket packet, Player player) {
            var level = player.level();
            if (player.isSpectator() || !level.hasChunkAt(packet.pos()) || !player.canReach(packet.pos(), 1)
                    || !level.mayInteract(player, packet.pos())) return;
            BlockEntity be = level.getBlockEntity(packet.pos());

            if (be instanceof IOConfigurable ioConfigurable) {
                ioConfigurable.cycleIOMode(packet.side());
            }
        }

        public void handleEnderfaceInteract(ServerboundEnderfaceInteractPacket packet, Player player) {
            var pos = packet.getHitResult().getBlockPos();
            var level = player.level();
            if (!player.isSpectator() && level.hasChunkAt(pos) && level.mayInteract(player, pos)
                    && EnderfaceBlockEntity.canPlayerInteractWithBlock(player, level, pos)) {
                var state = level.getBlockState(pos);
                state.use(level, player, InteractionHand.MAIN_HAND, packet.getHitResult());
            }
        }

        public void handleTransferItems(ServerboundTransferItemsPacket packet, Player player) {
            AbstractContainerMenu menu = player.containerMenu;
            if (menu.containerId != packet.containerId()) return;
            int first, end;
            if (menu instanceof VatMenu) {
                first = VatMenu.INPUTS_INDEX;
                end = VatMenu.LAST_INDEX + 1;
            } else if (menu instanceof WeatherObeliskMenu) {
                first = WeatherObeliskMenu.INPUTS_INDEX;
                end = WeatherObeliskMenu.LAST_INDEX + 1;
            } else if (menu instanceof FluidTankMenu) {
                first = 0;
                end = FluidTankMenu.MACHINE_SLOT_COUNT;
            } else {
                return;
            }
            if (packet.startslot() != first || packet.endslot() != end) return;
            MenuRecipeTransfer.transfer(player, menu, packet.stacks(), first, end, packet.maxTransfer());
        }
    }
}
