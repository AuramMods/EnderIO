package com.enderio.port.clientqa;

import com.enderio.enderio.init.EIOBlocks;
import com.enderio.enderio.init.EIOCreativeTabs;
import com.enderio.enderio.init.EIOItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Exercises Forge's actual creative tab, page controls, icon and search. */
final class ClientCreativeTabSmoke {
    private enum Stage { TITLE, WORLD, SELECT, SHOW, RESULTS, ENERGY, DONE }

    private final Minecraft minecraft = Minecraft.getInstance();
    private final Map<String, Boolean> checks = new LinkedHashMap<>();
    private final Map<String, Object> captures = new LinkedHashMap<>();
    private final long started = System.nanoTime();
    private Stage stage = Stage.TITLE;
    private int frames;
    private int pageTurns;
    private CompletableFuture<Boolean> energy;

    ClientCreativeTabSmoke() {
        MinecraftForge.EVENT_BUS.addListener(this::render);
    }

    private void render(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stage == Stage.DONE) return;
        try {
            if (System.nanoTime() - started > 145_000_000_000L) throw new AssertionError("Creative tab stopped at " + stage);
            switch (stage) {
                case TITLE -> {
                    if (!(minecraft.screen instanceof TitleScreen) || minecraft.getOverlay() != null) return;
                    String world = System.getProperty("enderio.clientqa.worldName");
                    var settings = new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                        true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    stage = Stage.WORLD;
                    minecraft.createWorldOpenFlows().createFreshLevel(world, settings, new WorldOptions(205L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                            .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
                }
                case WORLD -> {
                    var player = minecraft.player;
                    if (player == null || minecraft.level == null || minecraft.screen != null) return;
                    check("native_creative_mode_in_the_integrated_world", player.isCreative() && minecraft.getSingleplayerServer() != null);
                    minecraft.setScreen(new CreativeModeInventoryScreen(player, minecraft.level.enabledFeatures(), false));
                    stage = Stage.SELECT;
                }
                case SELECT -> {
                    if (!(minecraft.screen instanceof CreativeModeInventoryScreen screen)) return;
                    var tab = EIOCreativeTabs.TAB.get();
                    if (!select(screen, tab)) return;
                    verifyTabs(tab);
                    stage = Stage.SHOW;
                }
                case SHOW -> {
                    if (!(minecraft.screen instanceof CreativeModeInventoryScreen screen) || ++frames < 5) return;
                    capture("ender-io-creative-tab");
                    for (char character : "creative power".toCharArray()) {
                        check("native_search_accepts_typed_query", screen.charTyped(character, 0));
                    }
                    frames = 0;
                    stage = Stage.RESULTS;
                }
                case RESULTS -> {
                    if (!(minecraft.screen instanceof CreativeModeInventoryScreen screen) || ++frames < 5) return;
                    var slots = screen.getMenu().slots;
                    check("native_mod_tab_search_finds_only_creative_power", slots.get(0).getItem().is(EIOBlocks.CREATIVE_POWER.get().asItem())
                        && IntStream.range(1, 45).noneMatch(index -> slots.get(index).hasItem()));
                    capture("creative-power-search");
                    energy = measureEnergyCapability();
                    stage = Stage.ENERGY;
                }
                case ENERGY -> {
                    if (!energy.isDone()) return;
                    check("creative_power_advertises_an_output_only_capability_and_repeats_extraction", energy.join());
                    finish(null);
                }
                default -> {}
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            finish(failure);
        }
    }

    private boolean select(CreativeModeInventoryScreen screen, CreativeModeTab tab) {
        var page = screen.getCurrentPage();
        if (!page.getVisibleTabs().contains(tab)) {
            if (++pageTurns > 5) throw new AssertionError("The Ender IO tab is absent from the creative pages");
            var next = screen.children().stream().filter(child -> child instanceof Button button
                && button.getMessage().getString().equals(">")).map(Button.class::cast).findFirst().orElseThrow();
            next.onPress();
            return false;
        }
        int column = page.getColumn(tab);
        int tabX = tab.isAlignedRight() ? screen.getXSize() - 27 * (7 - column) + 1 : 27 * column;
        int tabY = page.isTop(tab) ? -32 : screen.getYSize();
        double x = screen.getGuiLeft() + tabX + 13;
        double y = screen.getGuiTop() + tabY + 16;
        check("click_selects_the_real_forge_ender_io_tab", screen.mouseClicked(x, y, 0) && screen.mouseReleased(x, y, 0));
        return true;
    }

    private void verifyTabs(CreativeModeTab tab) {
        var icon = tab.getIconItem();
        var sprite = minecraft.getItemRenderer().getModel(icon, minecraft.level, minecraft.player, 0).getParticleIcon().contents().name();
        check("the_shipped_ender_io_icon_has_its_texture_and_glint", icon.is(EIOItems.CREATIVE_ICON.get()) && icon.hasFoil()
            && sprite.toString().equals("enderio:item/creative_tab_icon"));
        check("creative_power_is_listed_and_searchable_on_the_stock_tab", tab.hasSearchBar()
            && tab.getDisplayItems().stream().anyMatch(stack -> stack.is(EIOBlocks.CREATIVE_POWER.get().asItem()))
            && tab.getSearchTabDisplayItems().stream().anyMatch(stack -> stack.is(EIOBlocks.CREATIVE_POWER.get().asItem())));
        var hidden = Stream.concat(Stream.of(EIOItems.CREATIVE_ICON.get()), EIOItems.LEGACY_CREATIVE_ICONS.stream().map(entry -> entry.get()))
            .map(Item.class::cast).toList();
        check("tab_icons_stay_absent_from_display_and_search_stacks", Stream.of(tab.getDisplayItems(), tab.getSearchTabDisplayItems(),
            CreativeModeTabs.searchTab().getDisplayItems()).flatMap(stacks -> stacks.stream())
            .noneMatch(stack -> hidden.contains(stack.getItem())));
    }

    private CompletableFuture<Boolean> measureEnergyCapability() {
        var server = minecraft.getSingleplayerServer();
        var position = minecraft.player.blockPosition().offset(4, 0, 0);
        return server.submit(() -> {
            var level = server.overworld();
            level.setBlockAndUpdate(position, EIOBlocks.CREATIVE_POWER.get().defaultBlockState());
            var entity = level.getBlockEntity(position);
            if (entity == null) throw new AssertionError("The placed Creative Power block has no block entity");
            var storage = entity.getCapability(ForgeCapabilities.ENERGY).orElseThrow(AssertionError::new);
            int advertised = Integer.MAX_VALUE / 2;
            return storage.getMaxEnergyStored() == Integer.MAX_VALUE && storage.getEnergyStored() == advertised
                && storage.canExtract() && !storage.canReceive() && storage.receiveEnergy(1, false) == 0
                && storage.extractEnergy(Integer.MAX_VALUE, true) == advertised && storage.getEnergyStored() == advertised
                && storage.extractEnergy(Integer.MAX_VALUE, false) == advertised
                && storage.extractEnergy(8192, false) == 8192 && storage.getEnergyStored() == advertised;
        });
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
        ClientSmoke.complete("client_creative_tab", result);
    }
}
