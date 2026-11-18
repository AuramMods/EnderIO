package com.enderio.core.gametest;

import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.content.tools.vials.SoulVialItem;
import com.enderio.enderio.foundation.util.EntityCaptureUtils;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSourceImpl;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.function.Consumer;

@GameTestHolder("endercore_tests")
@PrefixGameTestTemplate(false)
public class SoulVialInteractionTests {
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void playerCaptureAndReleasePreserveEntityAndVialCount(GameTestHelper helper) {
        try (var peer = new Peer(helper)) {
            Cow cow = cow(helper);
            cow.setCustomName(Component.literal("Captured cow"));
            cow.setHealth(7);
            UUID uuid = cow.getUUID();
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VialTestContent.SOUL_VIAL.get()));
            var result = VialTestContent.SOUL_VIAL.get().interactLivingEntity(peer.player.getMainHandItem(), peer.player, cow, InteractionHand.MAIN_HAND);
            helper.assertTrue(result == InteractionResult.SUCCESS && cow.isRemoved(), "Capture did not consume exactly one live entity");
            var filled = peer.player.getMainHandItem();
            helper.assertTrue(filled.getCount() == 1 && SoulBoundUtils.isBound(filled), "Filled vial did not expose its actual read-only soul capability");
            helper.assertTrue(!SoulBoundUtils.canBindSoul(filled), "Vial incorrectly became rebindable");
            helper.assertTrue(SoulVialItem.ENTITY_MAX_HEALTH.get(filled) == cow.getMaxHealth(), "Capture lost max-health tooltip state");
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.of(filled.save(new CompoundTag())));
            helper.assertTrue(release(helper, peer.player, new BlockPos(1, 0, 1)) == InteractionResult.SUCCESS,
                "Native item use did not release saved soul data");
            var releasedEntities = helper.getLevel().getEntitiesOfClass(Cow.class,
                new AABB(helper.absolutePos(new BlockPos(1, 1, 1))));
            helper.assertTrue(releasedEntities.size() == 1, "Release did not insert exactly one cow");
            Entity released = releasedEntities.get(0);
            helper.assertTrue(released instanceof Cow && ((Cow) released).getHealth() == 7
                && released.getCustomName().getString().equals("Captured cow"), "Release lost type, health or name");
            helper.assertTrue(vialCount(peer.player, false) == 1 && vialCount(peer.player, true) == 0,
                "Release duplicated or lost a vial");
            helper.assertTrue(!released.getUUID().equals(uuid), "Release did not retain the baseline fresh-UUID policy");
            released.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void captureRejectsPlayersDeadBlacklistedAndAlreadyFilledTargets(GameTestHelper helper) {
        try (var peer = new Peer(helper)) {
            var item = VialTestContent.SOUL_VIAL.get();
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 3));
            helper.assertTrue(item.interactLivingEntity(peer.player.getMainHandItem(), peer.player, peer.player,
                InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "Player was capturable");
            var dead = cow(helper);
            dead.setHealth(0);
            helper.assertTrue(item.interactLivingEntity(peer.player.getMainHandItem(), peer.player, dead,
                InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "Dead entity was capturable");
            dead.discard();
            helper.assertTrue(EntityCaptureUtils.getCapturableStatus(EntityType.WARDEN) == EntityCaptureUtils.CapturableStatus.BLACKLISTED
                && EntityCaptureUtils.getCapturableStatus(EntityType.WITHER) == EntityCaptureUtils.CapturableStatus.BLACKLISTED
                && EntityCaptureUtils.getCapturableStatus(EntityType.ENDER_DRAGON) == EntityCaptureUtils.CapturableStatus.BLACKLISTED,
                "Production native blacklist/common boss tags did not load");
            var warden = EntityType.WARDEN.create(helper.getLevel());
            helper.assertTrue(item.interactLivingEntity(peer.player.getMainHandItem(), peer.player, warden,
                InteractionHand.MAIN_HAND) == InteractionResult.FAIL && !warden.isRemoved(), "Blacklisted entity was consumed");
            helper.assertTrue(peer.player.getMainHandItem().getCount() == 3, "Rejected capture consumed an empty vial");
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, SoulVialItem.forSoul(Soul.of(EntityType.PIG)));
            var live = cow(helper);
            helper.assertTrue(item.interactLivingEntity(peer.player.getMainHandItem(), peer.player, live,
                InteractionHand.MAIN_HAND) == InteractionResult.FAIL && live.isAlive(), "Filled vial captured another entity");
            helper.assertTrue(Soul.ITEM_DATA.get(peer.player.getMainHandItem()).entityType() == EntityType.PIG, "Rejected capture replaced the stored soul");
            live.discard();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rejectedOrUncreatableEntityReleaseRetainsFilledVial(GameTestHelper helper) {
        try (var peer = new Peer(helper)) {
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, SoulVialItem.forSoul(Soul.of(EntityType.PLAYER)));
            helper.assertTrue(release(helper, peer.player, new BlockPos(1, 0, 1)) == InteractionResult.FAIL
                && SoulBoundUtils.isBound(peer.player.getMainHandItem()), "Uncreatable entity consumed the soul");
            var original = cow(helper);
            String marker = "denied-" + UUID.randomUUID();
            original.setCustomName(Component.literal(marker));
            var soul = Soul.of(original);
            original.discard();
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, SoulVialItem.forSoul(soul));
            Consumer<EntityJoinLevelEvent> deny = event -> {
                if (event.getEntity().hasCustomName() && event.getEntity().getCustomName().getString().equals(marker)) event.setCanceled(true);
            };
            MinecraftForge.EVENT_BUS.addListener(deny);
            try {
                helper.assertTrue(release(helper, peer.player, new BlockPos(1, 0, 1)) == InteractionResult.FAIL
                    && Soul.isSameEntitySameTag(soul, Soul.ITEM_DATA.get(peer.player.getMainHandItem()))
                    && vialCount(peer.player, false) == 0, "Cancelled insertion consumed or altered the soul");
            } finally { MinecraftForge.EVENT_BUS.unregister(deny); }
            helper.assertTrue(release(helper, peer.player, new BlockPos(1, 0, 1)) == InteractionResult.SUCCESS,
                "Previously rejected soul could not be retried");
            helper.assertTrue(vialCount(peer.player, false) == 1 && vialCount(peer.player, true) == 0, "Retried release changed vial accounting");
            helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(helper.absolutePos(new BlockPos(1, 1, 1))))
                .forEach(Entity::discard);
            helper.assertTrue(release(helper, peer.player, new BlockPos(1, 0, 1)) == InteractionResult.FAIL,
                "Empty vial reported a successful release");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void configuredSpawnerReplacementUsesNativeBlockEntityAndConsumesOneVial(GameTestHelper helper) throws Exception {
        var setting = BaseConfig.COMMON.ITEMS.ALLOW_SOUL_VIALS_TO_CHANGE_SPAWNERS;
        boolean original = setting.get();
        try (var peer = new Peer(helper)) {
            var relative = new BlockPos(1, 1, 1);
            var pos = helper.absolutePos(relative);
            helper.getLevel().setBlockAndUpdate(pos, Blocks.SPAWNER.defaultBlockState());
            var spawner = (SpawnerBlockEntity) helper.getLevel().getBlockEntity(pos);
            var oldData = TagParser.parseTag("""
                {Delay:43s,MinSpawnDelay:100s,MaxSpawnDelay:250s,SpawnCount:2s,SpawnRange:3s,
                 SpawnData:{entity:{id:"minecraft:pig"}},SpawnPotentials:[{weight:1,data:{entity:{id:"minecraft:pig"}}}]}
                """);
            spawner.load(oldData);
            setting.set(true);
            var stack = SoulVialItem.forSoul(Soul.of(EntityType.COW));
            stack.setCount(2);
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            helper.assertTrue(release(helper, peer.player, relative) == InteractionResult.CONSUME, "Configured spawner replacement did not run");
            var changed = spawner.saveWithoutMetadata();
            helper.assertTrue(changed.getCompound("SpawnData").getCompound("entity").getString("id").equals("minecraft:cow"),
                "Changed spawner entity did not serialize");
            var potentials = changed.getList("SpawnPotentials", Tag.TAG_COMPOUND);
            helper.assertTrue(potentials.size() == 1 && potentials.getCompound(0).getCompound("data").getCompound("entity")
                    .getString("id").equals("minecraft:cow"), "Original spawn pool can replace the new mob on a delay reset");
            helper.assertTrue(changed.getShort("Delay") == 43 && changed.getShort("MinSpawnDelay") == 100
                && changed.getShort("MaxSpawnDelay") == 250 && changed.getShort("SpawnCount") == 2
                && changed.getShort("SpawnRange") == 3, "Changing mob type reset the spawner's timing, count or range");
            helper.assertTrue(vialCount(peer.player, true) == 1 && vialCount(peer.player, false) == 1, "Spawner replacement did not consume exactly one vial");
            setting.set(false);
            var result = release(helper, peer.player, relative);
            helper.assertTrue(result == InteractionResult.SUCCESS && vialCount(peer.player, true) == 0 && vialCount(peer.player, false) == 2,
                "Disabled replacement did not use normal release behavior");
        } finally { setting.set(original); }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void registeredDispenserCapturesAndReleasesWithCorrectRemainders(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        var dispenser = (DispenserBlockEntity) helper.getLevel().getBlockEntity(pos);
        Cow cow = EntityType.COW.create(helper.getLevel());
        cow.setNoAi(true);
        cow.setPos(Vec3.atBottomCenterOf(pos.east()));
        helper.getLevel().addFreshEntity(cow);
        dispenser.setItem(0, new ItemStack(VialTestContent.SOUL_VIAL.get(), 3));
        Blocks.DISPENSER.tick(dispenser.getBlockState(), helper.getLevel(), pos, helper.getLevel().random);
        helper.assertTrue(cow.isRemoved() && dispenser.getItem(0).getCount() == 2 && SoulBoundUtils.isBound(dispenser.getItem(1)),
            "Native dispenser did not capture and insert the filled remainder");
        var filled = dispenser.getItem(1);
        dispenser.clearContent();
        dispenser.setItem(0, filled);
        Blocks.DISPENSER.tick(dispenser.getBlockState(), helper.getLevel(), pos, helper.getLevel().random);
        helper.assertTrue(dispenser.getItem(0).getCount() == 1 && !SoulBoundUtils.isBound(dispenser.getItem(0))
            && helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(pos.east())).size() == 1, "Native dispenser did not release and replace its last vial");
        helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(pos.east())).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fullDispenserEjectsRemainderAndFailedReleasePreservesInventory(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        var dispenser = (DispenserBlockEntity) helper.getLevel().getBlockEntity(pos);
        for (int i = 1; i < 9; i++) dispenser.setItem(i, new ItemStack(Items.DIAMOND, 64));
        var existing = cow(helper);
        String marker = "dispenser-" + UUID.randomUUID();
        existing.setCustomName(Component.literal(marker));
        var filled = SoulVialItem.forSoul(Soul.of(existing));
        existing.discard();
        filled.setCount(2);
        dispenser.setItem(0, filled);
        var source = new BlockSourceImpl(helper.getLevel(), pos);
        var behavior = VialTestContent.DISPENSER_ACCESS.get().behavior(filled);
        Consumer<EntityJoinLevelEvent> deny = event -> {
            if (event.getEntity().hasCustomName() && event.getEntity().getCustomName().getString().equals(marker)) event.setCanceled(true);
        };
        MinecraftForge.EVENT_BUS.addListener(deny);
        ItemStack unchanged;
        try { unchanged = behavior.dispense(source, filled); }
        finally { MinecraftForge.EVENT_BUS.unregister(deny); }
        helper.assertTrue(unchanged == filled && unchanged.getCount() == 2 && SoulBoundUtils.isBound(unchanged),
            "Rejected dispenser release consumed a filled vial");
        existing.discard();
        var remainder = behavior.dispense(source, unchanged);
        helper.assertTrue(remainder.getCount() == 1 && SoulBoundUtils.isBound(remainder), "Dispenser consumed more than one filled vial");
        var dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2),
            entity -> entity.getItem().is(VialTestContent.SOUL_VIAL.get()));
        helper.assertTrue(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 1
            && !SoulBoundUtils.isBound(dropped.get(0).getItem()), "Full dispenser did not eject exactly one empty remainder");
        for (int i = 1; i < 9; i++) helper.assertTrue(dispenser.getItem(i).getCount() == 64, "Remainder overwrote another slot");
        dropped.forEach(Entity::discard);
        helper.getLevel().getEntitiesOfClass(Cow.class, new AABB(pos.east())).forEach(Entity::discard);
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void specificEntityInteractionCancelsVanillaUseAfterCapture(GameTestHelper helper) {
        try (var peer = new Peer(helper)) {
            var villager = EntityType.VILLAGER.create(helper.getLevel());
            peer.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(VialTestContent.SOUL_VIAL.get(), 2));
            var event = new PlayerInteractEvent.EntityInteractSpecific(peer.player, InteractionHand.MAIN_HAND, villager, Vec3.ZERO);
            MinecraftForge.EVENT_BUS.post(event);
            helper.assertTrue(event.isCanceled() && event.getCancellationResult().consumesAction() && villager.isRemoved(),
                "Specific interaction continued into vanilla entity use after capture");
            helper.assertTrue(vialCount(peer.player, false) == 1 && vialCount(peer.player, true) == 1, "Specific interaction changed vial accounting");
            peer.player.getAbilities().instabuild = true;
            var wolf = EntityType.WOLF.create(helper.getLevel());
            var creative = new PlayerInteractEvent.EntityInteractSpecific(peer.player, InteractionHand.MAIN_HAND, wolf, Vec3.ZERO);
            MinecraftForge.EVENT_BUS.post(creative);
            helper.assertTrue(creative.isCanceled() && wolf.isRemoved() && vialCount(peer.player, false) == 1 && vialCount(peer.player, true) == 2,
                "Creative capture did not preserve its empty vial and produce one filled vial");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nativeEntityTagGeneratorMatchesShippedCaptureAndSpawnerDefaults(GameTestHelper helper) throws Exception {
        var root = java.nio.file.Files.createTempDirectory("enderio-entity-tags-");
        try {
            var registries = java.util.concurrent.CompletableFuture.<net.minecraft.core.HolderLookup.Provider>completedFuture(helper.getLevel().registryAccess());
            new com.enderio.enderio.datagen.common.tags.EIOEntityTagsProvider(new net.minecraft.data.PackOutput(root), registries, null)
                .run(net.minecraft.data.CachedOutput.NO_CACHE).join();
            for (String id : new String[] { "enderio:soul_vial_blacklist", "enderio:soul_vial_whitelist",
                    "enderio:spawner_blacklist", "enderio:spawner_whitelist", "c:bosses", "c:capturing_not_supported" }) {
                var location = net.minecraft.resources.ResourceLocation.parse(id);
                var generated = JsonParser.parseString(java.nio.file.Files.readString(root.resolve("data/" + location.getNamespace()
                    + "/tags/entity_types/" + location.getPath() + ".json")));
                try (var reader = helper.getLevel().getServer().getResourceManager().getResourceOrThrow(
                        location.withPath("tags/entity_types/" + location.getPath() + ".json")).openAsReader()) {
                    helper.assertTrue(generated.equals(JsonParser.parseReader(reader)), "Native tag generator changed shipped defaults: " + id);
                }
            }
        } finally {
            try (var files = java.nio.file.Files.walk(root)) {
                for (var file : files.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(file);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tagUpdatesRefreshCapturableEntriesAndPreserveWhitelistPrecedence(GameTestHelper helper) {
        var registry = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        var original = new java.util.HashMap<net.minecraft.tags.TagKey<EntityType<?>>, java.util.List<net.minecraft.core.Holder<EntityType<?>>>>();
        registry.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
        var changed = new java.util.HashMap<>(original);
        helper.assertTrue(!EntityCaptureUtils.getCapturableEntityTypes().contains(EntityType.WARDEN), "Default capturable cache includes a blacklisted mob");
        try {
            changed.put(EntityCaptureUtils.SOUL_VIAL_WHITELIST, java.util.List.of(registry.wrapAsHolder(EntityType.WARDEN), registry.wrapAsHolder(EntityType.ENDER_DRAGON)));
            registry.bindTags(changed);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TagsUpdatedEvent(helper.getLevel().registryAccess(), false, false));
            helper.assertTrue(EntityCaptureUtils.getCapturableStatus(EntityType.WARDEN) == EntityCaptureUtils.CapturableStatus.CAPTURABLE
                && EntityCaptureUtils.getCapturableEntityTypes().contains(EntityType.WARDEN), "Native tag update did not refresh whitelist override/cache");
            helper.assertTrue(EntityCaptureUtils.getCapturableStatus(EntityType.ENDER_DRAGON) == EntityCaptureUtils.CapturableStatus.BLACKLISTED
                && !EntityCaptureUtils.getCapturableEntityTypes().contains(EntityType.ENDER_DRAGON), "Whitelist bypassed the baseline dragon exclusion");
        } finally {
            registry.bindTags(original);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.TagsUpdatedEvent(helper.getLevel().registryAccess(), false, false));
        }
        helper.assertTrue(!EntityCaptureUtils.getCapturableEntityTypes().contains(EntityType.WARDEN), "Cache retained removed whitelist entries");
        helper.succeed();
    }

    private static Cow cow(GameTestHelper helper) {
        var cow = EntityType.COW.create(helper.getLevel());
        cow.setNoAi(true);
        cow.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        helper.getLevel().addFreshEntity(cow);
        return cow;
    }

    private static InteractionResult release(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
        var pos = helper.absolutePos(relative);
        return VialTestContent.SOUL_VIAL.get().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
            new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
    }

    private static int vialCount(ServerPlayer player, boolean filled) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(VialTestContent.SOUL_VIAL.get()) && SoulBoundUtils.isBound(stack) == filled) count += stack.getCount();
        }
        return count;
    }

    private static final class Peer implements AutoCloseable {
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel socket = new EmbeddedChannel(connection);
        final ServerPlayer player;
        Peer(GameTestHelper helper) {
            var level = helper.getLevel();
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "VialFixture"));
            new ServerGamePacketListenerImpl(level.getServer(), connection, player);
            connection.setProtocol(ConnectionProtocol.PLAY);
        }
        @Override public void close() {
            player.getAdvancements().stopListening();
            socket.finishAndReleaseAll();
            player.getTextFilter().leave();
        }
    }
}
