package com.junseo.city.vehicle;

import com.junseo.city.logic.Job;
import com.junseo.city.vehicle.model.CarDesign;
import com.junseo.city.vehicle.model.CarModels;

import java.util.Locale;

/**
 * 차 종류와 성능. 속도 단위는 블록/틱 (1.0 = 시속 72km).
 * 일반 차는 대리점에서 사고(가격, 색 선택), 직업 차는 그 직업으로 출근하면 열쇠를 받습니다.
 * 가격은 지금 설정의 돈 단위(정착금 2,500)에 맞춘 기본값이고 config.yml prices.car_* 로 바꿉니다.
 * 비율은 기획서(경차 1,500만 : 세단 3,500만 : SUV 5,000만 : 스포츠카 1.5억)를 따름
 */
public enum CarType {
    COMPACT("compact", "경차", 0.80, 0.019, 5.0, "morning", 6_000L, null),
    SEDAN("sedan", "중형 세단", 1.00, 0.022, 4.2, "sedan", 14_000L, null),
    LARGE("large", "대형 세단", 1.05, 0.023, 4.0, "grandeur", 22_000L, null),
    SUV("suv", "SUV", 0.95, 0.021, 4.0, "suv", 20_000L, null),
    SPORTS("sports", "스포츠카", 1.40, 0.032, 3.8, "sports", 60_000L, null),
    POLICE("police", "경찰차", 1.20, 0.028, 4.2, "police", 0, Job.POLICE),
    AMBULANCE("ambulance", "구급차", 1.00, 0.020, 3.6, "ambulance", 0, Job.EMS),
    TAXI("taxi", "택시", 1.00, 0.022, 4.2, "taxi", 0, null),
    DELIVERY("delivery", "택배 트럭", 0.80, 0.016, 3.4, "delivery", 0, Job.DELIVERY),
    GARBAGE("garbage", "청소차", 0.65, 0.013, 3.0, "garbage", 0, null),
    CASHVAN("cashvan", "현금수송차", 0.90, 0.018, 3.4, "cashvan", 0, null);

    private final String id;
    private final String displayName;
    private final double maxSpeed;
    private final double acceleration;
    private final double turnDegrees;
    private final String modelId;
    private final long price;
    private final Job job;

    CarType(String id, String displayName, double maxSpeed, double acceleration, double turnDegrees, String modelId, long price, Job job) {
        this.id = id;
        this.displayName = displayName;
        this.maxSpeed = maxSpeed;
        this.acceleration = acceleration;
        this.turnDegrees = turnDegrees;
        this.modelId = modelId;
        this.price = price;
        this.job = job;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public double maxSpeed() {
        return maxSpeed;
    }

    public double acceleration() {
        return acceleration;
    }

    public double turnDegrees() {
        return turnDegrees;
    }

    /** 리소스팩 모델 이름 */
    public String modelId() {
        return modelId;
    }

    public CarDesign design() {
        return CarModels.design(modelId);
    }

    /** 대리점 가격 (0 이면 팔지 않음: 직업 차) */
    public long price() {
        return price;
    }

    /** 대리점에서 파는 일반 차 */
    public boolean forSale() {
        return price > 0;
    }

    /** 이 직업 차를 몰 수 있는 직업 (일반 차·회사 차는 null) */
    public Job job() {
        return job;
    }

    public static CarType byId(String id) {
        if (id == null) {
            return null;
        }
        for (CarType type : values()) {
            if (type.id.equals(id.toLowerCase(Locale.ROOT))) {
                return type;
            }
        }
        return null;
    }
}
