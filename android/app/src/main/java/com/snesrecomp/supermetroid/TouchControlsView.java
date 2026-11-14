package com.snesrecomp.supermetroid;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

import java.util.LinkedHashMap;
import java.util.Map;

/** Configurable multitouch SNES pad. It emits the same Android key codes as
 * the runtime's default keyboard bindings, so game and overlay menus share one
 * input path. Positions are normalized and survive rotation/resolution changes. */
public final class TouchControlsView extends FrameLayout {
    private static final String PREFS = "touch-controls-v1";
    private final SharedPreferences prefs;
    private final Map<String, Control> controls = new LinkedHashMap<>();
    private Runnable settingsListener;
    private boolean editing;
    private float opacity;
    private float scale;

    private static final class Spec {
        final String id, label;
        final int key;
        final float x, y;
        final boolean small;
        Spec(String id, String label, int key, float x, float y, boolean small) {
            this.id=id; this.label=label; this.key=key; this.x=x; this.y=y; this.small=small;
        }
    }

    private static final Spec[] SPECS = {
        new Spec("up", "▲", KeyEvent.KEYCODE_DPAD_UP, .16f, .57f, false),
        new Spec("down", "▼", KeyEvent.KEYCODE_DPAD_DOWN, .16f, .85f, false),
        new Spec("left", "◀", KeyEvent.KEYCODE_DPAD_LEFT, .08f, .71f, false),
        new Spec("right", "▶", KeyEvent.KEYCODE_DPAD_RIGHT, .24f, .71f, false),
        new Spec("y", "Y", KeyEvent.KEYCODE_A, .73f, .71f, false),
        new Spec("x", "X", KeyEvent.KEYCODE_S, .82f, .57f, false),
        new Spec("b", "B", KeyEvent.KEYCODE_Z, .82f, .85f, false),
        new Spec("a", "A", KeyEvent.KEYCODE_X, .91f, .71f, false),
        new Spec("l", "L", KeyEvent.KEYCODE_C, .10f, .16f, true),
        new Spec("r", "R", KeyEvent.KEYCODE_V, .90f, .16f, true),
        new Spec("select", "SELECT", KeyEvent.KEYCODE_SHIFT_RIGHT, .43f, .88f, true),
        new Spec("start", "START", KeyEvent.KEYCODE_ENTER, .57f, .88f, true)
    };

    private final class Control extends TextView implements OnTouchListener {
        final Spec spec;
        boolean down;
        float dragDx, dragDy;
        Control(Spec spec) {
            super(TouchControlsView.this.getContext());
            this.spec = spec;
            setText(spec.label); setTextColor(Color.WHITE); setGravity(17);
            setTextSize(spec.small ? 11 : 19); setOnTouchListener(this);
            setElevation(5);
        }
        @Override public boolean onTouch(View view, MotionEvent e) {
            if (editing) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    dragDx = e.getRawX() - getX(); dragDy = e.getRawY() - getY();
                    return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE) {
                    float nx = clamp(e.getRawX() - dragDx, 0, TouchControlsView.this.getWidth() - view.getWidth());
                    float ny = clamp(e.getRawY() - dragDy, 0, TouchControlsView.this.getHeight() - view.getHeight());
                    view.setX(nx); view.setY(ny); return true;
                }
                if (e.getActionMasked() == MotionEvent.ACTION_UP || e.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    savePosition(this); return true;
                }
                return true;
            }
            int action = e.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) { send(true); return true; }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { send(false); return true; }
            return true;
        }
        void send(boolean pressed) {
            if (down == pressed) return;
            down = pressed;
            setPressed(pressed);
            try {
                if (pressed) SDLActivity.onNativeKeyDown(spec.key);
                else SDLActivity.onNativeKeyUp(spec.key);
            } catch (UnsatisfiedLinkError ignored) { down = false; }
        }
    }

    public TouchControlsView(Context context) {
        super(context);
        setClipChildren(false);
        setMotionEventSplittingEnabled(true);
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        opacity = prefs.getFloat("opacity", .62f);
        scale = prefs.getFloat("scale", 1f);
        for (Spec spec : SPECS) {
            Control c = new Control(spec); controls.put(spec.id, c); addView(c);
        }
        TextView settings = new TextView(context);
        settings.setText("⚙"); settings.setTextColor(Color.WHITE); settings.setTextSize(25);
        settings.setGravity(17); settings.setBackground(makeBackground(.45f, true));
        settings.setOnClickListener(v -> { if (settingsListener != null) settingsListener.run(); });
        LayoutParams gear = new LayoutParams(dp(48), dp(48)); gear.leftMargin=dp(10); gear.topMargin=dp(10);
        addView(settings, gear);
        post(this::layoutControls);
    }

    public void setSettingsListener(Runnable listener) { settingsListener = listener; }
    public boolean isEditing() { return editing; }
    public void setEditing(boolean value) {
        editing = value;
        for (Control c : controls.values()) {
            c.send(false);
            c.setBackground(makeBackground(value ? .9f : opacity, c.spec.small));
        }
    }
    public float getControlOpacity() { return opacity; }
    public float getControlScale() { return scale; }
    public void setControlOpacity(float value) {
        opacity = clamp(value, .15f, 1f); prefs.edit().putFloat("opacity", opacity).apply();
        if (!editing) for (Control c : controls.values()) c.setBackground(makeBackground(opacity, c.spec.small));
    }
    public void setControlScale(float value) {
        scale = clamp(value, .55f, 1.5f); prefs.edit().putFloat("scale", scale).apply(); layoutControls();
    }
    public void resetLayout() {
        SharedPreferences.Editor e = prefs.edit();
        for (Spec s : SPECS) { e.remove(s.id + ".x"); e.remove(s.id + ".y"); }
        e.apply(); layoutControls();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); post(this::layoutControls);
    }

    private void layoutControls() {
        if (getWidth() == 0 || getHeight() == 0) return;
        for (Control c : controls.values()) {
            int baseW = c.spec.small ? 82 : 68;
            int baseH = c.spec.small ? 45 : 68;
            LayoutParams p = (LayoutParams)c.getLayoutParams();
            p.width = dp(baseW * scale); p.height = dp(baseH * scale); c.setLayoutParams(p);
            c.setBackground(makeBackground(editing ? .9f : opacity, c.spec.small));
            float cx = prefs.getFloat(c.spec.id + ".x", c.spec.x);
            float cy = prefs.getFloat(c.spec.id + ".y", c.spec.y);
            c.setX(clamp(cx * getWidth() - p.width / 2f, 0, getWidth() - p.width));
            c.setY(clamp(cy * getHeight() - p.height / 2f, 0, getHeight() - p.height));
        }
    }

    private void savePosition(Control c) {
        float cx = (c.getX() + c.getWidth()/2f) / getWidth();
        float cy = (c.getY() + c.getHeight()/2f) / getHeight();
        prefs.edit().putFloat(c.spec.id + ".x", cx).putFloat(c.spec.id + ".y", cy).apply();
    }

    private GradientDrawable makeBackground(float alpha, boolean pill) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.argb((int)(alpha * 190), 15, 18, 25));
        d.setStroke(dp(2), Color.argb((int)(alpha * 255), 255, 193, 7));
        d.setCornerRadius(dp(pill ? 22 : 40)); return d;
    }
    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
