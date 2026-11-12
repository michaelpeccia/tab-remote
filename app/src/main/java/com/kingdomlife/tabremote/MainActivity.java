package com.kingdomlife.tabremote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.hardware.ConsumerIrManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {

    private static final int BG = 0xFF12151C;
    private static final int KEY = 0xFF2A2F3A;
    private static final int KEY_NAV = 0xFF353D4C;
    private static final int KEY_POWER = 0xFFB3261E;
    private static final int GOLD = 0xFFC9A227;
    private static final int TEXT = 0xFFF2F2F2;
    private static final int MUTED = 0xFF8A93A3;

    private static final String PREFS = "tabremote";
    private static final String PREF_DEVICE = "device";
    private static final String PREF_TIMING = "timing"; // 0 auto, 1 microseconds, 2 carrier cycles
    private static final String PREF_VARIANT = "variant"; // -1 = use timing mode, else index into VARIANTS

    /** Timing variants for the diagnostic: label, scale applied to every duration, frames sent. */
    private static final String[] V_LABEL = {"A", "B", "C", "D", "E", "F", "G", "H"};
    private static final String[] V_DESC = {
            "microseconds", "carrier cycles", "microseconds x3", "carrier cycles x3",
            "double length", "half length", "cycles double", "cycles half"};
    private static final float CYC = Ir.FREQ / 1000000f;
    private static final float[] V_SCALE = {1f, CYC, 1f, CYC, 2f, 0.5f, CYC * 2f, CYC * 0.5f};
    private static final int[] V_FRAMES = {1, 1, 3, 3, 1, 1, 1, 1};

    private final Handler ui = new Handler(Looper.getMainLooper());
    private HandlerThread irThread;
    private Handler ir;
    private ConsumerIrManager irManager;
    private SharedPreferences prefs;

    private List<Remotes.Device> devices;
    private int current;
    private LinearLayout root;
    private TextView status;
    private Runnable repeater;
    private final AtomicBoolean busy = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        irManager = (ConsumerIrManager) getSystemService(Context.CONSUMER_IR_SERVICE);
        irThread = new HandlerThread("ir");
        irThread.start();
        ir = new Handler(irThread.getLooper());

        devices = Remotes.all();
        current = Math.min(prefs.getInt(PREF_DEVICE, 0), devices.size() - 1);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        build();

        if (!hasIr()) setStatus("No IR blaster found. Tap SETUP for details.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRepeat();
        irThread.quit();
    }

    // ------------------------------------------------------------------ UI

    private void build() {
        root.removeAllViews();
        boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;

        // Tab bar
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), dp(8), dp(8), dp(4));
        for (int i = 0; i < devices.size(); i++) {
            final int index = i;
            TextView t = button(devices.get(i).name, i == current ? GOLD : KEY,
                    i == current ? BG : TEXT, 15);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    current = index;
                    prefs.edit().putInt(PREF_DEVICE, index).apply();
                    build();
                }
            });
            tabs.addView(t, weighted(1f, dp(44)));
        }
        TextView setup = button("SETUP", KEY, MUTED, 13);
        setup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSetup(); }
        });
        tabs.addView(setup, weighted(0.6f, dp(44)));
        root.addView(tabs);

        // Remote body
        Remotes.Device d = devices.get(current);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setPadding(dp(4), dp(4), dp(4), dp(4));
        for (int p = 0; p < d.panels.size(); p++) {
            LinearLayout panel = new LinearLayout(this);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(dp(6), 0, dp(6), dp(landscape ? 0 : 10));
            for (List<Remotes.Key> row : d.panels.get(p)) {
                LinearLayout r = new LinearLayout(this);
                r.setOrientation(LinearLayout.HORIZONTAL);
                for (Remotes.Key k : row) {
                    View v = (k == null) ? new View(this) : keyView(d, k);
                    r.addView(v, weighted(1f, dp(54)));
                }
                panel.addView(r);
            }
            float weight = (p == 1) ? 1.25f : 1f;
            if (landscape) {
                body.addView(panel, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, weight));
            } else {
                body.addView(panel, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextColor(MUTED);
        status.setTextSize(12);
        status.setPadding(dp(14), dp(2), dp(14), dp(8));
        status.setText("Point the tablet at the " + d.name + " and press a button.");
        root.addView(status);
    }

    private View keyView(final Remotes.Device d, final Remotes.Key k) {
        if (k.isKeypad()) {
            TextView t = button("1 2 3", KEY, TEXT, 15);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showKeypad(d); }
            });
            return t;
        }
        int bg = k.style == Remotes.POWER ? KEY_POWER
                : k.style == Remotes.ACCENT ? GOLD
                : k.style == Remotes.NAV ? KEY_NAV : KEY;
        int fg = k.style == Remotes.ACCENT ? BG : TEXT;
        TextView t = button(k.label, bg, fg, k.label.length() > 7 ? 13 : 16);
        t.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.setPressed(true);
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                        send(k, false);
                        if (k.repeat) startRepeat(k);
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.setPressed(false);
                        stopRepeat();
                        return true;
                }
                return true;
            }
        });
        return t;
    }

    private TextView button(String label, int bg, int fg, int sizeSp) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(fg);
        t.setTextSize(sizeSp);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setClickable(true);

        GradientDrawable normal = new GradientDrawable();
        normal.setColor(bg);
        normal.setCornerRadius(dp(10));
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(lighten(bg));
        pressed.setCornerRadius(dp(10));
        pressed.setStroke(dp(2), GOLD);
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_pressed}, pressed);
        states.addState(new int[]{}, normal);
        t.setBackgroundDrawable(states);
        return t;
    }

    private LinearLayout.LayoutParams weighted(float w, int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, height, w);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    private void showKeypad(Remotes.Device d) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(dp(12), dp(12), dp(12), dp(12));
        grid.setBackgroundColor(BG);
        LinearLayout row = null;
        int i = 0;
        for (Map.Entry<String, Remotes.Key> e : d.keypad.entrySet()) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            row.addView(keyView(d, e.getValue()), weighted(1f, dp(54)));
            i++;
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
                .setTitle(d.name + " keypad")
                .setView(grid)
                .setPositiveButton("Close", null)
                .show();
    }

    private void showSetup() {
        StringBuilder sb = new StringBuilder();
        sb.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("Model: ").append(Build.MODEL).append("\n");
        sb.append("IR blaster: ").append(hasIr() ? "found" : "NOT found").append("\n");
        if (hasIr()) {
            try {
                ConsumerIrManager.CarrierFrequencyRange[] r = irManager.getCarrierFrequencies();
                if (r != null && r.length > 0) {
                    sb.append("Carrier range: ").append(r[0].getMinFrequency())
                            .append("–").append(r[r.length - 1].getMaxFrequency()).append(" Hz\n");
                }
            } catch (Exception ignored) { }
        }
        sb.append("\nTiming mode: ").append(timingLabel()).append("\n");
        sb.append(variantLabel()).append("\n\n");
        sb.append("If buttons send but nothing reacts, open Timing test. ")
                .append("Older KitKat builds (4.4.2 and earlier) count carrier cycles instead of microseconds.");

        final AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
                .setTitle("Setup")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .setNeutralButton("Change timing", null)
                .setNegativeButton("Timing test", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int mode = (prefs.getInt(PREF_TIMING, 0) + 1) % 3;
                prefs.edit().putInt(PREF_TIMING, mode).apply();
                dlg.dismiss();
                showSetup();
            }
        });
        dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dlg.dismiss(); showDiagnostic(); }
        });
    }

    private Runnable autoTest;

    private void showDiagnostic() {
        final Remotes.Device d = devices.get(current);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(8), dp(12), dp(8));
        box.setBackgroundColor(BG);

        final TextView big = new TextView(this);
        big.setTextColor(GOLD);
        big.setTextSize(22);
        big.setTypeface(Typeface.DEFAULT_BOLD);
        big.setGravity(Gravity.CENTER);
        big.setText(variantLabel());
        box.addView(big, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView help = new TextView(this);
        help.setTextColor(TEXT);
        help.setTextSize(13);
        help.setText("Aim at the " + d.name + ". Tap a letter to send POWER that way. "
                + "The last letter you tap becomes the setting for every button, "
                + "so when the TV reacts, just close this.");
        box.addView(help);

        LinearLayout row = null;
        for (int i = 0; i < V_LABEL.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                box.addView(row);
            }
            final int idx = i;
            TextView b = button(V_LABEL[i], KEY_NAV, TEXT, 20);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    stopAuto();
                    prefs.edit().putInt(PREF_VARIANT, idx).apply();
                    big.setText(variantLabel());
                    send(d.power);
                }
            });
            row.addView(b, weighted(1f, dp(54)));
        }

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        final TextView auto = button("AUTO TEST (A to H)", GOLD, BG, 14);
        auto.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopAuto();
                autoTest = new Runnable() {
                    int i = 0;
                    @Override public void run() {
                        if (i >= V_LABEL.length) { big.setText("Done. Which letter worked?"); autoTest = null; return; }
                        prefs.edit().putInt(PREF_VARIANT, i).apply();
                        big.setText("Sending " + V_LABEL[i] + "  (" + V_DESC[i] + ")");
                        send(d.power);
                        i++;
                        ui.postDelayed(this, 3000);
                    }
                };
                ui.post(autoTest);
            }
        });
        TextView reset = button("RESET", KEY, TEXT, 14);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopAuto();
                prefs.edit().putInt(PREF_VARIANT, -1).apply();
                big.setText(variantLabel());
            }
        });
        bottom.addView(auto, weighted(2f, dp(50)));
        bottom.addView(reset, weighted(1f, dp(50)));
        box.addView(bottom);

        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
                .setTitle("Timing test")
                .setView(sv)
                .setPositiveButton("Close", null)
                .create();
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface di) { stopAuto(); }
        });
        dlg.show();
    }

    private void stopAuto() {
        if (autoTest != null) { ui.removeCallbacks(autoTest); autoTest = null; }
    }

    private String variantLabel() {
        int v = prefs.getInt(PREF_VARIANT, -1);
        return v < 0 ? "Using: timing mode (" + (useCycles() ? "cycles" : "microseconds") + ")"
                : "Using: " + V_LABEL[v] + "  (" + V_DESC[v] + ")";
    }

    // ------------------------------------------------------------------ IR

    private boolean hasIr() {
        try {
            return irManager != null && irManager.hasIrEmitter();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean autoCycles() {
        // Before Android 4.4.3 the pattern was interpreted as carrier cycles, not microseconds.
        // Go by API level: some custom ROMs report a fake version name.
        String r = Build.VERSION.RELEASE;
        return Build.VERSION.SDK_INT == 19 && !"4.4.3".equals(r) && !"4.4.4".equals(r);
    }

    private boolean useCycles() {
        int mode = prefs.getInt(PREF_TIMING, 0);
        return mode == 2 || (mode == 0 && autoCycles());
    }

    private String timingLabel() {
        int mode = prefs.getInt(PREF_TIMING, 0);
        String eff = useCycles() ? "carrier cycles" : "microseconds";
        return (mode == 0 ? "Auto → " : "Manual → ") + eff;
    }

    private void send(final Remotes.Key k) { send(k, false); }

    /** isRepeat: a held-button repeat; skipped if the last signal is still sending, so presses don't pile up. */
    private void send(final Remotes.Key k, boolean isRepeat) {
        if (k == null || k.pattern == null) return;
        if (isRepeat && busy.get()) return;
        if (!hasIr()) {
            setStatus("No IR blaster found on this device.");
            return;
        }
        int v = prefs.getInt(PREF_VARIANT, -1);
        final float scale = v >= 0 ? V_SCALE[v] : (useCycles() ? CYC : 1f);
        final int frames = v >= 0 ? V_FRAMES[v] : 1;
        busy.set(true);
        ir.post(new Runnable() {
            @Override public void run() {
                try {
                    int[] f = k.pattern;
                    int[] p = new int[f.length * frames + (frames - 1)];
                    int n = 0;
                    for (int r = 0; r < frames; r++) {
                        if (r > 0) p[n++] = 45000; // gap between repeated frames
                        for (int x : f) p[n++] = x;
                    }
                    if (scale != 1f) {
                        for (int i = 0; i < p.length; i++) p[i] = Math.max(1, Math.round(p[i] * scale));
                    }
                    irManager.transmit(Ir.FREQ, p);
                    postStatus("Sent " + k.label);
                } catch (Exception e) {
                    postStatus("Send failed: " + e.getMessage());
                } finally {
                    busy.set(false);
                }
            }
        });
    }

    private void startRepeat(final Remotes.Key k) {
        stopRepeat();
        repeater = new Runnable() {
            @Override public void run() {
                send(k, true);
                ui.postDelayed(this, 140);
            }
        };
        ui.postDelayed(repeater, 450);
    }

    private void stopRepeat() {
        if (repeater != null) {
            ui.removeCallbacks(repeater);
            repeater = null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private void postStatus(final String s) {
        ui.post(new Runnable() {
            @Override public void run() { setStatus(s); }
        });
    }

    private void setStatus(String s) {
        if (status != null) status.setText(s);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int lighten(int c) {
        int r = Math.min(255, Color.red(c) + 40);
        int g = Math.min(255, Color.green(c) + 40);
        int b = Math.min(255, Color.blue(c) + 40);
        return Color.argb(255, r, g, b);
    }
}
