package com.pocketdimensions.client;

import com.pocketdimensions.PocketDimensionsMod;
import com.pocketdimensions.dimension.ModDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

@Mod.EventBusSubscriber(modid = PocketDimensionsMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class PocketClientVisuals {
    public static int currentSkyTheme = 0;
    public static int currentWeatherVisual = 0;
    private static final Random RANDOM = new Random();

    public static void setVisuals(int s, int w) {
        currentSkyTheme = s;
        currentWeatherVisual = w;
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.dimension().equals(ModDimensions.POCKET_LEVEL)) {
            if (currentWeatherVisual == 1) { // Rain: stormy overcast fog
                e.setRed(0.14F); e.setGreen(0.16F); e.setBlue(0.22F);
                return;
            } else if (currentWeatherVisual == 2) { // Snow: soft winter blizzard fog
                e.setRed(0.72F); e.setGreen(0.78F); e.setBlue(0.88F);
                return;
            }
            switch (currentSkyTheme) {
                case 0 -> { e.setRed(0.04F); e.setGreen(0.05F); e.setBlue(0.12F); }
                case 1 -> { e.setRed(0.03F); e.setGreen(0.16F); e.setBlue(0.18F); }
                case 2 -> { e.setRed(0.20F); e.setGreen(0.06F); e.setBlue(0.18F); }
                case 3 -> { e.setRed(0.08F); e.setGreen(0.02F); e.setBlue(0.14F); }
                case 4 -> { e.setRed(0.22F); e.setGreen(0.14F); e.setBlue(0.05F); }
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null && mc.level.dimension().equals(ModDimensions.POCKET_LEVEL)) {
            Player p = mc.player;
            Level l = mc.level;

            if (currentWeatherVisual == 1) { // Gentle Rain
                for (int i = 0; i < 28; i++) {
                    double rx = p.getX() + (RANDOM.nextDouble() - 0.5) * 24.0;
                    double rz = p.getZ() + (RANDOM.nextDouble() - 0.5) * 24.0;
                    double ry = p.getY() + RANDOM.nextDouble() * 12.0 + 2.0;
                    l.addParticle(ParticleTypes.FALLING_WATER, rx, ry, rz, 0.0, -0.6, 0.0);
                    if (RANDOM.nextFloat() < 0.35F) {
                        l.addParticle(ParticleTypes.RAIN, rx, p.getY() + RANDOM.nextDouble() * 1.5, rz, 0.0, 0.0, 0.0);
                    }
                }
                if (RANDOM.nextInt(14) == 0) {
                    l.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.25F, 1.0F, false);
                }
            } else if (currentWeatherVisual == 2) { // Cozy Snowfall
                for (int i = 0; i < 35; i++) {
                    double sx = p.getX() + (RANDOM.nextDouble() - 0.5) * 24.0;
                    double sz = p.getZ() + (RANDOM.nextDouble() - 0.5) * 24.0;
                    double sy = p.getY() + RANDOM.nextDouble() * 14.0 + 1.0;
                    double vx = (RANDOM.nextDouble() - 0.5) * 0.05;
                    double vy = -0.07 - RANDOM.nextDouble() * 0.05;
                    double vz = (RANDOM.nextDouble() - 0.5) * 0.05;
                    l.addParticle(ParticleTypes.SNOWFLAKE, sx, sy, sz, vx, vy, vz);
                }
            }
        }
    }
}
