package net.tenthgradeps.app;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

public class MainActivity extends BaseActivity implements Session.Listener {
    interface Refreshable { void refreshScreen(); }

    private static final int[] ICONS = {R.drawable.ic_home, R.drawable.ic_lock, R.drawable.ic_star, R.drawable.ic_download, R.drawable.ic_person};
    private static final String[] LABELS = {"الرئيسية", "Premium", "المفضلة", "التنزيلات", "حسابي"};

    private final Fragment[] frags = new Fragment[5];
    private final LinearLayout[] navItems = new LinearLayout[5];
    private final ImageView[] navIcons = new ImageView[5];
    private final TextView[] navLabels = new TextView[5];
    private LinearLayout nav;
    private int current = -1;
    private int themeAtCreate;
    private TextView banner;

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(null); // fragments are rebuilt (with the right theme) instead of restored
        themeAtCreate = Ui.themeVersion;

        FrameLayout root = new FrameLayout(this);
        LinearLayout page = Ui.column(this);
        banner = Ui.tv(this, "", 13, Ui.ON_ACCENT, true);
        banner.setGravity(Gravity.CENTER);
        banner.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        banner.setBackgroundColor(Ui.accent());
        banner.setVisibility(View.GONE);
        page.addView(banner, Ui.lp(-1, -2));

        FrameLayout container = new FrameLayout(this);
        container.setId(View.generateViewId());
        page.addView(container, Ui.lpWeight(-1, 0, 1f));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        nav = buildNav();
        FrameLayout.LayoutParams np = new FrameLayout.LayoutParams(-1, Ui.dp(this, 64), Gravity.BOTTOM);
        np.setMargins(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 14));
        root.addView(nav, np);
        setRoot(root);

        frags[0] = FilesFragment.newInstance(false);
        frags[1] = FilesFragment.newInstance(true);
        frags[2] = new FavoritesFragment();
        frags[3] = new DownloadsFragment();
        frags[4] = new AccountFragment();
        FragmentTransaction t = getSupportFragmentManager().beginTransaction();
        for (int i = 0; i < 5; i++) t.add(container.getId(), frags[i], "tab" + i);
        t.commitNow();
        select(0);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (current != 0) select(0);
                else { setEnabled(false); getOnBackPressedDispatcher().onBackPressed(); }
            }
        });
    }

    /** Floating rounded tab bar: the selected tab expands into a pill with its label. */
    private LinearLayout buildNav() {
        LinearLayout bar = Ui.row(this);
        bar.setBackground(Ui.stroke(this, Ui.SURFACE, Ui.LINE, 32));
        bar.setElevation(Ui.dp(this, 8));
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            LinearLayout item = Ui.row(this);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            ImageView ic = new ImageView(this);
            ic.setImageResource(ICONS[i]);
            item.addView(ic, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
            TextView lb = Ui.tv(this, LABELS[i], 13, Ui.ON_ACCENT, true);
            lb.setVisibility(View.GONE);
            item.addView(lb, Ui.lpMargin(this, -2, -2, 6, 0, 0, 0));
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { select(idx); } });
            Ui.pressable(item);
            navItems[i] = item;
            navIcons[i] = ic;
            navLabels[i] = lb;
            bar.addView(item, Ui.lpWeight(0, -1, 1f));
        }
        return bar;
    }

    private void updateNav(int sel) {
        TransitionManager.beginDelayedTransition(nav);
        for (int k = 0; k < 5; k++) {
            boolean on = k == sel;
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) navItems[k].getLayoutParams();
            lp.width = on ? WRAP : 0;
            lp.weight = on ? 0f : 1f;
            navItems[k].setLayoutParams(lp);
            navItems[k].setPadding(on ? Ui.dp(this, 16) : 0, 0, on ? Ui.dp(this, 16) : 0, 0);
            navItems[k].setBackground(on ? Ui.round(this, Ui.accent(), 26) : null);
            navIcons[k].setColorFilter(on ? Ui.ON_ACCENT : Ui.MUTED, PorterDuff.Mode.SRC_IN);
            navLabels[k].setVisibility(on ? View.VISIBLE : View.GONE);
        }
    }

    private static final int WRAP = -2;

    void select(int i) {
        int previous = current;
        FragmentTransaction t = getSupportFragmentManager().beginTransaction();
        for (int k = 0; k < frags.length; k++) { if (k == i) t.show(frags[k]); else t.hide(frags[k]); }
        t.commitAllowingStateLoss();
        current = i;
        updateNav(i);
        if (frags[i] instanceof Refreshable) ((Refreshable) frags[i]).refreshScreen();
        View v = frags[i].getView();
        if (v != null && i != previous && previous >= 0) {
            v.setAlpha(0f);
            v.setTranslationY(Ui.dp(this, 10));
            v.animate().alpha(1f).translationY(0f).setDuration(220).start();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        Session.addListener(this);
        if (themeAtCreate != Ui.themeVersion) { recreate(); return; }
        updateBanner();
        if (current >= 0 && frags[current] instanceof Refreshable) ((Refreshable) frags[current]).refreshScreen();
        if (Session.loggedIn() && Session.refreshDue() && Net.online(this)) {
            Async.run(new Async.Work<Boolean>() {
                @Override public Boolean run() throws Exception { Api.refreshAccount(MainActivity.this); return Boolean.TRUE; }
            }, new Async.Done<Boolean>() {
                @Override public void done(Boolean r, Exception e) { onSessionChanged(); }
            });
        }
    }

    @Override protected void onStop() {
        super.onStop();
        Session.removeListener(this);
    }

    @Override public void onSessionChanged() {
        if (isFinishing() || isDestroyed()) return;
        if (themeAtCreate != Ui.themeVersion) { recreate(); return; }
        updateBanner();
        if (current >= 0 && frags[current] instanceof Refreshable) ((Refreshable) frags[current]).refreshScreen();
    }

    private void updateBanner() {
        Catalog c = Catalog.get(this);
        String m = c.message;
        boolean show = c.siteMaintenance && m != null && !m.isEmpty();
        banner.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) banner.setText(m);
    }
}
