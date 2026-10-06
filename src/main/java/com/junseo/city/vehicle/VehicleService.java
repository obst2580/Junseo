package com.junseo.city.vehicle;

import com.junseo.city.JunseoCity;
import com.junseo.city.data.Database;
import com.junseo.city.vehicle.model.CarModels;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 플레이어 차 목록 (DB vehicles). 켤 때 전부 읽어 메모리에 두고, 바뀔 때마다 DB 에 씁니다.
 * 서버가 꺼졌다 켜지면 길에 나와 있던 차는 모두 마지막 차고로 돌아갑니다.
 */
public final class VehicleService {
    /** 자가용 번호판 가운데 글자 */
    private static final String PLATE_LETTERS = "가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주";

    private final JunseoCity plugin;
    private final Database db;
    private final Map<UUID, VehicleRecord> byId = new ConcurrentHashMap<>();
    private final Map<String, VehicleRecord> byPlate = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public VehicleService(JunseoCity plugin, Database db) {
        this.plugin = plugin;
        this.db = db;
    }

    /** 켤 때 한 번 (끝날 때까지 기다림) */
    public void load() {
        List<VehicleRecord> rows = db.submit(c -> {
            List<VehicleRecord> out = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM vehicles"); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CarType type = CarType.byId(rs.getString("model"));
                    if (type == null) {
                        continue;
                    }
                    VehicleRecord.State state;
                    try {
                        state = VehicleRecord.State.valueOf(rs.getString("state").toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        state = VehicleRecord.State.GARAGE;
                    }
                    out.add(new VehicleRecord(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("owner")), type,
                            CarModels.Paint.byId(rs.getString("paint")), rs.getString("plate"), rs.getInt("health"), state,
                            rs.getString("garage"), rs.getLong("created_at")));
                }
            }
            return out;
        }).join();
        for (VehicleRecord r : rows) {
            if (r.state() == VehicleRecord.State.OUT) {
                r.setState(VehicleRecord.State.GARAGE, null);
                save(r);
            }
            byId.put(r.id(), r);
            byPlate.put(normalize(r.plate()), r);
        }
        plugin.getLogger().info("자동차 " + rows.size() + "대 불러옴");
    }

    public VehicleRecord byId(UUID id) {
        return id == null ? null : byId.get(id);
    }

    public VehicleRecord byPlate(String plate) {
        return plate == null ? null : byPlate.get(normalize(plate));
    }

    public List<VehicleRecord> ownedBy(UUID owner) {
        List<VehicleRecord> out = new ArrayList<>();
        for (VehicleRecord r : byId.values()) {
            if (r.owner().equals(owner)) {
                out.add(r);
            }
        }
        out.sort(Comparator.comparingLong(VehicleRecord::createdAt));
        return out;
    }

    /** 새 차 (대리점에서 산 차): 그 차고에 넣어 둠 */
    public VehicleRecord create(UUID owner, CarType type, CarModels.Paint paint, String garage) {
        String plate;
        synchronized (random) {
            do {
                plate = (10 + random.nextInt(90)) + "" + PLATE_LETTERS.charAt(random.nextInt(PLATE_LETTERS.length()))
                        + " " + (1000 + random.nextInt(9000));
            } while (byPlate.containsKey(normalize(plate)));
        }
        String newPlate = plate;
        VehicleRecord r = new VehicleRecord(UUID.randomUUID(), owner, type, paint, newPlate, 100, VehicleRecord.State.GARAGE,
                garage, System.currentTimeMillis());
        byId.put(r.id(), r);
        byPlate.put(normalize(newPlate), r);
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO vehicles (id, owner, model, paint, plate, health, state, garage, created_at) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, r.id().toString());
                ps.setString(2, owner.toString());
                ps.setString(3, type.id());
                ps.setString(4, paint.id());
                ps.setString(5, newPlate);
                ps.setInt(6, 100);
                ps.setString(7, "garage");
                ps.setString(8, garage);
                ps.setLong(9, r.createdAt());
                ps.executeUpdate();
            }
            return null;
        });
        return r;
    }

    /** 상태·체력·차고를 DB 에 씀 */
    public void save(VehicleRecord r) {
        int health = r.health();
        String state = r.state().name().toLowerCase(java.util.Locale.ROOT), garage = r.garage();
        db.execute(c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE vehicles SET health = ?, state = ?, garage = ? WHERE id = ?")) {
                ps.setInt(1, health);
                ps.setString(2, state);
                ps.setString(3, garage);
                ps.setString(4, r.id().toString());
                ps.executeUpdate();
            }
            return null;
        });
    }

    static String normalize(String plate) {
        return plate.replace(" ", "");
    }
}
