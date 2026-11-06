package net.tenthgradeps.app;

import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

/**
 * View-building helpers and the visual theme. The look follows iOS conventions: pure-black OLED background (or white,
 * chosen in Settings), grouped rounded surfaces, large bold titles, tinted buttons, press-scale feedback.
 * The whole UI is built in code so the accent colour can follow the subscription state.
 */
final class Ui {
    private Ui() {}

    // ---- palette (changed by applyTheme) ----
    static int BG = 0xFF000000;
    static int SURFACE = 0xFF1C1C1E;
    static int SURFACE2 = 0xFF2C2C2E;
    static int LINE = 0xFF38383A;
    static int TEXT = 0xFFFFFFFF;
    static int MUTED = 0xFF8E8E93;
    static int TURQ = 0xFFFFFFFF;   // legacy names: the app is black and white only, so every "accent" is just text colour
    static int GOLD = 0xFFFFFFFF;
    static int ON_ACCENT = 0xFF000000;
    static int DANGER = 0xFFFF453A;
    static boolean light = false;
    static int themeVersion = 0;

    /** mode: 0 = black (default), 1 = white, 2 = follow the device. */
    static void applyTheme(Context c) {
        int mode = Store.get(c).themeMode();
        boolean sysDark = (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean wantLight = mode == 1 || (mode == 2 && !sysDark);
        if (wantLight == light && themeVersion > 0) return;
        light = wantLight;
        themeVersion++;
        if (light) {
            BG = 0xFFF2F2F7; SURFACE = 0xFFFFFFFF; SURFACE2 = 0xFFE9E9EE; LINE = 0xFFD1D1D6; TEXT = 0xFF000000; MUTED = 0xFF6C6C70;
            TURQ = 0xFF000000; GOLD = 0xFF000000; ON_ACCENT = 0xFFFFFFFF; DANGER = 0xFFFF3B30;
        } else {
            BG = 0xFF000000; SURFACE = 0xFF1C1C1E; SURFACE2 = 0xFF2C2C2E; LINE = 0xFF38383A; TEXT = 0xFFFFFFFF; MUTED = 0xFF8E8E93;
            TURQ = 0xFFFFFFFF; GOLD = 0xFFFFFFFF; ON_ACCENT = 0xFF000000; DANGER = 0xFFFF453A;
        }
    }

    /** The app is black and white only: the "accent" is white on black and black on white. */
    static int accent() { return TURQ; }

    static int tint(int color, int alpha) { return (color & 0x00FFFFFF) | (alpha << 24); }

    // ---- fonts: a clean open-source sans (downloaded at build time into assets/fonts); falls back to the system font ----
    private static Typeface fReg, fSemi, fBold;
    private static boolean fontsTried;

    static synchronized void initFonts(Context c) {
        if (fontsTried) return;
        fontsTried = true;
        fReg = loadFont(c, "fonts/arabic-Regular.ttf");
        fSemi = loadFont(c, "fonts/arabic-SemiBold.ttf");
        fBold = loadFont(c, "fonts/arabic-Bold.ttf");
    }

    private static Typeface loadFont(Context c, String path) {
        try { return Typeface.createFromAsset(c.getAssets(), path); } catch (Exception e) { return null; }
    }

    /** weight: 0 regular, 1 semibold, 2 bold. */
    static void font(TextView t, int weight) {
        Typeface f = weight == 0 ? fReg : weight == 1 ? (fSemi != null ? fSemi : fBold) : fBold;
        if (f != null) t.setTypeface(f);
        else t.setTypeface(weight == 0 ? Typeface.DEFAULT : Typeface.DEFAULT_BOLD);
    }

    static void applyFontTree(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Typeface cur = t.getTypeface();
            if (cur != fReg && cur != fSemi && cur != fBold) font(t, cur != null && cur.isBold() ? 1 : 0);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyFontTree(g.getChildAt(i));
        }
    }

    // ---- sizes / shapes ----
    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable round(Context c, int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static GradientDrawable stroke(Context c, int fill, int line, float radiusDp) {
        GradientDrawable g = round(c, fill, radiusDp);
        g.setStroke(Math.max(1, dp(c, 1)), line);
        return g;
    }

    // ---- text ----
    static TextView tv(Context c, CharSequence text, float sp, int color, boolean bold) {
        initFonts(c);
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        font(t, bold ? 1 : 0);
        return t;
    }

    /** iOS-style large title at the top of a tab. */
    static TextView largeTitle(Context c, CharSequence text) {
        TextView t = tv(c, text, 34, TEXT, true);
        font(t, 2);
        t.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 8));
        return t;
    }

    /** Section header like "المواد" in iOS lists. */
    static TextView sectionTitle(Context c, CharSequence text) {
        TextView t = tv(c, text, 22, TEXT, true);
        font(t, 2);
        return t;
    }

    // ---- interaction feel ----
    /** Scales the view down slightly while pressed and springs back (with a light haptic tick), like iOS controls. */
    static void pressable(final View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start();
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(2.2f)).start();
                }
                return false;
            }
        });
    }

    /** Quick "pop" used when toggling a favourite star. */
    static void pop(final View v) {
        v.animate().scaleX(1.35f).scaleY(1.35f).setDuration(110).withEndAction(new Runnable() {
            @Override public void run() { v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(new OvershootInterpolator(3f)).start(); }
        }).start();
    }

    // ---- controls ----
    /** iOS "tinted" button: accent text on a soft accent fill; filled = solid accent fill with dark text. */
    static TextView button(Context c, CharSequence text, boolean filled) {
        int a = accent();
        TextView b = tv(c, text, 16, filled ? ON_ACCENT : a, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(c, 18), dp(c, 14), dp(c, 18), dp(c, 14));
        b.setBackground(round(c, filled ? a : tint(a, 0x26), 14));
        b.setClickable(true);
        pressable(b);
        return b;
    }

    static EditText input(Context c, String hint, int inputType) {
        initFonts(c);
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(TEXT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        e.setInputType(inputType);
        e.setSingleLine(inputType != (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        e.setPadding(dp(c, 14), dp(c, 13), dp(c, 14), dp(c, 13));
        e.setBackground(round(c, SURFACE2, 12));
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        font(e, 0);
        return e;
    }

    static TextView chip(Context c, String text, boolean selected) {
        TextView t = tv(c, text, 14, selected ? ON_ACCENT : TEXT, selected);
        t.setPadding(dp(c, 15), dp(c, 8), dp(c, 15), dp(c, 8));
        t.setBackground(round(c, selected ? accent() : SURFACE, 40));
        t.setClickable(true);
        pressable(t);
        return t;
    }

    interface OnPick { void pick(int index); }

    /** iOS segmented control. */
    static LinearLayout segmented(Context c, String[] labels, int selected, final OnPick cb) {
        LinearLayout track = row(c);
        track.setBackground(round(c, SURFACE, 12));
        int p = dp(c, 3);
        track.setPadding(p, p, p, p);
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == selected;
            TextView t = tv(c, labels[i], 14, on ? ON_ACCENT : TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(c, 9), 0, dp(c, 9));
            if (on) t.setBackground(round(c, accent(), 10));
            t.setClickable(true);
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { cb.pick(idx); } });
            track.addView(t, lpWeight(0, -2, 1f));
        }
        return track;
    }

    static void styleSwitch(SwitchCompat s) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{0xFFFFFFFF, 0xFFFFFFFF}));
        s.setTrackTintList(new ColorStateList(st, new int[]{accent(), light ? 0xFFC7C7CC : 0xFF48484A}));
        font(s, 1);
    }

    /** Small label: "PREMIUM" outlined, "مجاني" muted. */
    static TextView badge(Context c, boolean premium) {
        TextView b = tv(c, premium ? "PREMIUM" : "مجاني", 11, premium ? TEXT : MUTED, true);
        b.setPadding(dp(c, 11), dp(c, 5), dp(c, 11), dp(c, 5));
        b.setBackground(premium ? stroke(c, 0, TEXT, 12) : round(c, SURFACE2, 12));
        return b;
    }

    static ImageView icon(Context c, int res, int color, int sizeDp) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        v.setLayoutParams(new ViewGroup.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    static ImageView iconButton(Context c, int res, int color) {
        ImageView v = new ImageView(c);
        v.setImageResource(res);
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
        int p = dp(c, 9);
        v.setPadding(p, p, p, p);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, 42), dp(c, 42)));
        v.setClickable(true);
        return v;
    }

    static Drawable tintedDrawable(Context c, int res, int color) {
        Drawable d = c.getResources().getDrawable(res, null).mutate();
        d.setTint(color);
        return d;
    }

    // ---- layout helpers ----
    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lpWeight(int w, int h, float weight) { return new LinearLayout.LayoutParams(w, h, weight); }

    static LinearLayout.LayoutParams lpMargin(Context c, int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(c, l), dp(c, t), dp(c, r), dp(c, b));
        return p;
    }

    static View spacer(Context c, int heightDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, heightDp)));
        return v;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    /** Grouped rounded surface (like an inset-grouped iOS section). */
    static LinearLayout card(Context c) {
        LinearLayout l = column(c);
        l.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        l.setBackground(round(c, SURFACE, 20));
        return l;
    }

    static void toast(Context c, String m) { Toast.makeText(c, m, Toast.LENGTH_LONG).show(); }

    /** Shows a dialog styled like the rest of the app (rounded surface, accent buttons, app font). */
    static AlertDialog show(AlertDialog.Builder b) {
        final AlertDialog d = b.create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface x) {
                Context c = d.getContext();
                if (d.getWindow() != null) d.getWindow().setBackgroundDrawable(round(c, SURFACE, 22));
                int[] ids = {DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL};
                for (int id : ids) {
                    Button bt = d.getButton(id);
                    if (bt != null) { bt.setTextColor(accent()); font(bt, 1); }
                }
                if (d.getWindow() != null) applyFontTree(d.getWindow().getDecorView());
            }
        });
        d.show();
        return d;
    }

    static String formatBytes(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        return String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0);
    }
}
