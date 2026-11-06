package net.tenthgradeps.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Animated launch screen: logo and name, while the catalog, site status and the saved session are loaded. */
public class SplashActivity extends BaseActivity {
    private boolean animDone, dataDone, started;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        int accent = Ui.accent();

        LinearLayout col = Ui.column(this);
        col.setGravity(Gravity.CENTER);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        col.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 150), Ui.dp(this, 150)));

        TextView name = Ui.tv(this, "10thgradeps", 30, Ui.TEXT, true);
        name.setLetterSpacing(0.02f);
        name.setTextDirection(View.TEXT_DIRECTION_LTR);
        col.addView(name, Ui.lpMargin(this, -2, -2, 0, 20, 0, 0));

        TextView tag = Ui.tv(this, "مواد وامتحانات الصف العاشر — فلسطين", 14, Ui.MUTED, false);
        col.addView(tag, Ui.lpMargin(this, -2, -2, 0, 8, 0, 0));

        View line = new View(this);
        line.setBackground(Ui.round(this, accent, 3));
        col.addView(line, Ui.lpMargin(this, Ui.dp(this, 120), Ui.dp(this, 3), 0, 26, 0, 0));

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(-1, -1);
        FrameLayout wrap = new FrameLayout(this);
        wrap.addView(col, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        setRoot(wrap);

        logo.setAlpha(0f); logo.setScaleX(0.55f); logo.setScaleY(0.55f);
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(800).setInterpolator(new OvershootInterpolator(1.3f)).start();
        name.setAlpha(0f); name.setTranslationY(Ui.dp(this, 24));
        name.animate().alpha(1f).translationY(0f).setStartDelay(380).setDuration(600).setInterpolator(new DecelerateInterpolator()).start();
        tag.setAlpha(0f);
        tag.animate().alpha(1f).setStartDelay(650).setDuration(600).start();
        line.setScaleX(0f);
        line.animate().scaleX(1f).setStartDelay(800).setDuration(800).setInterpolator(new DecelerateInterpolator()).start();

        Async.MAIN.postDelayed(new Runnable() {
            @Override public void run() { animDone = true; go(); }
        }, 1900);
        Async.MAIN.postDelayed(new Runnable() {
            @Override public void run() { animDone = true; dataDone = true; go(); }
        }, 6000);

        Async.run(new Async.Work<Boolean>() {
            @Override public Boolean run() {
                Catalog.get(SplashActivity.this);
                try { Api.loadStatus(SplashActivity.this); } catch (Exception ignored) { }
                try { Api.loadSiteFiles(SplashActivity.this); } catch (Exception ignored) { }
                try { Api.refreshAccount(SplashActivity.this); } catch (Exception ignored) { }
                return Boolean.TRUE;
            }
        }, new Async.Done<Boolean>() {
            @Override public void done(Boolean r, Exception e) { dataDone = true; go(); }
        });
    }

    private void go() {
        if (started || !animDone || !dataDone || isFinishing()) return;
        started = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
