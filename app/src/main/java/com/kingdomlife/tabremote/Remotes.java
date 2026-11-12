package com.kingdomlife.tabremote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every remote the app knows about.
 *
 * Code sources:
 *  - Samsung TV: IRDB (probonopd/irdb) codes/Samsung/TV/7,7.csv  (NECx2, device 7, subdevice 7)
 *  - Vizio TV:   IRDB codes/Vizio/Unknown_Vizio/4,-1.csv           (NEC, device 4)
 *  - Roku: Flipper-IRDB Roku3, TCL_Roku_TV, Roku_Standalone, Roku_4k, Roku.ir; IRDB Roku 190,239
 *  - Samsung Blu-ray: Flipper-IRDB captures of BD-D5300, BD-D7500, BD-E5300, UBD-K8500
 *    (all four models send identical codes)
 *
 * To add a device: copy one of the blocks below and add it to all().
 */
final class Remotes {
    static final int NORMAL = 0, POWER = 1, ACCENT = 2, NAV = 3;
    static final String KEYPAD = "__keypad__";

    static final class Key {
        final String label;
        final int[] pattern;
        final boolean repeat;
        final int style;

        Key(String label, int[] pattern, boolean repeat, int style) {
            this.label = label; this.pattern = pattern; this.repeat = repeat; this.style = style;
        }

        boolean isKeypad() { return pattern == null && KEYPAD.equals(label); }
    }

    static final class Device {
        final String name;
        /** panels -> rows -> keys (a null key is an empty spacer) */
        final List<List<List<Key>>> panels = new ArrayList<>();
        final Map<String, Key> keypad = new LinkedHashMap<>();
        Key power;
        /** Key the timing test sends; POWER unless set. */
        Key test;

        Device(String name) { this.name = name; }

        Device panel() { panels.add(new ArrayList<List<Key>>()); return this; }

        Device row(Key... keys) {
            List<Key> r = new ArrayList<>();
            for (Key k : keys) r.add(k);
            panels.get(panels.size() - 1).add(r);
            return this;
        }
    }

    static Key k(String label, int[] p) { return new Key(label, p, false, NORMAL); }
    static Key rep(String label, int[] p) { return new Key(label, p, true, NORMAL); }
    static Key nav(String label, int[] p) { return new Key(label, p, true, NAV); }
    static Key ok(String label, int[] p) { return new Key(label, p, false, ACCENT); }
    static Key pwr(int[] p) { return new Key("POWER", p, false, POWER); }
    static Key keypadKey() { return new Key(KEYPAD, null, false, NORMAL); }

    static List<Device> all() {
        List<Device> list = new ArrayList<>();
        list.add(samsungTv());
        list.add(vizioTv());
        list.add(samsungBluRay());
        list.add(roku(0));
        return list;
    }

    // ---------------------------------------------------------------- Samsung TV
    private static int[] s(int fn) { return Ir.samsung32(7, 7, fn); }

    static Device samsungTv() {
        Device d = new Device("Samsung TV");
        d.power = pwr(s(2));
        d.panel()
                .row(d.power, k("SOURCE", s(1)))
                .row(k("HDMI 1", s(233)), k("HDMI 2", s(190)))
                .row(k("HDMI 3", s(194)), k("HDMI 4", s(197)))
                .row(k("HOME", s(121)), k("MENU", s(26)))
                .row(k("GUIDE", s(79)), k("INFO", s(31)))
                .row(k("TOOLS", s(75)), keypadKey());
        d.panel()
                .row(null, nav("▲", s(96)), null)
                .row(nav("◀", s(101)), ok("OK", s(104)), nav("▶", s(98)))
                .row(null, nav("▼", s(97)), null)
                .row(k("RETURN", s(88)), k("EXIT", s(45)));
        d.panel()
                .row(rep("VOL +", s(7)), rep("CH +", s(18)))
                .row(rep("VOL −", s(11)), rep("CH −", s(16)))
                .row(k("MUTE", s(15)), k("PRE-CH", s(19)))
                .row(k("CH LIST", s(107)));
        int[] digits = {17, 4, 5, 6, 8, 9, 10, 12, 13, 14};
        for (int i = 1; i <= 9; i++) d.keypad.put(String.valueOf(i), k(String.valueOf(i), s(digits[i])));
        d.keypad.put("PRE-CH", k("PRE-CH", s(19)));
        d.keypad.put("0", k("0", s(digits[0])));
        d.keypad.put("ENTER", ok("OK", s(104)));
        return d;
    }

    // ---------------------------------------------------------------- Vizio TV
    private static int[] v(int fn) { return Ir.nec(4, -1, fn); }

    static Device vizioTv() {
        Device d = new Device("Vizio TV");
        d.power = pwr(v(8));
        d.panel()
                .row(d.power, k("INPUT", v(47)))
                .row(k("HDMI", v(198)), k("MENU", v(67)))
                .row(k("INFO", v(28)), k("LAST", v(26)))
                .row(keypadKey());
        d.panel()
                .row(null, nav("▲", v(69)), null)
                .row(nav("◀", v(71)), ok("OK", v(68)), nav("▶", v(72)))
                .row(null, nav("▼", v(70)), null)
                .row(k("EXIT", v(73)));
        d.panel()
                .row(rep("VOL +", v(2)), rep("CH +", v(0)))
                .row(rep("VOL −", v(3)), rep("CH −", v(1)))
                .row(k("MUTE", v(9)));
        for (int i = 1; i <= 9; i++) d.keypad.put(String.valueOf(i), k(String.valueOf(i), v(16 + i)));
        d.keypad.put("DASH", k("—", v(255)));
        d.keypad.put("0", k("0", v(16)));
        d.keypad.put("ENTER", ok("OK", v(68)));
        return d;
    }

    // ---------------------------------------------------------------- Samsung Blu-ray
    private static int[] b(String bits) { return Ir.samsung36(bits); }

    static Device samsungBluRay() {
        Device d = new Device("Blu-ray");
        d.power = pwr(b("11100000000011111111"));
        d.panel()
                .row(d.power, k("EJECT", b("11101000000001111111")))
                .row(k("HOME", b("11100110100010010111")), k("SMART HUB", b("11100001110011100011")))
                .row(k("DISC MENU", b("11101011100001000111")), k("POP-UP", b("11100000010011111011")))
                .row(k("INFO", b("11100111100010000111")), k("TOOLS", b("11100101110010100011")))
                .row(k("NETFLIX", b("11100111110010000011")), keypadKey());
        d.panel()
                .row(null, nav("▲", b("11100001100011100111")), null)
                .row(nav("◀", b("11101101100000100111")), ok("OK", b("11100011100011000111")), nav("▶", b("11100101100010100111")))
                .row(null, nav("▼", b("11101001100001100111")), null)
                .row(k("RETURN", b("11101110100000010111")), k("EXIT", b("11101101010000101011")))
                .row(k("PREV", b("11101011000001001111")), rep("REW", b("11100100100010110111")),
                        rep("FF", b("11101010100001010111")), k("NEXT", b("11101000100001110111")))
                .row(ok("PLAY", b("11100010100011010111")), k("PAUSE", b("11100100110010110011")),
                        k("STOP", b("11101100100000110111")));
        // Volume buttons on this page control the Samsung TV, like the real Blu-ray remote.
        d.panel()
                .row(rep("TV VOL +", s(7)))
                .row(rep("TV VOL −", s(11)))
                .row(k("TV MUTE", s(15)))
                .row(k("SUBTITLE", b("11100110010010011011")), k("AUDIO", b("11101010010001011011")));
        String[] digits = {
                "11101101000000101111", // 0
                "11100100000010111111", // 1
                "11101100000000111111", // 2
                "11100010000011011111", // 3
                "11101010000001011111", // 4
                "11100110000010011111", // 5
                "11101110000000011111", // 6
                "11100001000011101111", // 7
                "11101001000001101111", // 8
                "11100101000010101111", // 9
        };
        for (int i = 1; i <= 9; i++) d.keypad.put(String.valueOf(i), k(String.valueOf(i), b(digits[i])));
        d.keypad.put("RETURN", k("RETURN", b("11101110100000010111")));
        d.keypad.put("0", k("0", b(digits[0])));
        d.keypad.put("ENTER", ok("OK", b("11100011100011000111")));
        return d;
    }

    // ---------------------------------------------------------------- Roku
    // Roku has used several code sets. The CODE SET button on the Roku tab cycles through them.
    static final String CODESET = "__codeset__";
    static final String[] ROKU_SET_NAMES = {"Set 1 (EA C7)", "Set 2 (EA C2)", "Set 3 (BE EF)", "Set 4 (86 05)"};

    private static final int[][] ROKU_ADDR = {{0xEA, 0xC7}, {0xEA, 0xC2}, {0xBE, 0xEF}, {0x86, 0x05}};
    // order: power, home, back, replay, options, up, down, left, right, ok, rew, play, ff, vol+, vol-, mute, netflix, hulu, rokuCh
    private static final int[][] ROKU_FN = {
            {0x17, 0x03, 0x66, 0x78, 0x61, 0x19, 0x33, 0x1E, 0x2D, 0x2A, 0x34, 0x4C, 0x55, 0x0F, 0x10, 0x20, 0x52, 0x4D, 0x6E},
            {0x17, 0x03, 0x66, 0x78, 0x61, 0x19, 0x33, 0x1E, 0x2D, 0x2A, 0x34, 0x4C, 0x55, 0x0F, 0x10, 0x20, 0x52, 0x4D, 0x6E},
            {-1, 0x00, -1, -1, -1, 0x01, 0x05, 0x02, 0x04, 0x03, 0x06, 0x07, 0x08, -1, -1, -1, -1, -1, -1},
            {0x0F, 0x14, 0x1B, -1, -1, 0x42, 0x43, 0x16, 0x15, 0x18, -1, -1, -1, 0x0C, 0x0D, -1, -1, -1, -1},
    };

    static Key codesetKey(int set) { return new Key(CODESET, null, false, NORMAL); }

    static Device roku(int set) {
        final int a = ROKU_ADDR[set][0], b = ROKU_ADDR[set][1];
        final int[] fn = ROKU_FN[set];
        Device d = new Device("Roku");
        d.power = fn[0] < 0 ? null : pwr(Ir.nec(a, b, fn[0]));
        Key home = rk("HOME", a, b, fn[1], false, NORMAL);
        d.test = home; // Roku players have no power, so the timing test sends HOME
        d.panel()
                .row(d.power, home)
                .row(rk("BACK", a, b, fn[2], false, NORMAL), rk("REPLAY", a, b, fn[3], false, NORMAL))
                .row(rk("OPTIONS ✱", a, b, fn[4], false, NORMAL), rk("NETFLIX", a, b, fn[16], false, NORMAL))
                .row(rk("HULU", a, b, fn[17], false, NORMAL), rk("ROKU CH", a, b, fn[18], false, NORMAL))
                .row(codesetKey(set));
        d.panel()
                .row(null, rk("▲", a, b, fn[5], true, NAV), null)
                .row(rk("◀", a, b, fn[7], true, NAV), rk("OK", a, b, fn[9], false, ACCENT), rk("▶", a, b, fn[8], true, NAV))
                .row(null, rk("▼", a, b, fn[6], true, NAV), null)
                .row(rk("REW", a, b, fn[10], true, NORMAL), rk("PLAY / PAUSE", a, b, fn[11], false, ACCENT),
                        rk("FF", a, b, fn[12], true, NORMAL));
        // Volume works on Roku TVs; a Roku player passes volume to the TV with its own remote instead.
        d.panel()
                .row(rk("VOL +", a, b, fn[13], true, NORMAL))
                .row(rk("VOL −", a, b, fn[14], true, NORMAL))
                .row(rk("MUTE", a, b, fn[15], false, NORMAL));
        return d;
    }

    /** A Roku key, or null (an empty spot) when this code set has no such button. */
    private static Key rk(String label, int a, int b, int fn, boolean repeat, int style) {
        return fn < 0 ? null : new Key(label, Ir.nec(a, b, fn), repeat, style);
    }

    private Remotes() {}
}
