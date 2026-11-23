package com.enderio.enderio.content.tools.vials;

import com.enderio.enderio.integration.gamestages.StageFeature;
import com.enderio.enderio.integration.gamestages.StageRules;

import com.enderio.core.client.item.AdvancedTooltipProvider;
import com.enderio.core.common.util.TooltipUtil;
import com.enderio.enderio.api.EnderIOAPI;
import com.enderio.core.common.item.ItemDataKey;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import com.enderio.enderio.api.soul.Soul;
import com.enderio.enderio.api.soul.SoulBoundUtils;
import com.enderio.enderio.config.base.BaseConfig;
import com.enderio.enderio.content.tools.ToolsLang;
import com.enderio.enderio.foundation.util.EntityCaptureUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.FORGE)
public class SoulVialItem extends Item implements AdvancedTooltipProvider {

    public static final ResourceLocation FILLED_MODEL_PROPERTY = EnderIOAPI.rl("soul_vial_filled");

    /**
     * Should match key from {@link LivingEntity#addAdditionalSaveData(CompoundTag)}
     */
    public static final String KEY_HEALTH = "Health";
    public static final ItemDataKey<Float> ENTITY_MAX_HEALTH = new ItemDataKey<>(EnderIOAPI.rl("entity_max_health"), Codec.FLOAT);

    private static Item soulVialItem() {
        return BuiltInRegistries.ITEM.getOptional(EnderIOAPI.rl("soul_vial"))
            .orElseThrow(() -> new IllegalStateException("Soul vial item is not registered"));
    }

    public SoulVialItem(Properties properties) {
        super(properties);
    }

    public static ItemStack forSoul(Soul soul) {
        ItemStack soulVial = new ItemStack(soulVialItem());
        Soul.ITEM_DATA.set(soulVial, soul);
        return soulVial;
    }

    // Item appearance and description

    @Override
    public boolean isFoil(ItemStack stack) {
        return Soul.ITEM_DATA.getOrDefault(stack, Soul.EMPTY).hasEntity();
    }

    @Override
    public void addCommonTooltips(ItemStack itemStack, @Nullable Player player, List<Component> tooltips) {
        // Add entity name
        var soul = Soul.ITEM_DATA.getOrDefault(itemStack, Soul.EMPTY);
        if (!soul.isEmpty()) {
            tooltips.add(TooltipUtil.style(Component.translatable(soul.entityType().getDescriptionId())));
        }
    }

    @Override
    public void addDetailedTooltips(ItemStack itemStack, @Nullable Player player, List<Component> tooltips) {
        var soul = Soul.ITEM_DATA.getOrDefault(itemStack, Soul.EMPTY);
        if (soul.isEmpty()) {
            return;
        }

        // Try add entity health
        float maxHealth = ENTITY_MAX_HEALTH.getOrDefault(itemStack, 0f);
        if (maxHealth <= 0) {
            return;
        }

        var entityTag = soul.entityTag();
        if (entityTag.contains(KEY_HEALTH)) {
            float health = entityTag.getFloat(KEY_HEALTH);
            tooltips.add(TooltipUtil.styledWithArgs(ToolsLang.SOUL_VIAL_TOOLTIP_HEALTH, health, maxHealth));
        }
    }

    // endregion

    // region Interactions

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity interactionTarget,
            InteractionHand usedHand) {
        if (player.level().isClientSide || !StageRules.permitsAction(player, StageFeature.SOUL_CAPTURE)
                || !StageRules.permitsItem(player, stack)) {
            return InteractionResult.FAIL;
        }

        Optional<ItemStack> itemStack = catchEntity(stack, interactionTarget,
                component -> player.displayClientMessage(component, true));
        if (itemStack.isPresent()) {
            ItemStack filledVial = itemStack.get();
            ItemStack hand = player.getItemInHand(usedHand);
            if (hand.isEmpty()) {
                hand.setCount(1); // Forge will fire the destroyItemEvent and vanilla replaces it to
                                  // ItemStack.EMPTY if this isn't done
                player.setItemInHand(usedHand, filledVial);
            } else {
                if (!player.addItem(filledVial)) {
                    player.drop(filledVial, false);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }
        var player = context.getPlayer();
        if (!StageRules.permitsAction(player, StageFeature.SOUL_SPAWNING)
                || !StageRules.permitsItem(player, context.getItemInHand())) {
            return InteractionResult.FAIL;
        }


        // Only players may use the soul vial
        if (player == null) {
            return InteractionResult.FAIL;
        }

        Runnable consumeVial = () -> {
            context.getItemInHand().shrink(1);
            var emptyVial = soulVialItem().getDefaultInstance();
            if (!player.addItem(emptyVial)) {
                player.drop(emptyVial, false);
            }
        };

        if (BaseConfig.COMMON.ITEMS.ALLOW_SOUL_VIALS_TO_CHANGE_SPAWNERS.get()) {
            if (context.getLevel().getBlockEntity(context.getClickedPos()) instanceof SpawnerBlockEntity spawnerHolder) {
                var storedSoul = Soul.ITEM_DATA.get(context.getItemInHand());
                if (storedSoul == null || storedSoul.isEmpty()) {
                    return InteractionResult.FAIL;
                }

                BlockPos pos = context.getClickedPos();
                BlockState blockState = serverLevel.getBlockState(pos);

                spawnerHolder.setEntityId(storedSoul.entityType(), serverLevel.getRandom());
                // 1.20.1 only changes the current entry. Load it as the sole candidate so
                // a vanilla delay reset cannot select the previous mob from the weighted pool.
                var spawner = spawnerHolder.getSpawner();
                var settings = spawner.save(new CompoundTag());
                settings.remove("SpawnPotentials");
                spawner.load(serverLevel, pos, settings);
                spawnerHolder.setChanged();
                serverLevel.sendBlockUpdated(pos, blockState, blockState, 3);
                serverLevel.gameEvent(context.getPlayer(), GameEvent.BLOCK_CHANGE, pos);
                consumeVial.run();
                return InteractionResult.CONSUME;
            }
        }

        return releaseEntity(context.getLevel(), context.getItemInHand(), context.getClickedFace(), context.getClickedPos(), consumeVial);
    }

    /**
     * @return the filled vial
     */
    private static Optional<ItemStack> catchEntity(ItemStack soulVial, LivingEntity entity,
            Consumer<Component> displayCallback) {

        // Soul Vial is filled, so it can't capture
        Soul soul = Soul.ITEM_DATA.getOrDefault(soulVial, Soul.EMPTY);
        if (soul.hasEntity()) {
            return Optional.empty();
        }

        if (entity instanceof Player) {
            displayCallback.accept(ToolsLang.SOUL_VIAL_ERROR_PLAYER);
            return Optional.empty();
        }

        if (!entity.isAlive()) {
            displayCallback.accept(ToolsLang.SOUL_VIAL_ERROR_DEAD);
            return Optional.empty();
        }

        //noinspection unchecked
        EntityCaptureUtils.CapturableStatus status = EntityCaptureUtils
                .getCapturableStatus((EntityType<? extends LivingEntity>) entity.getType());
        if (status != EntityCaptureUtils.CapturableStatus.CAPTURABLE) {
            displayCallback.accept(status.errorMessage());
            return Optional.empty();
        }

        // Create a filled vial and put the entity's NBT inside.
        if (entity instanceof Mob mob && mob.getLeashHolder() != null) {
            mob.dropLeash(true, true);
        }

        // Create the filled vial
        ItemStack filledVial = soulVial.copyWithCount(1);
        Soul.ITEM_DATA.set(filledVial, Soul.of(entity));
        ENTITY_MAX_HEALTH.set(filledVial, entity.getMaxHealth());

        // Consume the stack
        soulVial.shrink(1);

        // Remove the captured mob.
        entity.discard();
        return Optional.of(filledVial);
    }

    private static InteractionResult releaseEntity(Level level, ItemStack filledVial, Direction face, BlockPos pos,
            Runnable vialConsumed) {
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.FAIL;
        var storedSoul = Soul.ITEM_DATA.get(filledVial);
        if (storedSoul == null || storedSoul.isEmpty()) return InteractionResult.FAIL;

        double spawnX = pos.getX() + face.getStepX() + 0.5;
        double spawnY = pos.getY() + face.getStepY();
        double spawnZ = pos.getZ() + face.getStepZ() + 0.5;
        float rotation = Mth.wrapDegrees(level.getRandom().nextFloat() * 360.0f);
        Optional<Entity> entity = EntityType.create(storedSoul.getEntityTagWithId(), serverLevel);
        if (entity.isEmpty()) return InteractionResult.FAIL;

        Entity released = entity.get();
        released.setPos(spawnX, spawnY, spawnZ);
        released.setYRot(rotation);
        if (!serverLevel.addFreshEntity(released)) return InteractionResult.FAIL;
        vialConsumed.run();
        return InteractionResult.SUCCESS;
    }

    // endregion

    // region Utilities

    public static List<ItemStack> getAllFilled() {
        List<ItemStack> items = new ArrayList<>();
        for (var entityType : EntityCaptureUtils.getCapturableEntityTypes()) {
            items.add(forSoul(Soul.of(entityType)));
        }
        return items;
    }

    // endregion

    // region events

    /**
     * Fix for certain mobs that don't work with the soul vial.
     * So far the list includes:
     * - Donkey
     * - Mule
     * - Llama
     * - Villagers
     * - Wandering Trader
     * - Wolves
     */
    @SubscribeEvent
    public static void onLivingInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        ItemStack stack = event.getItemStack();

        // Use a copy of the stack if we're in creative.
        if (event.getEntity().getAbilities().instabuild) {
            stack = stack.copy();
        }

        if (stack.is(soulVialItem())) {
            if (event.getTarget() instanceof AbstractHorse || event.getTarget() instanceof Villager
                    || event.getTarget() instanceof WanderingTrader || event.getTarget() instanceof Wolf) {
                InteractionResult result = stack.interactLivingEntity(event.getEntity(), (LivingEntity) event.getTarget(), event.getHand());
                if (result.consumesAction()) {
                    event.setCancellationResult(result);
                    event.setCanceled(true);
                }
            }
        }
    }

    @EventBusSubscriber(modid = "enderio", bus = EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            event.enqueueWork(() -> {
                DispenserBlock.registerBehavior(soulVialItem(), new SoulVialDispenseBehavior());
            });
        }
    }

    // endregion

    // region Dispenser

    private static class SoulVialDispenseBehavior extends OptionalDispenseItemBehavior {
        private final DefaultDispenseItemBehavior dropRemainder = new DefaultDispenseItemBehavior();

        private ItemStack consumeWithRemainder(BlockSource source, ItemStack stack, ItemStack remainder) {
            stack.shrink(1);
            if (stack.isEmpty()) return remainder;
            DispenserBlockEntity dispenser = source.getEntity();
            if (dispenser.addItem(remainder) < 0) dropRemainder.dispense(source, remainder);
            return stack;
        }

        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            setSuccess(true);
            if (SoulBoundUtils.isBound(stack)) {
                return release(source, stack);
            } else {
                return capture(source, stack);
            }
        }

        private ItemStack release(BlockSource source, ItemStack stack) {
            Direction dispenserDirection = source.getBlockState().getValue(DispenserBlock.FACING);
            AtomicReference<ItemStack> resultStack = new AtomicReference<>();
            releaseEntity(source.getLevel(), stack, dispenserDirection, source.getPos(),
                () -> resultStack.set(this.consumeWithRemainder(source, stack, soulVialItem().getDefaultInstance())));

            if (resultStack.get() != null) {
                return resultStack.get();
            } else {
                this.setSuccess(false);
                return stack;
            }
        }

        private ItemStack capture(BlockSource source, ItemStack stack) {
            BlockPos blockpos = source.getPos().relative(source.getBlockState().getValue(DispenserBlock.FACING));
            for (LivingEntity livingentity : source.getLevel()
                .getEntitiesOfClass(LivingEntity.class, new AABB(blockpos),
                    living -> !(living instanceof Player))) {

                // Copy, consumeWithRemainder will shrink the stack.
                Optional<ItemStack> filledVial = catchEntity(stack.copy(), livingentity, component -> {});
                if (filledVial.isPresent()) {
                    return this.consumeWithRemainder(source, stack, filledVial.get());
                }
            }

            this.setSuccess(false);
            return stack;
        }
    }

    // endregion
}
