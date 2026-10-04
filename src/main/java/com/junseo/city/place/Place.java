package com.junseo.city.place;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

/** 저장된 장소 하나. */
public record Place(int id, PlaceType type, String world, double x, double y, double z, float yaw, float pitch,
                    String name) {

    public static Place of(int id, PlaceType type, Location loc, String name) {
        return new Place(id, type, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), name);
    }

    /** 월드가 없으면 null. */
    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public boolean isBlock(Block block) {
        return block.getWorld().getName().equals(world)
                && block.getX() == (int) Math.floor(x)
                && block.getY() == (int) Math.floor(y)
                && block.getZ() == (int) Math.floor(z);
    }

    public double distanceSquared(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(world)) {
            return Double.MAX_VALUE;
        }
        double dx = loc.getX() - x;
        double dy = loc.getY() - y;
        double dz = loc.getZ() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public String label() {
        return name == null || name.isBlank() ? type.displayName() : name;
    }
}
