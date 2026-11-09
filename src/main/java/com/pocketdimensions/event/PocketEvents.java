package com.pocketdimensions.event;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.dimension.ModDimensions;
import com.pocketdimensions.dimension.PocketData;
import com.pocketdimensions.dimension.PocketPlotManager;
import com.pocketdimensions.dimension.PocketSavedData;
import com.pocketdimensions.network.ModNetworking;
import com.pocketdimensions.network.packet.PacketSyncSkyWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = PocketDimensionsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PocketEvents {

    public static void sendPocketWorldBorder(ServerPlayer player, PocketData pd) {
        WorldBorder border = new WorldBorder();
        border.setCenter(pd.spawnBlockX(), pd.spawnBlockZ());
        border.setSize(48.0); // 3 chunks = 48 blocks diameter
        border.setDamagePerBlock(0.2);
        border.setDamageSafeZone(1.0);
        border.setWarningBlocks(2);
        border.setWarningTime(15);
        player.connection.send(new ClientboundInitializeBorderPacket(border));
    }

    public static void restoreWorldBorder(ServerPlayer player, ServerLevel targetLevel) {
        if (targetLevel != null) {
            player.connection.send(new ClientboundInitializeBorderPacket(targetLevel.getWorldBorder()));
        }
    }

    @SubscribeEvent
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck e) {
        if (e.getLevel() instanceof ServerLevel sl && sl.dimension().equals(ModDimensions.POCKET_LEVEL)) {
            MobSpawnType t = e.getSpawnType();
            if (t == MobSpawnType.NATURAL || t == MobSpawnType.CHUNK_GENERATION ||
                t == MobSpawnType.SPAWNER || t == MobSpawnType.STRUCTURE ||
                t == MobSpawnType.PATROL || t == MobSpawnType.REINFORCEMENT ||
                t == MobSpawnType.EVENT || t == MobSpawnType.JOCKEY) {
                e.setResult(Event.Result.DENY);
            }
        }
    }

    @SubscribeEvent
    public static void onFinalize(MobSpawnEvent.FinalizeSpawn e) {
        if (e.getLevel() instanceof ServerLevel sl && sl.dimension().equals(ModDimensions.POCKET_LEVEL)) {
            MobSpawnType t = e.getSpawnType();
            if (t == MobSpawnType.NATURAL || t == MobSpawnType.CHUNK_GENERATION ||
                t == MobSpawnType.SPAWNER || t == MobSpawnType.STRUCTURE ||
                t == MobSpawnType.PATROL || t == MobSpawnType.REINFORCEMENT ||
                t == MobSpawnType.EVENT || t == MobSpawnType.JOCKEY) {
                e.setSpawnCancelled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent e) {
        if (!e.loadedFromDisk() && e.getLevel().dimension().equals(ModDimensions.POCKET_LEVEL) && e.getEntity() instanceof Enemy) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide || !(e.player instanceof ServerPlayer player)) return;

        if (player.level().dimension().equals(ModDimensions.POCKET_LEVEL)) {
            PocketSavedData sd = PocketSavedData.get(player.server);
            PocketData pd = sd.getPocketAt(player.chunkPosition().x, player.chunkPosition().z);
            if (pd == null) pd = sd.getPocketByOwner(player.getUUID());

            if (player.getY() < -30.0) {
                PocketSavedData.teleportOut(player, player.server);
                player.fallDistance = 0.0f;
                player.setDeltaMovement(Vec3.ZERO);
                player.sendSystemMessage(Component.translatable("message.pocketdimensions.void_fall"));
                return;
            }

            if (pd != null) {
                double px = player.getX();
                double pz = player.getZ();
                double minX = pd.minBlockX() + 0.3;
                double maxX = pd.maxBlockX() + 0.7;
                double minZ = pd.minBlockZ() + 0.3;
                double maxZ = pd.maxBlockZ() + 0.7;

                boolean out = false;
                double clampX = px;
                double clampZ = pz;
                if (px < minX) { clampX = minX; out = true; }
                else if (px > maxX) { clampX = maxX; out = true; }
                if (pz < minZ) { clampZ = minZ; out = true; }
                else if (pz > maxZ) { clampZ = maxZ; out = true; }

                if (out) {
                    player.teleportTo(clampX, player.getY(), clampZ);
                    player.setDeltaMovement(player.getDeltaMovement().multiply(0, 1, 0));
                    player.displayClientMessage(Component.translatable("message.pocketdimensions.boundary"), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel sl && sl.dimension().equals(ModDimensions.POCKET_LEVEL)) {
            if (e.getPlayer() != null && e.getPlayer().hasPermissions(2)) return;
            PocketSavedData sd = PocketSavedData.get(sl.getServer());
            PocketData pd = sd.getPocketAt(e.getPos().getX() >> 4, e.getPos().getZ() >> 4);
            if (pd == null || !pd.containsBlock(e.getPos().getX(), e.getPos().getZ())) {
                e.setCanceled(true);
                if (e.getPlayer() != null) {
                    e.getPlayer().displayClientMessage(Component.translatable("message.pocketdimensions.boundary"), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel() instanceof ServerLevel sl && sl.dimension().equals(ModDimensions.POCKET_LEVEL)) {
            if (e.getEntity() instanceof ServerPlayer sp && sp.hasPermissions(2)) return;
            PocketSavedData sd = PocketSavedData.get(sl.getServer());
            PocketData pd = sd.getPocketAt(e.getPos().getX() >> 4, e.getPos().getZ() >> 4);
            if (pd == null || !pd.containsBlock(e.getPos().getX(), e.getPos().getZ())) {
                e.setCanceled(true);
                if (e.getEntity() instanceof Player p) {
                    p.displayClientMessage(Component.translatable("message.pocketdimensions.boundary"), true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!e.getLevel().isClientSide && e.getLevel().dimension().equals(ModDimensions.POCKET_LEVEL)) {
            if (e.getEntity().hasPermissions(2)) return;
            BlockPos target = e.getPos().relative(e.getHitVec().getDirection());
            PocketSavedData sd = PocketSavedData.get(e.getEntity().getServer());
            PocketData pd = sd.getPocketAt(target.getX() >> 4, target.getZ() >> 4);
            if (pd == null || !pd.containsBlock(target.getX(), target.getZ())) {
                e.setCanceled(true);
                e.getEntity().displayClientMessage(Component.translatable("message.pocketdimensions.boundary"), true);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase == TickEvent.Phase.END && !e.level.isClientSide && e.level.dimension().equals(ModDimensions.POCKET_LEVEL) && e.level instanceof ServerLevel sl) {
            if (sl.isRaining() || sl.isThundering()) {
                sl.setWeatherParameters(0, 0, false, false);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartedEvent e) {
        ServerLevel pl = e.getServer().getLevel(ModDimensions.POCKET_LEVEL);
        if (pl != null) {
            for (PocketData pd : PocketSavedData.get(e.getServer()).getAllPockets()) {
                if (pd.isChunkLoaderActive()) {
                    PocketPlotManager.applyChunkLoading(pl, pd, true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onDimChange(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getTo().equals(ModDimensions.POCKET_LEVEL) && e.getEntity() instanceof ServerPlayer sp) {
            syncPocket(sp);
        } else if (e.getFrom().equals(ModDimensions.POCKET_LEVEL) && e.getEntity() instanceof ServerPlayer sp) {
            restoreWorldBorder(sp, sp.server.getLevel(e.getTo()));
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.level().dimension().equals(ModDimensions.POCKET_LEVEL)) {
            syncPocket(sp);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.level().dimension().equals(ModDimensions.POCKET_LEVEL)) {
            syncPocket(sp);
        }
    }

    private static void syncPocket(ServerPlayer sp) {
        PocketSavedData sd = PocketSavedData.get(sp.server);
        PocketData pd = sd.getPocketAt(sp.chunkPosition().x, sp.chunkPosition().z);
        if (pd == null) pd = sd.getPocketByOwner(sp.getUUID());
        if (pd != null) {
            sendPocketWorldBorder(sp, pd);
            ModNetworking.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sp),
                    new PacketSyncSkyWeather(pd.getSkyTheme(), pd.getWeatherVisual())
            );
        }
    }
}
