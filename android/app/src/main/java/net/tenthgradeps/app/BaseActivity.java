package net.tenthgradeps.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Right-to-left layout, the selected theme (black / white), iOS-style push and pop transitions. */
public abstract class BaseActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle b) {
        Ui.applyTheme(this);
        setTheme(Ui.light ? R.style.AppThemeLight : R.style.AppThemeDark);
        super.onCreate(b);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        WindowInsetsControllerCompat c = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setAppearanceLightStatusBars(Ui.light);
        c.setAppearanceLightNavigationBars(Ui.light);
    }

    protected void setRoot(View content) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    @Override public void startActivity(Intent intent) {
        super.startActivity(intent);
        overridePendingTransition(R.anim.push_enter, R.anim.push_exit);
    }

    @Override public void finish() {
        super.finish();
        overridePendingTransition(R.anim.pop_enter, R.anim.pop_exit);
    }

    /** iOS navigation bar: accent back chevron at the start, centred semibold title. */
    protected FrameLayout topBar(String title) {
        FrameLayout bar = new FrameLayout(this);
        bar.setPadding(Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8));
        TextView t = Ui.tv(this, title, 17, Ui.TEXT, true);
        t.setGravity(Gravity.CENTER);
        bar.addView(t, new FrameLayout.LayoutParams(-1, Ui.dp(this, 42), Gravity.CENTER));
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, Ui.accent());
        back.setScaleX(-1f); // the drawable points left; in RTL "back" points right
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        bar.addView(back, new FrameLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42), Gravity.START | Gravity.CENTER_VERTICAL));
        return bar;
    }
}
