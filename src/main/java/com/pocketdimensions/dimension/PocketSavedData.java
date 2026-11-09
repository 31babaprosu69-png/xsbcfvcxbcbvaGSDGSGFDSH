package com.pocketdimensions.dimension;

import com.pocketdimensions.event.PocketEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PocketSavedData extends SavedData {
    private static final String DATA_NAME = "pocketdimensions_data";
    private int nextPlotIndex = 0;
    private final Map<UUID, PocketData> pockets = new HashMap<>();
    private final Map<UUID, PlayerReturnPos> returnLocations = new HashMap<>();

    public static class PlayerReturnPos {
        public final ResourceKey<Level> dim;
        public final double x, y, z;
        public final float yaw, pitch;

        public PlayerReturnPos(ResourceKey<Level> dim, double x, double y, double z, float yaw, float pitch) {
            this.dim = dim;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dim", dim.location().toString());
            tag.putDouble("x", x);
            tag.putDouble("y", y);
            tag.putDouble("z", z);
            tag.putFloat("yaw", yaw);
            tag.putFloat("pitch", pitch);
            return tag;
        }

        public static PlayerReturnPos load(CompoundTag tag) {
            ResourceKey<Level> d = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(tag.getString("dim")));
            return new PlayerReturnPos(d, tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"), tag.getFloat("yaw"), tag.getFloat("pitch"));
        }
    }

    public PocketSavedData() {}

    public static PocketSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PocketSavedData::load, PocketSavedData::new, DATA_NAME);
    }

    public Collection<PocketData> getAllPockets() { return pockets.values(); }
    public PocketData getPocketByOwner(UUID u) { return pockets.get(u); }

    public PocketData getPocketByOwnerName(String name) {
        for (PocketData pd : pockets.values()) {
            if (pd.getOwnerName().equalsIgnoreCase(name)) return pd;
        }
        return null;
    }

    public PocketData getPocketAt(int cx, int cz) {
        for (PocketData pd : pockets.values()) {
            if (Math.abs(cx - pd.getCenterChunkX()) <= 1 && Math.abs(cz - pd.getCenterChunkZ()) <= 1) return pd;
        }
        return null;
    }

    public void saveReturnLocation(ServerPlayer player) {
        returnLocations.put(player.getUUID(), new PlayerReturnPos(
                player.level().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()
        ));
        setDirty();
    }

    public PlayerReturnPos getReturnLocation(UUID uuid) {
        return returnLocations.get(uuid);
    }

    public PocketData getOrCreatePocket(ServerPlayer p) {
        UUID u = p.getUUID();
        if (pockets.containsKey(u)) {
            PocketData pd = pockets.get(u);
            pd.setOwnerName(p.getGameProfile().getName());
            setDirty();
            return pd;
        }

        int idx = nextPlotIndex++;
        int gridX = (idx % 100) * 64;
        int gridZ = (idx / 100) * 64;

        PocketData nd = new PocketData(u, p.getGameProfile().getName(), idx, gridX, gridZ);
        pockets.put(u, nd);
        setDirty();

        ServerLevel pl = p.server.getLevel(ModDimensions.POCKET_LEVEL);
        if (pl != null) {
            PocketPlotManager.generateStarterPlatform(pl, nd);
        }
        return nd;
    }

    public void setChunkLoader(UUID u, boolean active, MinecraftServer server) {
        PocketData pd = pockets.get(u);
        if (pd != null) {
            pd.setChunkLoaderActive(active);
            setDirty();
            ServerLevel pl = server.getLevel(ModDimensions.POCKET_LEVEL);
            if (pl != null) {
                PocketPlotManager.applyChunkLoading(pl, pd, active);
            }
        }
    }

    public boolean deletePocket(UUID u, MinecraftServer server) {
        PocketData pd = pockets.remove(u);
        if (pd == null) return false;

        ServerLevel pl = server.getLevel(ModDimensions.POCKET_LEVEL);
        if (pl != null) {
            PocketPlotManager.applyChunkLoading(pl, pd, false);
            for (ServerPlayer player : pl.players()) {
                if (pd.containsBlock(player.getBlockX(), player.getBlockZ())) {
                    teleportOut(player, server);
                    player.sendSystemMessage(Component.translatable("message.pocketdimensions.deleted"));
                }
            }
            PocketPlotManager.clearPlot(pl, pd);
        }
        setDirty();
        return true;
    }

    public static void teleportOut(ServerPlayer player, MinecraftServer server) {
        PocketSavedData sd = PocketSavedData.get(server);
        PlayerReturnPos ret = sd.getReturnLocation(player.getUUID());

        ServerLevel targetLevel = null;
        double tx, ty, tz;
        float yaw = 0.0F, pitch = 0.0F;

        if (ret != null) {
            targetLevel = server.getLevel(ret.dim);
            tx = ret.x;
            ty = ret.y;
            tz = ret.z;
            yaw = ret.yaw;
            pitch = ret.pitch;
        }

        if (targetLevel == null) {
            targetLevel = server.overworld();
            BlockPos sp = targetLevel.getSharedSpawnPos();
            tx = sp.getX() + 0.5;
            ty = sp.getY() + 1.0;
            tz = sp.getZ() + 0.5;
        }

        if (ty <= targetLevel.getMinBuildHeight()) {
            BlockPos sp = targetLevel.getSharedSpawnPos();
            tx = sp.getX() + 0.5;
            ty = sp.getY() + 1.0;
            tz = sp.getZ() + 0.5;
        }

        player.teleportTo(targetLevel, tx, ty, tz, yaw, pitch);
        player.fallDistance = 0.0f;
        player.setDeltaMovement(Vec3.ZERO);

        // Restore normal world border
        PocketEvents.restoreWorldBorder(player, targetLevel);

        player.sendSystemMessage(Component.translatable("message.pocketdimensions.leaving"));
    }

    public static PocketSavedData load(CompoundTag tag) {
        PocketSavedData sd = new PocketSavedData();
        sd.nextPlotIndex = tag.getInt("NextPlot");
        ListTag list = tag.getList("Pockets", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            PocketData pd = PocketData.load(list.getCompound(i));
            sd.pockets.put(pd.getOwnerUUID(), pd);
        }

        if (tag.contains("ReturnLocations", Tag.TAG_LIST)) {
            ListTag retList = tag.getList("ReturnLocations", Tag.TAG_COMPOUND);
            for (int i = 0; i < retList.size(); i++) {
                CompoundTag retTag = retList.getCompound(i);
                sd.returnLocations.put(retTag.getUUID("uuid"), PlayerReturnPos.load(retTag));
            }
        }
        return sd;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("NextPlot", nextPlotIndex);
        ListTag list = new ListTag();
        for (PocketData pd : pockets.values()) list.add(pd.save(new CompoundTag()));
        tag.put("Pockets", list);

        ListTag retList = new ListTag();
        for (Map.Entry<UUID, PlayerReturnPos> entry : returnLocations.entrySet()) {
            CompoundTag retTag = entry.getValue().save();
            retTag.putUUID("uuid", entry.getKey());
            retList.add(retTag);
        }
        tag.put("ReturnLocations", retList);

        return tag;
    }
}
