package com.forgeward.gaugehelper;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** The arithmetic behind the helper lines. Averages only: a real run can come in over or under. */
final class Odds {
    /** Chances are floats: 0.02f is a hair under 0.02, and a bare ceil would call that 51 for 1. */
    private static final double FLOAT_SLACK = 1e-4;

    /** How many crafts it takes, on average, to make target items at this many per craft. Always at least 1. */
    static int crafts(double perCraft, double target) {
        if (perCraft <= 0) return 0;
        return Math.max(1, (int) Math.ceil(target / perCraft - FLOAT_SLACK));
    }

    static String percent(double chance) {
        return plain(chance * 100);
    }

    static String amount(double value) {
        if (value > 0 && value < 0.01) return "<0.01";
        return plain(value);
    }

    private static String plain(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private Odds() {}
}
