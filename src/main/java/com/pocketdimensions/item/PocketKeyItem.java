package com.pocketdimensions.item;

import com.pocketdimensions.dimension.ModDimensions;
import com.pocketdimensions.dimension.PocketData;
import com.pocketdimensions.dimension.PocketSavedData;
import com.pocketdimensions.event.PocketEvents;
import com.pocketdimensions.network.ModNetworking;
import com.pocketdimensions.network.packet.PacketOpenSettings;
import com.pocketdimensions.network.packet.PacketSyncSkyWeather;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PocketKeyItem extends Item {
    public static final int COOLDOWN_TICKS = 800; // 40 seconds (40 * 20 ticks)

    public PocketKeyItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // 1. Shift + Right Click: Settings GUI
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                PocketSavedData sd = PocketSavedData.get(sp.server);
                PocketData pd = sd.getOrCreatePocket(sp);

                ModNetworking.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> sp),
                        new PacketOpenSettings(
                                pd.getOwnerUUID(),
                                pd.getOwnerName(),
                                pd.isChunkLoaderActive(),
                                pd.getSkyTheme(),
                                pd.getWeatherVisual(),
                                pd.isPublic(),
                                new ArrayList<>(pd.getAllowedPlayerNames()),
                                sp.hasPermissions(2)
                        )
                );
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }

        // 2. Exiting Dimension (if inside Pocket Level): NO COOLDOWN!
        if (level.dimension().equals(ModDimensions.POCKET_LEVEL)) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                PocketSavedData.teleportOut(sp, sp.server);
            }
            return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
        }

        // 3. Entering Dimension: check cooldown
        if (player.getCooldowns().isOnCooldown(this)) {
            if (!level.isClientSide) {
                int remainingSec = Math.max(1, Math.round(player.getCooldowns().getCooldownPercent(this, 0.0F) * (COOLDOWN_TICKS / 20.0F)));
                player.sendSystemMessage(Component.translatable("message.pocketdimensions.recharging", remainingSec));
            }
            return InteractionResultHolder.fail(held);
        }

        // Enter Dimension: set return location, teleport, sync visuals & border, apply 40s cooldown
        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            PocketSavedData sd = PocketSavedData.get(sp.server);
            PocketData pd = sd.getOrCreatePocket(sp);

            sd.saveReturnLocation(sp);

            ServerLevel pl = sp.server.getLevel(ModDimensions.POCKET_LEVEL);
            if (pl != null) {
                sp.teleportTo(pl, pd.spawnBlockX() + 0.5, pd.spawnBlockY(), pd.spawnBlockZ() + 0.5, 0.0f, 0.0f);
                sp.fallDistance = 0.0f;
                sp.setDeltaMovement(Vec3.ZERO);
                sp.sendSystemMessage(Component.translatable("message.pocketdimensions.entering"));

                // Sync World Border and Weather/Sky directly
                PocketEvents.sendPocketWorldBorder(sp, pd);
                ModNetworking.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> sp),
                        new PacketSyncSkyWeather(pd.getSkyTheme(), pd.getWeatherVisual())
                );

                // Entering triggers 40 seconds cooldown
                player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            }
        }

        return InteractionResultHolder.sidedSuccess(held, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Shift + Right-Click: Settings GUI").withStyle(ChatFormatting.YELLOW));
    }
}
