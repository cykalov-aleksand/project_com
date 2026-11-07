package com.example.projectCom.setup_controller;

import java.util.ArrayList;
import java.util.List;

public class SyncPeriodOption {
    private final String displayName;
    private final int divisor;          // в единицах 0,1 мс: 0,1 мс -> 1, 500 мс -> 5000

    private SyncPeriodOption(String displayName, int divisor) {
        this.displayName = displayName;
        this.divisor = divisor;
    }

    private static final List<SyncPeriodOption> ALL = buildAll();

    private static List<SyncPeriodOption> buildAll() {
        List<SyncPeriodOption> list = new ArrayList<>();
        list.add(new SyncPeriodOption("0,1 мс", 1));            // минимальный шаг 0,1 мс
        for (int ms = 1; ms <= 500; ms++) {                     // дальше шаг 1 мс
            list.add(new SyncPeriodOption(ms + " мс", ms * 10));
        }
        return List.copyOf(list);
    }

    public static List<SyncPeriodOption> values() {
        return ALL;
    }

    public String getDisplayName() { return displayName; }
    public int getDivisor() { return divisor; }

    public static final int MIN_DIVISOR = 1;
    public static final int MAX_DIVISOR = 5000;

    // Поиск константы по делителю — для проверки после чтения из базы
    public static boolean isValidDivisor(int divisor) {
        return divisor >= MIN_DIVISOR && divisor <= MAX_DIVISOR;
    }
}
