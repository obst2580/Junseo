package com.junseo.city.vehicle.model;

import static com.junseo.city.vehicle.model.Tex.mix;
import static com.junseo.city.vehicle.model.Tex.rgb;
import static com.junseo.city.vehicle.model.Tex.roundRect;
import static com.junseo.city.vehicle.model.Tex.shade;

/**
 * 캡오버 트럭 (앞이 납작한 1톤 탑차, 청소차): 앞쪽 운전석(캡) + 그 뒤 짐칸 상자.
 * 캡은 {@link BaseDesign} 규칙 그대로, 짐칸·뼈대는 따로 쌓고 칠합니다.
 */
class TruckDesign extends BaseDesign {
    /** 캡 뒤 끝 z, 캡 지붕 높이, 짐칸 아래·위 높이, 짐칸 반폭, 뼈대 반폭 */
    double cabRearZ, cabTop, boxBot, boxTop, boxHW, frameHW;
    int boxColor;
    /** 짐칸 옆 글씨 (없으면 null) */
    String boxText;
    int boxTextColor;
    /** 청소차: 짐칸 위 모서리가 둥글고 뒤가 비스듬히 내려감 */
    boolean compactor;

    double boxTopAt(double z) {
        if (compactor && z < ZR + 0.70) {
            return boxTop - (ZR + 0.70 - z) * 0.9;
        }
        return boxTop;
    }

    @Override
    public double[][] layers(double z) {
        if (z >= cabRearZ) {
            return super.layers(z);
        }
        double[][] out = new double[7][];
        boolean box = z < cabRearZ - 0.10;
        out[0] = new double[]{0.40, box ? boxBot : 0.95, frameHW};
        out[1] = box ? new double[]{boxBot, boxTopAt(z) - (compactor ? 0.12 : 0), boxHW}
                : new double[]{0, -1, 0};
        out[2] = box && compactor ? new double[]{boxTopAt(z) - 0.12, boxTopAt(z), boxHW - 0.10} : new double[]{0, -1, 0};
        for (int i = 3; i < 7; i++) {
            out[i] = new double[]{0, -1, 0};
        }
        return out;
    }

    @Override
    int baseSide(double z, double y) {
        if (z >= cabRearZ) {
            return super.baseSide(z, y);
        }
        if (y >= boxBot) {
            return boxSide(z, y, false);
        }
        for (double axle : new double[]{frontAxle, rearAxle}) {
            if (Math.hypot(z - axle, y - R) < archR + 0.018) {
                return DARK;
            }
        }
        if (y > 0.50 && y < 0.56) {
            return rgb(150, 152, 156);      // 옆 보호대
        }
        return DARK;
    }

    @Override
    public int sideRight(double z, double y) {
        if (z < cabRearZ && y >= boxBot) {
            return livery.side(this, z, y, boxSide(z, y, true), true);
        }
        return super.sideRight(z, y);
    }

    int boxSide(double z, double y, boolean right) {
        double top = boxTopAt(z);
        if (y > top - 0.04 || y < boxBot + 0.05) {
            return rgb(170, 172, 176);       // 위아래 테두리
        }
        if (boxText != null) {
            double h = Math.min(0.32, (top - boxBot) * 0.22);
            if (sideText(boxText, ZR + 0.30, (boxBot + top) / 2 - h / 2, h, z, y, right)) {
                return boxTextColor;
            }
        }
        double rib = ((ZF - z) % 0.30 + 0.30) % 0.30;
        if (!compactor && rib < 0.012) {
            return shade(boxColor, 0.88);
        }
        return shade(boxColor, 0.94 + 0.06 * (y - boxBot) / (top - boxBot));
    }

    /** 앞 그림: 캡 지붕보다 높은 곳은 캡 뒤로 보이는 짐칸 앞면 */
    @Override
    int baseFront(double x, double y) {
        if (y > cabTop - 0.005) {
            return Math.abs(x) > boxHW - 0.04 || y > boxTop - 0.04 ? rgb(170, 172, 176) : shade(boxColor, 0.9);
        }
        return super.baseFront(x, y);
    }

    @Override
    int baseRear(double x, double y) {
        double ax = Math.abs(x);
        if (y >= boxBot) {
            if (ax > boxHW - 0.04 || y > boxTop - 0.04 || y < boxBot + 0.05) {
                return rgb(170, 172, 176);
            }
            if (compactor) {
                return y < boxBot + 0.35 ? rgb(60, 62, 66) : shade(boxColor, 0.92);   // 쓰레기 넣는 입구
            }
            if (ax < 0.012) {
                return rgb(80, 82, 86);      // 두 문 사이
            }
            if (Math.abs(ax - 0.30) < 0.015 && y > boxBot + 0.25 && y < boxTop - 0.25) {
                return rgb(150, 152, 156);   // 문 손잡이 막대
            }
            return shade(boxColor, 0.93);
        }
        if (ax > HW - 0.22 && ax < HW - 0.05 && y > 0.45 && y < 0.58) {
            return y > 0.52 ? RED : AMBER;
        }
        double pd = roundRect(x, y, 0, plateRY, 0.26, 0.055, 0.01);
        if (pd < 0) {
            return pd > -0.007 ? rgb(40, 40, 42) : PLATE;
        }
        return y > 0.40 && y < 0.48 ? rgb(60, 60, 62) : DARK;
    }

    @Override
    int baseTop(double x, double z) {
        if (z < cabRearZ) {
            double ax = Math.abs(x);
            if (z < cabRearZ - 0.10 && ax <= boxHW + 1e-6) {
                double rib = ((ZF - z) % 0.30 + 0.30) % 0.30;
                return rib < 0.012 || ax > boxHW - 0.04 ? rgb(170, 172, 176) : mix(boxColor, rgb(210, 212, 214), compactor ? 0.1 : 0.5);
            }
            return DARK;
        }
        return super.baseTop(x, z);
    }

    // ------------------------------------------------------------------ 차종

    private static void cab(TruckDesign d, double cabLen, double cabH) {
        double zf = d.ZF, zr = d.ZR;
        d.cabRearZ = zf - cabLen;
        d.cabTop = cabH;
        double cowlZ = zf - 0.10, cowlY = 1.15;
        d.topZ = new double[]{zr, d.cabRearZ - 0.001, d.cabRearZ, d.cabRearZ + 0.25, zf - 0.62, zf - 0.40, zf - 0.22, cowlZ, zf - 0.03, zf};
        d.topY = new double[]{1.0, 1.0, cabH - 0.03, cabH, cabH, cabH - 0.12, cabH - 0.45, cowlY, 1.0, 0.90};
        d.botZ = new double[]{zr, zf - 0.30, zf - 0.08, zf};
        d.botY = new double[]{0.40, 0.40, 0.42, 0.48};
        d.wsZ0 = cowlZ;
        d.wsY0 = cowlY;
        d.wsZ1 = zf - 0.40;
        d.wsY1 = cabH - 0.12;
        d.roofEndZ = zr - 1;       // 뒤 판 없음
        d.rgZ0 = zr - 1;
        d.rgZ1 = zr - 2;
        d.rgY0 = 9;
        d.rgY1 = 9;
        d.beltF = 1.22;
        d.beltR = 1.22;
        d.taper = new double[]{0.97, 0.95, 0.93, 0.91};
        d.taperY = new double[]{0.20, 0.40, 0.60};
        d.cornerF = 0.15;
        d.cornerR = 0;
        d.windowRearZ = d.cabRearZ + 0.25;
        d.seams = new double[]{d.cabRearZ + 0.20, zf - 0.20};
        d.handles = new double[]{d.cabRearZ + 0.32};
        d.handleY = 1.15;
        d.creaseY = 0.95;
        d.rockerY = 0.48;
        d.hoodY = 1.12;
        double hw = d.HW;
        d.headX = new double[]{hw - 0.32, hw - 0.04, hw - 0.03, hw - 0.30};
        d.headY = new double[]{0.92, 0.92, 0.76, 0.76};
        d.gY = 0.86;
        d.gHW = hw - 0.42;
        d.gHH = 0.07;
        d.gR = 0.02;
        d.gStyle = 1;
        d.intakeY = 0.58;
        d.intakeHW = hw - 0.08;
        d.intakeHH = 0.08;
        d.fogRX = 0;
        d.plateFY = 0.58;
        d.plateRY = 0.66;
        d.mirror = new double[]{hw - 0.05, 1.35, zf - 0.30, hw + 0.18, 1.62, zf - 0.20};
        d.floor = new double[]{hw - 0.30, 0.42, 0.95, zr + 0.30, zf - 0.20};
    }

    /** 1톤 탑차 (택배): 흰 알루미늄 짐칸 */
    static TruckDesign delivery() {
        TruckDesign d = new TruckDesign();
        d.id = "delivery";
        d.paint = rgb(236, 238, 240);
        d.size(5.10, 1.74, 2.45);
        d.wheels(0.65, 2.64, 0.31, 0.19, 1.48);
        d.tireWidth = 0.195;
        d.archR = 0.37;
        cab(d, 1.55, 1.98);
        d.boxBot = 0.88;
        d.boxTop = 2.45;
        d.boxHW = 0.90;
        d.frameHW = 0.45;
        d.boxColor = rgb(236, 238, 240);
        d.boxText = "EXPRESS";
        d.boxTextColor = rgb(30, 70, 160);
        d.livery = new Livery() {
            @Override
            public int side(BaseDesign c, double z, double y, int col, boolean right) {
                TruckDesign t = (TruckDesign) c;
                if (z < t.cabRearZ && y > t.boxBot + 0.12 && y < t.boxBot + 0.20) {
                    return rgb(30, 70, 160);   // 파란 줄
                }
                return col;
            }
        };
        return d;
    }

    /** 청소차: 주황 압착 짐칸, 흰 캡 */
    static TruckDesign garbage() {
        TruckDesign d = new TruckDesign();
        d.id = "garbage";
        d.paint = rgb(240, 240, 238);
        d.size(6.30, 2.10, 2.80);
        d.wheels(1.05, 3.40, 0.43, 0.254, 1.80);
        d.tireWidth = 0.245;
        d.archR = 0.50;
        cab(d, 1.80, 2.40);
        d.boxBot = 1.05;
        d.boxTop = 2.80;
        d.boxHW = 1.04;
        d.frameHW = 0.50;
        d.boxColor = rgb(236, 120, 32);
        d.boxText = "CLEAN CITY";
        d.boxTextColor = rgb(250, 250, 250);
        d.compactor = true;
        d.mirror = new double[]{1.00, 1.60, d.ZF - 0.35, 1.25, 1.95, d.ZF - 0.22};
        return d;
    }
}
