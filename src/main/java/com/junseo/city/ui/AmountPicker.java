package com.junseo.city.ui;

import org.bukkit.entity.Player;

import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * 입력창 없이 버튼만으로 금액을 고르는 화면.
 * [+1천] [+1만] [+10만] [+100만] / [-1천] [-1만] [초기화] [최대] → [확인]
 */
public final class AmountPicker {
    private static final long[] STEPS = {1_000, 10_000, 100_000, 1_000_000};

    private final UiService ui;
    private final LongFunction<String> money;

    public AmountPicker(UiService ui, LongFunction<String> money) {
        this.ui = ui;
        this.money = money;
    }

    /**
     * @param title   화면 제목
     * @param hint    설명 한 줄
     * @param max     고를 수 있는 최대 금액 (보통 잔액)
     * @param confirm 확인을 누르면 고른 금액으로 호출
     * @param back    뒤로를 누르면 호출
     */
    public void open(Player player, String title, String hint, long max, LongConsumer confirm, Runnable back) {
        render(player, title, hint, max, new long[]{0}, null, confirm, back);
    }

    private void render(Player player, String title, String hint, long max, long[] amount, String error,
                        LongConsumer confirm, Runnable back) {
        Screen s = new Screen(title).columns(4).buttonWidth(70);
        s.line("<gray>" + hint);
        s.line("<white>금액: <gold><bold>" + money.apply(amount[0]) + "</bold></gold>  <dark_gray>(최대 " + money.apply(max) + ")");
        if (error != null) {
            s.line("<red>" + error);
        }
        for (long step : STEPS) {
            s.button("<green>+" + shortMoney(step), () -> {
                amount[0] = Math.min(max, amount[0] + step);
                render(player, title, hint, max, amount, null, confirm, back);
            });
        }
        s.button("<red>-1천", () -> {
            amount[0] = Math.max(0, amount[0] - 1_000);
            render(player, title, hint, max, amount, null, confirm, back);
        });
        s.button("<red>-1만", () -> {
            amount[0] = Math.max(0, amount[0] - 10_000);
            render(player, title, hint, max, amount, null, confirm, back);
        });
        s.button("<gray>초기화", () -> {
            amount[0] = 0;
            render(player, title, hint, max, amount, null, confirm, back);
        });
        s.button("<yellow>최대", () -> {
            amount[0] = max;
            render(player, title, hint, max, amount, null, confirm, back);
        });
        s.button("<aqua><bold>확인", () -> {
            if (amount[0] <= 0) {
                render(player, title, hint, max, amount, "금액을 골라 주세요.", confirm, back);
            } else {
                confirm.accept(amount[0]);
            }
        });
        s.exit("◀ 뒤로", back);
        ui.show(player, s);
    }

    static String shortMoney(long amount) {
        if (amount >= 10_000 && amount % 10_000 == 0) {
            return (amount / 10_000) + "만";
        }
        if (amount >= 1_000 && amount % 1_000 == 0) {
            return (amount / 1_000) + "천";
        }
        return String.valueOf(amount);
    }
}
