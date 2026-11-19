package com.enderio.port.clientqa;

import com.enderio.core.common.blockentity.EnderBlockEntity;
import com.enderio.core.common.energy.ItemStackEnergy;
import com.enderio.enderio.api.io.IOMode;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorBankBlockEntity;
import com.enderio.enderio.content.machines.capacitor_bank.CapacitorTier;
import com.enderio.enderio.content.machines.solar_panel.SolarPanelTier;
import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIODataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class ClientBlockCarrySmoke {
    private enum Stage { TITLE, CONNECT, MINING, PICKUP, PLACEMENT, DONE }
    private static final String ATHENA_PACK = "mod/enderio:resourcepacks/athena";
    private static final String BASIC_BANK_SPRITE = "enderio:block/basic_capacitor_bank";
    private static final String CONNECTED_BASIC_BANK_SPRITE = "enderio:block/ctm/basic_capacitor_bank_horizontal";

    enum Scenario {
        SOLAR("solar", "collected_exactly_one_charged_panel", new BlockPos(24, 80, 24), new BlockPos(27, 80, 24),
            new Vec3(24.5, 80, 27.5), new Vec3(27.5, 80, 27.5),
            () -> EIOBlocks.SOLAR_PANELS.get(SolarPanelTier.VIBRANT).get(), stack -> {
                var soul = EIODataComponents.SOUL.get(stack);
                return EIODataComponents.ENERGY.getOrDefault(stack, 0) == 23789
                    && soul != null && soul.entityType() == EntityType.PHANTOM;
            }),
        CAPACITOR_BANK("capacitor-bank", "collected_exactly_one_charged_bank", new BlockPos(1040, 80, 8),
            new BlockPos(1040, 80, 8), new Vec3(1040.5, 80, 11.5), new Vec3(1040.5, 80, 10.5),
            () -> EIOBlocks.CAPACITOR_BANKS.get(CapacitorTier.BASIC).get(), stack -> {
                var io = EIODataComponents.IO_CONFIG.get(stack);
                return ItemStackEnergy.getEnergyStored(stack) == 500000 && io != null
                    && io.getMode(Direction.NORTH) == IOMode.DISABLED && io.getMode(Direction.SOUTH) == IOMode.PULL;
            }),
        CRAFTER("crafter", "collected_the_crafter_with_charge_and_inventory", new BlockPos(3078, 80, 8), new BlockPos(3078, 80, 13),
            new Vec3(3078.5, 80, 11.5), new Vec3(3078.5, 80, 15.5), EIOBlocks.CRAFTER::get, stack -> {
                var contents = EnderBlockEntity.ITEM_INVENTORY.get(stack);
                return EIODataComponents.ENERGY.getOrDefault(stack, 0) == 3200 && contents != null && contents.getInt("Size") == 21;
            });

        private final String name;
        private final String pickupCheck;
        private final BlockPos source;
        private final BlockPos destination;
        private final Vec3 miningPosition;
        private final Vec3 placingPosition;
        private final Supplier<Block> block;
        private final Predicate<ItemStack> carriedState;

        Scenario(String name, String pickupCheck, BlockPos source, BlockPos destination, Vec3 miningPosition,
                 Vec3 placingPosition, Supplier<Block> block, Predicate<ItemStack> carriedState) {
            this.name = name;
            this.pickupCheck = pickupCheck;
            this.source = source;
            this.destination = destination;
            this.miningPosition = miningPosition;
            this.placingPosition = placingPosition;
            this.block = block;
            this.carriedState = carriedState;
        }
    }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final String address = System.getProperty("enderio.clientqa.server");
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
    private final LinkedHashMap<String, Object> captures = new LinkedHashMap<>();
    private final LinkedHashMap<String, List<String>> bankFaces = new LinkedHashMap<>();
    private List<String> bankOverlaySprites = List.of();
    private final Scenario scenario;
    private Stage stage = Stage.TITLE;
    private long titleReady;
    private int miningTick;

    ClientBlockCarrySmoke(Scenario scenario) {
        this.scenario = scenario;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen) throw new AssertionError("Disconnected during " + stage);
            if (System.nanoTime() - started > 165_000_000_000L) throw new AssertionError("Timed out in " + stage);
            if (stage == Stage.TITLE) {
                if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                if (titleReady == 0) titleReady = System.nanoTime();
                if (System.nanoTime() - titleReady < 2_000_000_000L) return;
                ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
                    new ServerData("Ender IO local " + scenario.name + " item test", address, true), false);
                stage = Stage.CONNECT;
                return;
            }
            if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) return;
            Block block = scenario.block.get();
            switch (stage) {
                case CONNECT -> {
                    if (minecraft.getToasts().getToast(SystemToast.class, SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                    if (minecraft.player.distanceToSqr(scenario.miningPosition) > 0.1
                        || !minecraft.player.getMainHandItem().is(Items.DIAMOND_PICKAXE)
                        || !minecraft.level.getBlockState(scenario.source).is(block)) return;
                    if (scenario == Scenario.CAPACITOR_BANK
                        && !(minecraft.level.getBlockEntity(scenario.source.west()) instanceof CapacitorBankBlockEntity)) return;
                    var connection = minecraft.getConnection().getConnection();
                    check("real_dedicated_survival_connection", !minecraft.player.isCreative() && !minecraft.player.isSpectator()
                        && minecraft.getSingleplayerServer() == null && !connection.isMemoryConnection()
                        && connection.getRemoteAddress() instanceof InetSocketAddress);
                    if (scenario == Scenario.CAPACITOR_BANK) recordBankFace("before_mining");
                    check("normal_survival_mining_action", minecraft.gameMode.startDestroyBlock(scenario.source, Direction.SOUTH));
                    miningTick = minecraft.player.tickCount;
                    stage = Stage.MINING;
                }
                case MINING -> {
                    if (!minecraft.level.isEmptyBlock(scenario.source)) {
                        if (miningTick != minecraft.player.tickCount) {
                            miningTick = minecraft.player.tickCount;
                            minecraft.gameMode.continueDestroyBlock(scenario.source, Direction.SOUTH);
                        }
                        return;
                    }
                    if (scenario == Scenario.CAPACITOR_BANK) recordBankFace("after_mining");
                    signal("mined");
                    stage = Stage.PICKUP;
                }
                case PICKUP -> {
                    if (minecraft.player.distanceToSqr(scenario.placingPosition) > 0.1) return;
                    var inventory = minecraft.player.getInventory();
                    int slot = 0;
                    while (slot < 9 && !inventory.getItem(slot).is(block.asItem())) {
                        slot++;
                    }
                    if (slot == 9) return;
                    var pickedUp = inventory.getItem(slot);
                    check(scenario.pickupCheck, pickedUp.getCount() == 1 && scenario.carriedState.test(pickedUp));
                    inventory.selected = slot;
                    minecraft.gameMode.useItemOn(minecraft.player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atBottomCenterOf(scenario.destination), Direction.UP, scenario.destination.below(), false));
                    signal("placement");
                    stage = Stage.PLACEMENT;
                }
                case PLACEMENT -> {
                    if (!Files.exists(output.resolve("server-verified")) || !minecraft.level.getBlockState(scenario.destination).is(block)
                        || !minecraft.player.getMainHandItem().isEmpty()) return;
                    check("placement_consumes_the_picked_up_stack", minecraft.player.getMainHandItem().isEmpty()
                        && minecraft.level.getBlockState(scenario.destination).is(block)
                        && (scenario.source.equals(scenario.destination) || minecraft.level.isEmptyBlock(scenario.source)));
                    if (scenario == Scenario.CAPACITOR_BANK) {
                        if (!(minecraft.level.getBlockEntity(scenario.destination) instanceof CapacitorBankBlockEntity bank)
                            || bank.getIOMode(Direction.NORTH) != IOMode.DISABLED
                            || bank.getIOMode(Direction.SOUTH) != IOMode.PULL) return;
                        check("replanted_bank_synchronizes_both_faces", true);
                        verifyBankTextures();
                        verifyBankOverlay(bank);
                    }
                    String captureName = scenario.name + "-replaced";
                    var capture = ClientSmoke.capture(captureName + ".png");
                    captures.put(captureName, capture);
                    check("silent_hidden_replacement", Boolean.TRUE.equals(capture.get("passed")));
                    finish(null);
                }
                default -> { }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private void recordBankFace(String stage) {
        var pos = scenario.source.west();
        var state = minecraft.level.getBlockState(pos);
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var bank = (CapacitorBankBlockEntity) minecraft.level.getBlockEntity(pos);
        var data = model.getModelData(minecraft.level, pos, state, bank.getModelData());
        var sprites = model.getQuads(state, Direction.SOUTH, RandomSource.create(0), data, RenderType.solid()).stream()
            .map(quad -> quad.getSprite().contents().name().toString()).sorted().toList();
        bankFaces.put(stage, sprites);
    }

    private void verifyBankTextures() {
        recordBankFace("after_replant");
        boolean loaded = ModList.get().isLoaded("athena");
        check("athena_pack_available_with_its_dependency", loaded == minecraft.getResourcePackRepository().getAvailableIds().contains(ATHENA_PACK));
        var path = ResourceLocation.fromNamespaceAndPath("enderio", "blockstates/basic_capacitor_bank.json");
        var resource = minecraft.getResourceManager().getResource(path).orElseThrow();
        check("expected_capacitor_blockstate_pack_active", loaded == resource.sourcePackId().equals(ATHENA_PACK));
        var connected = bankFaces.get("before_mining");
        var isolated = bankFaces.get("after_mining");
        var replanted = bankFaces.get("after_replant");
        if (loaded) {
            check("athena_uses_two_connected_quads_toward_the_neighbor", connected.equals(List.of(
                BASIC_BANK_SPRITE, BASIC_BANK_SPRITE, CONNECTED_BASIC_BANK_SPRITE, CONNECTED_BASIC_BANK_SPRITE)));
            check("same_tier_seam_updates_on_break_and_replant", isolated.equals(List.of(
                BASIC_BANK_SPRITE, BASIC_BANK_SPRITE, BASIC_BANK_SPRITE, BASIC_BANK_SPRITE)) && connected.equals(replanted));
        } else {
            check("vanilla_bank_texture_works_without_athena", connected.equals(List.of(BASIC_BANK_SPRITE))
                && connected.equals(isolated) && connected.equals(replanted));
        }
    }

    private void verifyBankOverlay(CapacitorBankBlockEntity bank) {
        var state = bank.getBlockState();
        var model = minecraft.getBlockRenderer().getBlockModel(state);
        var data = model.getModelData(minecraft.level, bank.getBlockPos(), state, bank.getModelData());
        check("io_overlay_still_has_a_cutout_render_layer", model.getRenderTypes(state, RandomSource.create(0), data).contains(RenderType.cutout()));
        bankOverlaySprites = model.getQuads(state, null, RandomSource.create(0), data, RenderType.cutout()).stream()
            .map(quad -> quad.getSprite().contents().name().toString()).sorted().toList();
        check("both_configured_io_sprites_are_baked", bankOverlaySprites.equals(List.of("enderio:block/overlay/disabled", "enderio:block/overlay/pull")));
    }

    private void signal(String name) throws Exception {
        Path directory = output.resolve("signals");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve(name + ".json"), "{}");
    }

    private void check(String name, boolean passed) {
        checks.put(name, passed);
        if (!passed) throw new AssertionError(name);
    }

    private void finish(Throwable failure) {
        stage = Stage.DONE;
        var result = new LinkedHashMap<String, Object>();
        result.put("passed", failure == null);
        result.put("checks", checks);
        result.put("captures", captures);
        if (!bankFaces.isEmpty()) result.put("bankFaceSprites", bankFaces);
        if (!bankOverlaySprites.isEmpty()) result.put("bankOverlaySprites", bankOverlaySprites);
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_" + scenario.name.replace('-', '_') + "_carry", result);
    }
}
