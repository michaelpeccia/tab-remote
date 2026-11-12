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
                        send(k);
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
        sb.append("\nTiming mode: ").append(timingLabel()).append("\n\n");
        sb.append("If buttons send but nothing reacts, switch the timing mode and try POWER again. ")
                .append("Older KitKat builds (4.4.2 and earlier) count carrier cycles instead of microseconds.");

        final AlertDialog dlg = new AlertDialog.Builder(this, AlertDialog.THEME_HOLO_DARK)
                .setTitle("Setup")
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .setNeutralButton("Change timing", null)
                .setNegativeButton("Test POWER", null)
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
            @Override public void onClick(View v) { send(devices.get(current).power); }
        });
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
        String r = Build.VERSION.RELEASE;
        return Build.VERSION.SDK_INT == 19
                && ("4.4".equals(r) || "4.4.1".equals(r) || "4.4.2".equals(r));
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

    private void send(final Remotes.Key k) {
        if (k == null || k.pattern == null) return;
        if (!hasIr()) {
            setStatus("No IR blaster found on this device.");
            return;
        }
        final boolean cycles = useCycles();
        ir.post(new Runnable() {
            @Override public void run() {
                try {
                    int[] p = k.pattern;
                    if (cycles) {
                        int[] c = new int[p.length];
                        for (int i = 0; i < p.length; i++) {
                            c[i] = Math.max(1, Math.round(p[i] * (Ir.FREQ / 1000000f)));
                        }
                        p = c;
                    }
                    irManager.transmit(Ir.FREQ, p);
                    postStatus("Sent " + k.label);
                } catch (Exception e) {
                    postStatus("Send failed: " + e.getMessage());
                }
            }
        });
    }

    private void startRepeat(final Remotes.Key k) {
        stopRepeat();
        repeater = new Runnable() {
            @Override public void run() {
                send(k);
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
