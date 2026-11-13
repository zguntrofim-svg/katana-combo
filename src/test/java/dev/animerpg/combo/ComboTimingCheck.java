package dev.animerpg.combo;
public final class ComboTimingCheck {
    public static void main(String[] args) {
        assert ComboTiming.next(-1, 0, 0) == 0;
        assert ComboTiming.next(0, 17, 0) == 1;
        assert ComboTiming.next(0, 18, 0) == 0;
        assert ComboTiming.next(1, 10, 0) == 2;
        assert ComboTiming.next(2, 1, 0) == 0;
        for (int s = 0; s < 3; s++) {
            assert ComboTiming.HIT_START[s] > 0;
            assert ComboTiming.HIT_END[s] < ComboTiming.DURATION[s];
            assert !ComboTiming.canQueue(s, 0);
            assert ComboTiming.canQueue(s, ComboTiming.DURATION[s] - 1);
            assert !ComboTiming.canQueue(s, ComboTiming.DURATION[s]);
        }
        System.out.println("Combo timing checks passed");
    }
}
