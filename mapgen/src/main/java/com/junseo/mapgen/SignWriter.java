package com.junseo.mapgen;

import com.junseo.citymap.buildings.CityBuildings;
import com.junseo.citymap.buildings.Placement;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.DyeColor;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * 건물 표지판에 글씨를 씁니다. 생성 단계에서는 표지판 블록만 놓을 수 있어서(글씨는 블록 엔티티),
 * 청크가 처음 만들어져 불러와질 때 이 리스너가 글씨를 채웁니다. 글씨를 쓴 표지판은 고칠 수 없게 밀랍칠합니다.
 */
final class SignWriter implements Listener {
    private final Supplier<CityBuildings> buildings;

    SignWriter(Supplier<CityBuildings> buildings) {
        this.buildings = buildings;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (event.isNewChunk() && event.getWorld().getGenerator() instanceof CityChunkGenerator) {
            write(event.getChunk());
        }
    }

    /** 청크 하나의 표지판 글씨를 씁니다. 쓴 표지판 수 */
    int write(Chunk chunk) {
        World world = chunk.getWorld();
        int n = 0;
        for (Placement.SignSpot spot : buildings.get().signs(chunk.getX(), chunk.getZ())) {
            if (spot.y() <= world.getMinHeight() || spot.y() >= world.getMaxHeight()) {
                continue;
            }
            if (!(world.getBlockAt(spot.x(), spot.y(), spot.z()).getState() instanceof Sign sign)) {
                continue;
            }
            String[] lines = spot.block().textLines();
            DyeColor color = color(spot.block().textColor());
            boolean hanging = spot.block().id().endsWith("hanging_sign");
            for (Side side : hanging ? Side.values() : new Side[]{Side.FRONT}) {
                SignSide s = sign.getSide(side);
                for (int i = 0; i < 4; i++) {
                    s.line(i, Component.text(i < lines.length ? lines[i] : ""));
                }
                s.setColor(color);
                s.setGlowingText(spot.block().textGlows());
            }
            sign.setWaxed(true);
            sign.update(true, false);
            n++;
        }
        return n;
    }

    private static DyeColor color(String name) {
        try {
            return name == null ? DyeColor.BLACK : DyeColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DyeColor.BLACK;
        }
    }
}
