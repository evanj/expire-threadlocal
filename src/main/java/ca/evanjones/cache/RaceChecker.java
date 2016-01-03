package ca.evanjones.cache;

/** Attempts to surface data races. */
public class RaceChecker {
    private long a;
    private long b;

    public RaceChecker(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException("value must be positive");
        }
        a = value;
        b = -value;
    }

    // should always return value (positive).
    public long swapPart1() {
        long value = 0;
        if (a > 0) {
            value = a;
            a = 0;
            b = value + 1;
        } else {
            value = b;
            b = 0;
            a = value + 1;
        }
        return value;
    }

    public void swapPart2(long value) {
        if (a == 0) {
            a = value;
            b = -value;
        } else {
            b = value;
            a = -value;

        }
    }

    public long sanityCheck() {
        if (a > 0) {
            return a;
        }
        return b;
    }

    private static void assertPositive(long v) {
        if (v <= 0) {
            throw new RuntimeException("not positive");
        }
    }

    public void check() {
        long v1 = sanityCheck();
        assertPositive(v1);
        long v2 = swapPart1();
        assertPositive(v2);
        swapPart2(v2);
        long v3 = sanityCheck();
        assertPositive(v3);
    }
}
