package com.kingdomlife.tabremote;

import java.util.ArrayList;

/** Builds IR timing patterns (in microseconds, mark/space alternating, ending on a mark). */
final class Ir {
    static final int FREQ = 38000;

    private Ir() {}

    /** NEC: 9000/4500 header, LSB-first bytes. sub < 0 means "inverted device byte". Used by Vizio. */
    static int[] nec(int dev, int sub, int fn) {
        Pattern p = new Pattern();
        p.pair(9000, 4500);
        p.byteLsb(dev);
        p.byteLsb(sub < 0 ? (~dev & 0xFF) : sub);
        p.byteLsb(fn);
        p.byteLsb(~fn & 0xFF);
        p.mark(560);
        return p.toArray();
    }

    /** Samsung32 / NECx2: 4500/4500 header, then the same byte layout as NEC. Used by Samsung TVs. */
    static int[] samsung32(int dev, int sub, int fn) {
        Pattern p = new Pattern();
        p.pair(4500, 4500);
        p.byteLsb(dev);
        p.byteLsb(sub);
        p.byteLsb(fn);
        p.byteLsb(~fn & 0xFF);
        p.mark(560);
        return p.toArray();
    }

    /**
     * Samsung36: used by Samsung Blu-ray players. 16 address bits, a long gap,
     * then 20 command bits. Bits are given exactly as sent, left to right.
     */
    static int[] samsung36(String bits20) {
        Pattern p = new Pattern();
        p.pair(4500, 4500);
        p.bits("0000010000000000", 535, 470, 1475);
        p.pair(535, 4480);
        p.bits(bits20, 535, 470, 1475);
        p.mark(535);
        return p.toArray();
    }

    private static final class Pattern {
        private final ArrayList<Integer> v = new ArrayList<>();

        void pair(int mark, int space) { v.add(mark); v.add(space); }

        void mark(int mark) { v.add(mark); }

        void byteLsb(int b) {
            for (int i = 0; i < 8; i++) {
                pair(560, ((b >> i) & 1) == 1 ? 1690 : 560);
            }
        }

        void bits(String s, int mark, int zero, int one) {
            for (int i = 0; i < s.length(); i++) {
                pair(mark, s.charAt(i) == '1' ? one : zero);
            }
        }

        int[] toArray() {
            int[] a = new int[v.size()];
            for (int i = 0; i < a.length; i++) a[i] = v.get(i);
            return a;
        }
    }
}
