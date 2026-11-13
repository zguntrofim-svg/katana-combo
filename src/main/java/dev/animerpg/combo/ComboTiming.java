package dev.animerpg.combo;

/** All times are server ticks (20 ticks = one second at normal TPS). */
public final class ComboTiming {
    public static final int RESET = 18;
    public static final int[] DURATION = {10, 12, 16};
    public static final int[] HIT_START = {3, 4, 6};
    public static final int[] HIT_END = {5, 6, 8};
    public static final float[] DAMAGE = {1f, 1f, 1.5f};
    public static int next(int previous, long now, long ended) {
        return previous < 0 || previous == 2 || now - ended >= RESET ? 0 : previous + 1;
    }
    public static boolean canQueue(int step, long age) {
        return age >= DURATION[step] - 4 && age < DURATION[step];
    }
    private ComboTiming() {}
}
