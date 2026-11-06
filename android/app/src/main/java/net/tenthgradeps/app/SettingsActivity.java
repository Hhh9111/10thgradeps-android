package net.tenthgradeps.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

public class SettingsActivity extends BaseActivity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        root.addView(topBar("الإعدادات"), Ui.lp(-1, -2));
        ScrollView sv = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, Ui.lpWeight(-1, 0, 1f));
        setRoot(root);
        build();
    }

    private TextView heading(String t) {
        TextView h = Ui.tv(this, t, 13, Ui.MUTED, true);
        h.setPadding(Ui.dp(this, 8), Ui.dp(this, 22), 0, Ui.dp(this, 8));
        return h;
    }

    private SwitchCompat toggle(String label, boolean on, CompoundButton.OnCheckedChangeListener l) {
        SwitchCompat s = new SwitchCompat(this);
        s.setText(label);
        s.setTextColor(Ui.TEXT);
        s.setTextSize(16);
        s.setChecked(on);
        s.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        Ui.styleSwitch(s);
        s.setOnCheckedChangeListener(l);
        return s;
    }

    private void build() {
        content.removeAllViews();
        final Store store = Store.get(this);

        content.addView(heading("المظهر"));
        LinearLayout look = Ui.card(this);
        look.addView(Ui.segmented(this, new String[]{"أسود", "أبيض", "حسب الجهاز"}, store.themeMode(), new Ui.OnPick() {
            @Override public void pick(int i) {
                if (i == store.themeMode()) return;
                store.setThemeMode(i);
                Ui.applyTheme(SettingsActivity.this);
                recreate();
            }
        }));
        look.addView(Ui.tv(this, "الأسود المعتم هو الافتراضي (مناسب لشاشات OLED). غيّره إلى الأبيض متى شئت.", 12, Ui.MUTED, false), Ui.lpMargin(this, -2, -2, 0, 10, 0, 0));
        content.addView(look, Ui.lp(-1, -2));

        if (Session.loggedIn()) {
            content.addView(heading("الحساب"));
            LinearLayout acc = Ui.card(this);
            acc.addView(Ui.tv(this, Session.displayName(), 17, Ui.TEXT, true));
            acc.addView(Ui.tv(this, "@" + Session.username(), 13, Ui.MUTED, false));
            TextView rename = Ui.button(this, "تغيير الاسم المعروض", false);
            rename.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { renameDialog(); } });
            acc.addView(rename, Ui.lpMargin(this, -1, -2, 0, 12, 0, 0));
            if (!Session.isOwner()) {
                TextView pw = Ui.button(this, "تغيير كلمة المرور", false);
                pw.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { passwordDialog(); } });
                acc.addView(pw, Ui.lpMargin(this, -1, -2, 0, 8, 0, 0));
            }
            content.addView(acc, Ui.lp(-1, -2));
        }

        content.addView(heading("التنزيلات"));
        LinearLayout dl = Ui.card(this);
        dl.addView(toggle("التنزيل عبر Wi‑Fi فقط", store.wifiOnly(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { store.setWifiOnly(on); }
        }));
        dl.addView(Ui.tv(this, "المساحة المستخدمة: " + Ui.formatBytes(store.usedBytes()) + " — " + store.downloads().size() + " ملف", 13, Ui.MUTED, false));
        TextView del = Ui.button(this, "حذف كل التنزيلات", false);
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Ui.show(new AlertDialog.Builder(SettingsActivity.this).setMessage("حذف كل الملفات المنزّلة من هذا الجهاز؟")
                        .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) { store.deleteAll(); build(); }
                        }).setNegativeButton("إلغاء", null));
            }
        });
        dl.addView(del, Ui.lpMargin(this, -1, -2, 0, 12, 0, 0));
        content.addView(dl, Ui.lp(-1, -2));

        content.addView(heading("القراءة"));
        LinearLayout rd = Ui.card(this);
        rd.addView(toggle("إبقاء الشاشة مضاءة أثناء قراءة الملف", store.keepScreenOn(), new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean on) { store.setKeepScreenOn(on); }
        }));
        content.addView(rd, Ui.lp(-1, -2));

        content.addView(heading("حول التطبيق"));
        LinearLayout about = Ui.card(this);
        String ver = "1.0.0";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) { }
        about.addView(Ui.tv(this, "10thgradeps — الإصدار " + ver, 15, Ui.TEXT, true));
        about.addView(Ui.tv(this, "يعمل بنفس حسابات الموقع واشتراكاته: " + AppConfig.SITE, 12, Ui.MUTED, false), Ui.lpMargin(this, -2, -2, 0, 4, 0, 0));
        TextView site = Ui.button(this, "فتح الموقع في المتصفح", false);
        site.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.SITE))); }
        });
        about.addView(site, Ui.lpMargin(this, -1, -2, 0, 12, 0, 0));
        TextView clear = Ui.button(this, "مسح ذاكرة الصور المؤقتة", false);
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ImageLoader.clearDisk(SettingsActivity.this); Ui.toast(SettingsActivity.this, "تم المسح."); }
        });
        about.addView(clear, Ui.lpMargin(this, -1, -2, 0, 8, 0, 0));
        content.addView(about, Ui.lp(-1, -2));
    }

    private void renameDialog() {
        final EditText et = Ui.input(this, "الاسم الجديد", InputType.TYPE_CLASS_TEXT);
        et.setText(Session.displayName());
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), 0);
        box.addView(et);
        Ui.show(new AlertDialog.Builder(this).setTitle("تغيير الاسم").setView(box)
                .setPositiveButton("حفظ", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        final String n = et.getText().toString().trim();
                        Async.run(new Async.Work<Boolean>() {
                            @Override public Boolean run() throws Exception { Api.updateProfile(n); return Boolean.TRUE; }
                        }, new Async.Done<Boolean>() {
                            @Override public void done(Boolean r, Exception e) {
                                if (e != null) Ui.toast(SettingsActivity.this, Net.friendly(e)); else build();
                            }
                        });
                    }
                }).setNegativeButton("إلغاء", null));
    }

    private void passwordDialog() {
        final EditText cur = Ui.input(this, "كلمة المرور الحالية", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText nw = Ui.input(this, "كلمة المرور الجديدة (8 أحرف على الأقل)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), 0);
        box.addView(cur, Ui.lpMargin(this, -1, -2, 0, 0, 0, 8));
        box.addView(nw);
        Ui.show(new AlertDialog.Builder(this).setTitle("تغيير كلمة المرور").setView(box)
                .setPositiveButton("تغيير", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        final String c = cur.getText().toString();
                        final String n = nw.getText().toString();
                        Async.run(new Async.Work<Boolean>() {
                            @Override public Boolean run() throws Exception { Api.changePassword(c, n); return Boolean.TRUE; }
                        }, new Async.Done<Boolean>() {
                            @Override public void done(Boolean r, Exception e) {
                                Ui.toast(SettingsActivity.this, e != null ? Net.friendly(e) : "تم تغيير كلمة المرور.");
                            }
                        });
                    }
                }).setNegativeButton("إلغاء", null));
    }
}
