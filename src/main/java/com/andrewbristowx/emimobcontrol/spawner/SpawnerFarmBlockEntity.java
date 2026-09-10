package com.andrewbristowx.emimobcontrol.spawner;

import com.andrewbristowx.emimobcontrol.EmiMobControl;
import com.andrewbristowx.emimobcontrol.SpawnerTier;
import com.andrewbristowx.emimobcontrol.XpEssenceItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public final class SpawnerFarmBlockEntity extends BlockEntity implements WorldlyContainer {
    private static final String DEFAULT_ENTITY = "minecraft:pig";
    private static final String ITEM_ENTITY_KEY = "EmiSpawnerEntity";
    private static final String ITEM_TIER_KEY = "EmiSpawnerTier";
    /** When set (non-blank), this farm skips the whole mob-kill/loot-table simulation and just drops
     * copies of this exact item every cycle - used for materials with no sensible "kill" to simulate,
     * like Cobblemon's Bonguris (Apricorns), which only ever come from a tree, never a mob. */
    private static final String ITEM_DIRECT_KEY = "EmiSpawnerDirectItem";
    private static final int INVENTORY_SIZE = 27;
    private static final int[] SLOTS = createSlots();
    private static final Set<String> DENIED = Set.of(
            "minecraft:player", "minecraft:ender_dragon", "minecraft:wither",
            "minecraft:elder_guardian", "minecraft:giant"
    );

    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private String entityTypeId = DEFAULT_ENTITY;
    private String directItemId = "";
    private UUID owner;
    private int progress;
    private int xpRemainder;
    private SpawnerTier tier = SpawnerTier.BASE;
    private boolean hopperConnected;
    private boolean outputFull;

    public SpawnerFarmBlockEntity(BlockPos pos, BlockState state) {
        super(EmiMobControl.SPAWNER_FARM_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(Level rawLevel, BlockPos pos, BlockState state, SpawnerFarmBlockEntity farm) {
        if (!(rawLevel instanceof ServerLevel level)) return;
        if (level.getGameTime() % 20L == 0L) {
            farm.refreshStatus(level, pos, state);
            SpawnerFarmDisplayService.sync(level, pos, farm);
        }
        // Production is only throttled by its own output filling up, not by requiring a literal
        // vanilla Hopper block underneath - Sophisticated Storage's hopper upgrade (and anything else
        // using Fabric's Transfer API, now wired up in EmiMobControl.onInitialize) also empties this
        // container without ever placing a real Hopper block, so gating on hopperConnected here would
        // starve production for anyone using those instead of a plain hopper.
        if (farm.outputFull) return;

        farm.progress++;
        if (farm.progress < farm.tier.cycleTicks()) return;
        farm.progress = 0;
        farm.produce(level, pos);
        farm.setChanged();
        level.sendBlockUpdated(pos, state, state, 3);
    }

    public void readSpawnerItem(ItemStack stack, @Nullable LivingEntity placer) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = data.copyTag();
        String candidate = tag.getString(ITEM_ENTITY_KEY);
        if (isAllowedEntity(candidate)) entityTypeId = candidate;
        directItemId = tag.getString(ITEM_DIRECT_KEY);
        tier = SpawnerTier.fromOrdinal(tag.getInt(ITEM_TIER_KEY));
        if (placer instanceof Player player) owner = player.getUUID();
        setChanged();
        sync();
        if (level instanceof ServerLevel serverLevel) {
            SpawnerFarmDisplayService.sync(serverLevel, worldPosition, this);
        }
    }

    public static ItemStack createSpawnerItem(String entityTypeId) {
        return createSpawnerItem(entityTypeId, SpawnerTier.BASE);
    }

    public static ItemStack createSpawnerItem(String entityTypeId, SpawnerTier tier) {
        String safeId = isAllowedEntity(entityTypeId) ? entityTypeId : DEFAULT_ENTITY;
        ItemStack stack = new ItemStack(EmiMobControl.SPAWNER_FARM_ITEM);
        CompoundTag tag = new CompoundTag();
        tag.putString(ITEM_ENTITY_KEY, safeId);
        tag.putInt(ITEM_TIER_KEY, tier.ordinal());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.tryParse(safeId));
        Component mobName = type.getDescription();
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("block.emimobcontrol.spawner_farm.named", mobName));
        return stack;
    }

    /** A farm pre-configured to drop a fixed item every cycle instead of simulating a mob kill - see
     * {@link #ITEM_DIRECT_KEY}. {@code itemId} must already be a valid, non-air item id; callers (e.g.
     * a crafting recipe's result NBT) are expected to only ever pass a real registered item.
     * Accepts a comma-separated list (e.g. {@code "minecraft:honey_bottle,minecraft:honeycomb"}) to
     * drop several different items every cycle instead of just one - a plain single id keeps working
     * exactly as before, so this is backward compatible with every farm already placed. */
    public static ItemStack createDirectItemSpawnerItem(String itemId) {
        return createDirectItemSpawnerItem(itemId, SpawnerTier.BASE);
    }

    /** Same as {@link #createDirectItemSpawnerItem(String)} but preserving a specific tier - used when
     * picking a placed farm back up, so upgrading it and then silk-touching it doesn't reset it to Base. */
    public static ItemStack createDirectItemSpawnerItem(String itemId, SpawnerTier tier) {
        ItemStack stack = new ItemStack(EmiMobControl.SPAWNER_FARM_ITEM);
        CompoundTag tag = new CompoundTag();
        tag.putString(ITEM_DIRECT_KEY, itemId);
        tag.putInt(ITEM_TIER_KEY, tier.ordinal());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(firstDirectItemId(itemId)));
        stack.set(DataComponents.CUSTOM_NAME,
                Component.translatable("block.emimobcontrol.spawner_farm.named", item.getDescription()));
        return stack;
    }

    private static String firstDirectItemId(String rawDirectItemId) {
        int comma = rawDirectItemId.indexOf(',');
        return comma < 0 ? rawDirectItemId : rawDirectItemId.substring(0, comma);
    }

    public static SpawnerTier getItemTier(ItemStack stack) {
        CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return SpawnerTier.fromOrdinal(data.copyTag().getInt(ITEM_TIER_KEY));
    }

    public static void setItemTier(ItemStack stack, SpawnerTier tier) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(ITEM_TIER_KEY, tier.ordinal());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static boolean isAllowedEntity(String entityTypeId) {
        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        if (id == null || !"minecraft".equals(id.getNamespace()) || DENIED.contains(id.toString())) return false;
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return false;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(id);
        return type.getCategory() != MobCategory.MISC;
    }

    public String getEntityTypeId() {
        return entityTypeId;
    }

    public Component getMobName() {
        if (!directItemId.isBlank()) {
            ResourceLocation itemId = ResourceLocation.tryParse(firstDirectItemId(directItemId));
            Item item = itemId == null ? null : BuiltInRegistries.ITEM.get(itemId);
            if (item != null && item != Items.AIR) return item.getDescription();
        }
        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
        return type.getDescription();
    }

    public String getDirectItemId() {
        return directItemId;
    }

    public SpawnerTier getTier() {
        return tier;
    }

    public boolean isHopperConnected() {
        return hopperConnected;
    }

    public boolean isOutputFull() {
        return outputFull;
    }

    private void refreshStatus(ServerLevel level, BlockPos pos, BlockState state) {
        boolean newHopper = level.getBlockState(pos.below()).is(Blocks.HOPPER);
        boolean newFull = !hasEmptySlot();
        if (newHopper != hopperConnected || newFull != outputFull) {
            hopperConnected = newHopper;
            outputFull = newFull;
            setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    private void produce(ServerLevel level, BlockPos pos) {
        if (!directItemId.isBlank()) {
            produceDirectItem();
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(entityTypeId);
        EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
        if (type == null) return;

        Entity entity = type.create(level);
        if (entity == null) return;
        entity.setPos(Vec3.atCenterOf(pos));

        ServerPlayer killer = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        DamageSource damageSource = killer == null
                ? level.damageSources().generic()
                : level.damageSources().playerAttack(killer);

        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
                .withLuck(0.0F);
        if (killer != null) {
            params.withOptionalParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                    .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, killer)
                    .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, killer);
        }

        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(type.getDefaultLootTable());
        for (int kill = 0; kill < tier.simulatedKills(); kill++) {
            for (ItemStack generated : lootTable.getRandomItems(params.create(LootContextParamSets.ENTITY))) {
                insert(generated.copy());
            }
        }

        int experience = experienceFor(entityTypeId) * tier.simulatedKills() + xpRemainder;
        int essenceCount = experience / XpEssenceItem.EXPERIENCE_PER_ESSENCE;
        xpRemainder = experience % XpEssenceItem.EXPERIENCE_PER_ESSENCE;
        while (essenceCount > 0) {
            int count = Math.min(64, essenceCount);
            insert(new ItemStack(EmiMobControl.XP_ESSENCE, count));
            essenceCount -= count;
        }
        outputFull = !hasEmptySlot();
    }

    /** Direct-item mode: no entity, no loot table - just hand over copies of the configured item(s).
     * Reuses {@link SpawnerTier#simulatedKills()} as the per-cycle yield of EACH item so higher
     * tiers still feel like an upgrade, the same way they multiply loot rolls in the normal
     * kill-simulation mode. {@link #directItemId} may list several items separated by commas (e.g.
     * a bee farm dropping both honey bottles and honeycomb every cycle) - a single id behaves
     * exactly as before. */
    private void produceDirectItem() {
        for (String rawId : directItemId.split(",")) {
            ResourceLocation itemId = ResourceLocation.tryParse(rawId.strip());
            Item item = itemId == null ? null : BuiltInRegistries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) continue;
            insert(new ItemStack(item, tier.simulatedKills()));
        }
        outputFull = !hasEmptySlot();
    }

    private void insert(ItemStack incoming) {
        if (incoming.isEmpty()) return;
        for (int slot = 0; slot < items.size() && !incoming.isEmpty(); slot++) {
            ItemStack present = items.get(slot);
            if (!present.isEmpty() && ItemStack.isSameItemSameComponents(present, incoming)) {
                int move = Math.min(incoming.getCount(), present.getMaxStackSize() - present.getCount());
                if (move > 0) {
                    present.grow(move);
                    incoming.shrink(move);
                }
            }
        }
        for (int slot = 0; slot < items.size() && !incoming.isEmpty(); slot++) {
            if (items.get(slot).isEmpty()) {
                int move = Math.min(incoming.getCount(), incoming.getMaxStackSize());
                ItemStack placed = incoming.copy();
                placed.setCount(move);
                items.set(slot, placed);
                incoming.shrink(move);
            }
        }
    }

    private boolean hasEmptySlot() {
        return items.stream().anyMatch(ItemStack::isEmpty);
    }

    private static int experienceFor(String entityId) {
        return switch (entityId) {
            case "minecraft:blaze", "minecraft:piglin_brute" -> 10;
            case "minecraft:ravager" -> 20;
            case "minecraft:guardian" -> 10;
            case "minecraft:slime", "minecraft:magma_cube" -> 3;
            default -> 5;
        };
    }

    private void sync() {
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("EntityType", entityTypeId);
        tag.putString("DirectItem", directItemId);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Progress", progress);
        tag.putInt("XpRemainder", xpRemainder);
        tag.putInt("Tier", tier.ordinal());
        tag.putBoolean("Hopper", hopperConnected);
        tag.putBoolean("OutputFull", outputFull);
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        String candidate = tag.getString("EntityType");
        entityTypeId = isAllowedEntity(candidate) ? candidate : DEFAULT_ENTITY;
        directItemId = tag.getString("DirectItem");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        progress = Math.max(0, tag.getInt("Progress"));
        xpRemainder = Math.max(0, tag.getInt("XpRemainder"));
        tier = SpawnerTier.fromOrdinal(tag.getInt("Tier"));
        hopperConnected = tag.getBoolean("Hopper");
        outputFull = tag.getBoolean("OutputFull");
        ContainerHelper.loadAllItems(tag, items, registries);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("EntityType", entityTypeId);
        tag.putString("DirectItem", directItemId);
        tag.putInt("Tier", tier.ordinal());
        tag.putBoolean("Hopper", hopperConnected);
        tag.putBoolean("OutputFull", outputFull);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override public int getContainerSize() { return items.size(); }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return items.get(slot); }
    @Override public ItemStack removeItem(int slot, int amount) { ItemStack result = ContainerHelper.removeItem(items, slot, amount); if (!result.isEmpty()) setChanged(); return result; }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(items, slot); }
    @Override public void setItem(int slot, ItemStack stack) { items.set(slot, stack); if (stack.getCount() > getMaxStackSize()) stack.setCount(getMaxStackSize()); setChanged(); }
    @Override public boolean stillValid(Player player) { return level != null && level.getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D) <= 64.0D; }
    @Override public void clearContent() { items.clear(); setChanged(); }
    @Override public int[] getSlotsForFace(Direction side) { return SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) { return false; }
    // Any side, not just DOWN - a hopper/storage upgrade may be placed against any face of this block.
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) { return true; }

    private static int[] createSlots() {
        int[] slots = new int[INVENTORY_SIZE];
        for (int i = 0; i < INVENTORY_SIZE; i++) slots[i] = i;
        return slots;
    }
}
