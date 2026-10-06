package com.junseo.city.vehicle;

import com.junseo.city.JunseoCity;
import com.junseo.citymap.buildings.CityBuildings;
import com.junseo.citymap.buildings.Placement;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 공영 차고 (도시 생성기가 지은 공영주차장·주차타워·렌터카 차고지, 지도에 P).
 * 차를 꺼내는 자리(주차 칸)는 건물 데이터에 들어 있습니다. 도시 배치는 서버가 켜진 뒤 따로 계산되므로 처음 쓸 때 만듭니다.
 */
public final class GarageService {
    /** 차고 하나: 이름, 월드 상자 (x0, z0, x1, z1), 주차 칸 (월드 좌표, 바퀴가 닿는 높이 위 칸) */
    public record Garage(String name, double x0, double z0, double x1, double z1, List<Placement.CarSpot> spots) {
        public double cx() {
            return (x0 + x1) / 2;
        }

        public double cz() {
            return (z0 + z1) / 2;
        }

        /** 상자까지 거리 (안이면 0) */
        public double distance(double x, double z) {
            double dx = Math.max(0, Math.max(x0 - x, x - x1)), dz = Math.max(0, Math.max(z0 - z, z - z1));
            return Math.hypot(dx, dz);
        }
    }

    /** 차를 꺼내거나 넣을 수 있는 거리 (차고 상자에서) */
    public static final double NEAR = 12;

    private final JunseoCity plugin;
    private volatile List<Garage> garages;

    public GarageService(JunseoCity plugin) {
        this.plugin = plugin;
    }

    /** 차고 목록 (도시 배치가 아직이면 빈 목록) */
    public List<Garage> all() {
        List<Garage> g = garages;
        if (g != null) {
            return g;
        }
        CityBuildings b = plugin.map().buildings();
        if (b == null || plugin.map().terrain() == null) {
            return List.of();
        }
        synchronized (this) {
            if (garages == null) {
                int baseY = plugin.map().terrain().groundY() + 1;
                List<Garage> out = new ArrayList<>();
                for (Placement p : b.garages()) {
                    double[] bb = p.bounds();
                    out.add(new Garage(p.name, bb[0], bb[1], bb[2], bb[3], p.carSpots(baseY)));
                }
                garages = List.copyOf(out);
                plugin.getLogger().info("공영 차고 " + out.size() + "곳 (주차 칸 " + out.stream().mapToInt(x -> x.spots().size()).sum() + "개)");
            }
            return garages;
        }
    }

    public Garage byName(String name) {
        for (Garage g : all()) {
            if (g.name().equals(name)) {
                return g;
            }
        }
        return null;
    }

    /** 가장 가까운 차고 (maxDist 안, 없으면 null) */
    public Garage nearest(Location at, double maxDist) {
        Garage best = null;
        double bd = maxDist;
        for (Garage g : all()) {
            double d = g.distance(at.getX(), at.getZ());
            if (d <= bd) {
                bd = d;
                best = g;
            }
        }
        return best;
    }

    /** 빈 주차 칸 (다른 차가 2.5칸 안에 없는 곳). 칸 정보가 없으면 차고 가운데 */
    public Location freeSpot(Garage g, World world, List<Location> occupied) {
        for (Placement.CarSpot s : g.spots()) {
            boolean free = true;
            for (Location o : occupied) {
                if (o.getWorld() == world && Math.hypot(o.getX() - s.x(), o.getZ() - s.z()) < 2.5 && Math.abs(o.getY() - s.y()) < 3) {
                    free = false;
                    break;
                }
            }
            if (free) {
                return new Location(world, s.x(), s.y(), s.z(), s.yaw(), 0);
            }
        }
        if (g.spots().isEmpty()) {
            int y = world.getHighestBlockYAt((int) Math.floor(g.cx()), (int) Math.floor(g.cz())) + 1;
            return new Location(world, g.cx(), y, g.cz());
        }
        return null;
    }
}
