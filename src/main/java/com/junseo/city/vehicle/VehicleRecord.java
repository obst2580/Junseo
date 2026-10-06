package com.junseo.city.vehicle;

import com.junseo.city.vehicle.model.CarModels;

import java.util.UUID;

/**
 * 플레이어가 가진 차 한 대 (DB vehicles 한 줄). 여러 스레드에서 읽고 써서 모든 메서드가 동기화되어 있습니다.
 */
public final class VehicleRecord {
    /** garage: 공영 차고에 있음, out: 길에 나와 있음, impound: 견인소에 압류됨 */
    public enum State { GARAGE, OUT, IMPOUND }

    private final UUID id;
    private final UUID owner;
    private final CarType type;
    private final CarModels.Paint paint;
    private final String plate;
    private final long createdAt;
    private int health;
    private State state;
    private String garage;

    public VehicleRecord(UUID id, UUID owner, CarType type, CarModels.Paint paint, String plate, int health, State state,
                         String garage, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.type = type;
        this.paint = paint;
        this.plate = plate;
        this.health = health;
        this.state = state;
        this.garage = garage;
        this.createdAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public UUID owner() {
        return owner;
    }

    public CarType type() {
        return type;
    }

    public CarModels.Paint paint() {
        return paint;
    }

    public String plate() {
        return plate;
    }

    public long createdAt() {
        return createdAt;
    }

    public synchronized int health() {
        return health;
    }

    public synchronized void setHealth(int health) {
        this.health = Math.max(0, Math.min(100, health));
    }

    public synchronized State state() {
        return state;
    }

    public synchronized String garage() {
        return garage;
    }

    public synchronized void setState(State state, String garage) {
        this.state = state;
        if (garage != null) {
            this.garage = garage;
        }
    }

    /** 이름 (예: 흰색 중형 세단) */
    public String label() {
        return (CarModels.hasPaints(type.modelId()) ? paint.label + " " : "") + type.displayName();
    }
}
