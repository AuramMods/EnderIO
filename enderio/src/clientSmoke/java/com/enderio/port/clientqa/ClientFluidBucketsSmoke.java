package com.enderio.port.clientqa;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.id;
import static com.enderio.port.clientqa.ClientAlloyProgressionSmoke.item;

final class ClientFluidBucketsSmoke {
    private enum Stage { TITLE, JOIN, READY, HELD, REJECT_FLOW, SOURCE, RESULT, STORED, FINISH, DONE }

    private record Sample(String fluid, int light) {
        String bucket() { return fluid + "_bucket"; }
    }

    private static final List<Sample> SAMPLES = List.of(
        new Sample("nutrient_distillation", 0), new Sample("dew_of_the_void", 3), new Sample("vapor_of_levity", 0),
        new Sample("hootch", 0), new Sample("rocket_fuel", 0), new Sample("fire_water", 5), new Sample("xp_juice", 10),
        new Sample("liquid_sunshine", 15), new Sample("liquid_darkness", 0), new Sample("cloud_seed", 0),
        new Sample("cloud_seed_concentrated", 0));

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Path output = Path.of(System.getProperty("enderio.clientqa.output"));
    private final long started = System.nanoTime();
    private final boolean collect;
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private Stage stage = Stage.TITLE;
    private long titleAt;
    private int settleBy = -1;
    private int index;

    ClientFluidBucketsSmoke(boolean collect) {
        this.collect = collect;
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (minecraft.screen instanceof DisconnectedScreen || System.nanoTime() - started > 190_000_000_000L) {
                throw new AssertionError("Fluid bucket probe stopped at " + stage + " for fixture " + index);
            }
            if (stage == Stage.TITLE) {
                connect();
                return;
            }
            var player = minecraft.player;
            if (player == null || minecraft.level == null) return;
            if (stage == Stage.JOIN) {
                if (minecraft.screen != null || minecraft.getToasts().getToast(SystemToast.class,
                    SystemToast.SystemToastIds.UNSECURE_SERVER_WARNING) != null) return;
                check("native_tcp_survival", !player.isCreative() && !player.isSpectator()
                    && !minecraft.getConnection().getConnection().isMemoryConnection());
                signal("connected");
                stage = Stage.READY;
                return;
            }
            if (stage == Stage.FINISH) {
                if (ready("finish")) finish(null);
                return;
            }
            var sample = SAMPLES.get(index);
            var source = new BlockPos(4460 + index * 18, 80, 8);
            switch (stage) {
                case READY -> {
                    if (!settled("ready", sample, collect ? source.east() : source)) return;
                    if (!expected(player.getInventory().getItem(storedSlot()), sample, !collect)) return;
                    player.getInventory().selected = handSlot();
                    if (storedSlot() != handSlot()) swap();
                    stage = Stage.HELD;
                }
                case HELD -> {
                    if (!expected(player.getMainHandItem(), sample, !collect)) return;
                    if (collect) {
                        stage = Stage.REJECT_FLOW;
                    } else {
                        verifyBucket(sample, player.getMainHandItem());
                        stage = Stage.SOURCE;
                    }
                }
                case REJECT_FLOW -> {
                    var flow = source.east();
                    if (!matchesFluid(flow, sample, false) || !looksAt(flow.below(), false)) return;
                    check(sample.fluid() + "_empty_bucket_rejects_flowing_liquid",
                        !minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction()
                            && player.getMainHandItem().is(Items.BUCKET));
                    signal(sample.fluid() + "-rejected-flow");
                    stage = Stage.SOURCE;
                }
                case SOURCE -> {
                    if (collect) {
                        if (!settled("source", sample, source) || !matchesFluid(source, sample, true) || !looksAt(source, true)) return;
                    } else if (!looksAt(source.below(), false)) {
                        return;
                    }
                    check(sample.fluid() + (collect ? "_uses_empty_bucket_on_saved_source" : "_pours_stock_bucket"),
                        minecraft.gameMode.useItem(player, InteractionHand.MAIN_HAND).consumesAction());
                    stage = Stage.RESULT;
                }
                case RESULT -> {
                    if (!expected(player.getMainHandItem(), sample, collect)) return;
                    if (collect ? !minecraft.level.isEmptyBlock(source) : !matchesFluid(source, sample, true)) return;
                    if (storedSlot() != handSlot()) swap();
                    stage = Stage.STORED;
                }
                case STORED -> {
                    if (!expected(player.getInventory().getItem(storedSlot()), sample, collect)) return;
                    if (collect) {
                        if (!minecraft.level.isEmptyBlock(source.east())) return;
                        verifyBucket(sample, player.getInventory().getItem(storedSlot()));
                    } else {
                        if (!matchesFluid(source.east(), sample, false)
                            || minecraft.level.getBrightness(LightLayer.BLOCK, source) < sample.light()) return;
                        verifyWorldSprites(sample, source);
                        capture(sample.fluid() + "-placed");
                    }
                    signal(sample.fluid() + "-complete");
                    stage = ++index == SAMPLES.size() ? Stage.FINISH : Stage.READY;
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean looksAt(BlockPos pos, boolean includeFluids) {
        var player = minecraft.player;
        return player.pick(player.getBlockReach(), 1.0f, includeFluids) instanceof BlockHitResult hit
            && hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private boolean matchesFluid(BlockPos pos, Sample sample, boolean source) {
        var state = minecraft.level.getFluidState(pos);
        var name = id("fluid_" + sample.fluid() + (source ? "_still" : "_flowing"));
        return state.isSource() == source && name.equals(ForgeRegistries.FLUIDS.getKey(state.getType()));
    }

    private void verifyWorldSprites(Sample sample, BlockPos pos) {
        var state = minecraft.level.getBlockState(pos);
        var fluid = minecraft.level.getFluidState(pos);
        var render = IClientFluidTypeExtensions.of(fluid);
        var still = render.getStillTexture(fluid, minecraft.level, pos);
        var flowing = render.getFlowingTexture(fluid, minecraft.level, pos);
        var atlas = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        check(sample.fluid() + "_uses_stock_still_and_flowing_atlas_sprites",
            id("block/" + sample.fluid() + "_still").equals(still)
                && id("block/" + sample.fluid() + "_flowing").equals(flowing)
                && atlas.apply(still).contents().name().equals(still) && atlas.apply(flowing).contents().name().equals(flowing));
        check(sample.fluid() + "_emits_stock_block_light",
            state.getLightEmission(minecraft.level, pos) == sample.light()
                && minecraft.level.getBrightness(LightLayer.BLOCK, pos) == sample.light());
    }

    private void verifyBucket(Sample sample, ItemStack stack) {
        var fluid = FluidUtil.getFluidContained(stack);
        var model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0);
        check(sample.fluid() + "_bucket_has_1000_mb_of_the_exact_source_and_a_baked_model",
            fluid.isPresent() && fluid.get().getAmount() == 1_000
                && id("fluid_" + sample.fluid() + "_still").equals(ForgeRegistries.FLUIDS.getKey(fluid.get().getFluid()))
                && model != minecraft.getModelManager().getMissingModel());
    }

    private static boolean expected(ItemStack stack, Sample sample, boolean filled) {
        return stack.getCount() == 1 && stack.is(filled ? item(sample.bucket()) : Items.BUCKET);
    }

    private int storedSlot() {
        return index < 8 ? index : index + 1;
    }

    private int handSlot() {
        return Math.min(index, 8);
    }

    private void swap() {
        var player = minecraft.player;
        minecraft.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, storedSlot(), handSlot(),
            ClickType.SWAP, player);
    }

    private boolean settled(String marker, Sample sample, BlockPos pos) {
        var player = minecraft.player;
        var stand = Vec3.atBottomCenterOf(pos.south().above());
        if (!ready(sample.fluid() + "-" + marker) || minecraft.screen != null || player.distanceToSqr(stand) > 0.12) {
            settleBy = -1;
            return false;
        }
        if (settleBy < 0) settleBy = player.tickCount + 6;
        if (player.tickCount < settleBy) return false;
        settleBy = -1;
        return true;
    }

    private void connect() {
        if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
        if (titleAt == 0) titleAt = System.nanoTime();
        if (System.nanoTime() - titleAt < 2_000_000_000L) return;
        String address = System.getProperty("enderio.clientqa.server");
        ConnectScreen.startConnecting(new TitleScreen(), minecraft, ServerAddress.parseString(address),
            new ServerData("Ender IO Fluid Buckets", address, true), false);
        stage = Stage.JOIN;
    }

    private boolean ready(String marker) {
        return Files.exists(output.resolve("server-" + marker));
    }

    private void signal(String name) throws Exception {
        var signals = output.resolve("signals");
        Files.createDirectories(signals);
        Files.writeString(signals.resolve(name + ".json"), "{}");
    }

    private void capture(String name) throws Exception {
        var frame = ClientSmoke.capture(name + ".png");
        captures.put(name, frame);
        check(name + "_renders_hidden_and_muted", Boolean.TRUE.equals(frame.get("passed")));
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
        if (failure != null) result.put("error", failure.toString());
        ClientSmoke.complete("client_fluid_buckets", result);
    }
}
