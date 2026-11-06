package net.tenthgradeps.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class AccountFragment extends Fragment implements MainActivity.Refreshable {
    private LinearLayout content;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        android.content.Context ctx = requireContext();
        ScrollView sv = new ScrollView(ctx);
        sv.setBackgroundColor(Ui.BG);
        content = Ui.column(ctx);
        content.setPadding(0, 0, 0, Ui.dp(ctx, 110));
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
        build();
        return sv;
    }

    @Override public void refreshScreen() { if (content != null && isAdded()) build(); }

    private void build() {
        final android.content.Context c = requireContext();
        content.removeAllViews();
        int accent = Ui.accent();
        content.addView(Ui.largeTitle(c, "حسابي"), Ui.lp(-1, -2));

        LinearLayout card = Ui.card(c);
        card.setBackground(Ui.round(c, Ui.SURFACE, 22));
        if (!Session.loggedIn()) {
            card.addView(Ui.tv(c, "أنت تتصفح كزائر", 17, Ui.TEXT, true));
            card.addView(Ui.tv(c, "سجّل الدخول بحساب الموقع لتفعيل المفضلة على حسابك وملفات Premium.", 13, Ui.MUTED, false), Ui.lpMargin(c, -2, -2, 0, 6, 0, 14));
            TextView login = Ui.button(c, "تسجيل الدخول", true);
            login.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startActivity(new Intent(c, LoginActivity.class)); } });
            card.addView(login);
        } else {
            LinearLayout top = Ui.row(c);
            String initial = Session.displayName().isEmpty() ? "؟" : Session.displayName().substring(0, 1);
            TextView av = Ui.tv(c, initial, 22, Ui.ON_ACCENT, true);
            av.setGravity(Gravity.CENTER);
            av.setBackground(Ui.round(c, accent, 28));
            top.addView(av, new LinearLayout.LayoutParams(Ui.dp(c, 56), Ui.dp(c, 56)));
            LinearLayout names = Ui.column(c);
            names.addView(Ui.tv(c, Session.displayName(), 17, Ui.TEXT, true));
            TextView un = Ui.tv(c, "@" + Session.username(), 13, Ui.MUTED, false);
            un.setTextDirection(View.TEXT_DIRECTION_LTR);
            names.addView(un);
            top.addView(names, Ui.lpMargin(c, 0, -2, 14, 0, 0, 0));
            ((LinearLayout.LayoutParams) names.getLayoutParams()).weight = 1f;
            top.addView(Ui.badge(c, Session.premiumUsable()));
            card.addView(top);

            String plan;
            if (Session.premiumUsable()) {
                plan = Session.premiumOwner() ? "حساب المالك — وصول كامل"
                        : "subjects".equals(Session.premiumType()) ? "اشتراك مواد محددة" : "اشتراك كامل";
                String exp = Session.premiumExpiresAt();
                plan += exp.isEmpty() ? " — دائم" : " — حتى " + exp.substring(0, Math.min(10, exp.length()));
            } else plan = "حساب مجاني";
            card.addView(Ui.tv(c, plan, 13, Ui.MUTED, false), Ui.lpMargin(c, -2, -2, 0, 12, 0, 0));
        }
        content.addView(card, Ui.lpMargin(c, -1, -2, 16, 0, 16, 0));

        if (Session.loggedIn()) {
            content.addView(menu("الاشتراك Premium", SubscriptionActivity.class), Ui.lpMargin(c, -1, -2, 16, 12, 16, 0));
        }
        content.addView(menu("الإعدادات", SettingsActivity.class), Ui.lpMargin(c, -1, -2, 16, 10, 16, 0));

        if (Session.loggedIn()) {
            JSONArray notes = Session.notifications();
            if (notes.length() > 0) {
                content.addView(Ui.sectionTitle(c, "الإشعارات" + (Session.unread() > 0 ? " (" + Session.unread() + " جديد)" : "")), Ui.lpMargin(c, -2, -2, 20, 22, 20, 10));
                for (int i = notes.length() - 1, shown = 0; i >= 0 && shown < 8; i--, shown++) {
                    JSONObject n = notes.optJSONObject(i);
                    if (n == null) continue;
                    LinearLayout nc = Ui.card(c);
                    String t = n.optString("title", "");
                    nc.addView(Ui.tv(c, t, 14, Ui.TEXT, true));
                    String body = n.optString("message", "");
                    if (!body.isEmpty()) nc.addView(Ui.tv(c, body, 13, Ui.MUTED, false), Ui.lpMargin(c, -2, -2, 0, 4, 0, 0));
                    content.addView(nc, Ui.lpMargin(c, -1, -2, 16, 0, 16, 8));
                }
                if (Session.unread() > 0) Async.run(new Async.Work<Boolean>() {
                    @Override public Boolean run() { Api.markRead(); return Boolean.TRUE; }
                }, new Async.Done<Boolean>() { @Override public void done(Boolean r, Exception e) { } });
            }
            if (Session.premiumUsable()) {
                TextView ask = Ui.button(c, "طلب ملف أو حل سؤال", false);
                ask.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { requestDialog(); } });
                content.addView(ask, Ui.lpMargin(c, -1, -2, 16, 16, 16, 0));
            }
            TextView out = Ui.tv(c, "تسجيل الخروج", 15, Ui.DANGER, true);
            out.setGravity(Gravity.CENTER);
            out.setPadding(0, Ui.dp(c, 14), 0, Ui.dp(c, 14));
            out.setBackground(Ui.round(c, Ui.tint(Ui.DANGER, 0x26), 14));
            Ui.pressable(out);
            out.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmLogout(); } });
            content.addView(out, Ui.lpMargin(c, -1, -2, 16, 20, 16, 0));
        }
    }

    private View menu(String label, final Class<?> target) {
        final android.content.Context c = requireContext();
        LinearLayout r = Ui.row(c);
        r.setPadding(Ui.dp(c, 16), Ui.dp(c, 15), Ui.dp(c, 16), Ui.dp(c, 15));
        r.setBackground(Ui.round(c, Ui.SURFACE, 16));
        Ui.pressable(r);
        TextView t = Ui.tv(c, label, 15, Ui.TEXT, true);
        r.addView(t, Ui.lpWeight(0, -2, 1f));
        r.addView(Ui.tv(c, "‹", 20, Ui.MUTED, false));
        r.setClickable(true);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startActivity(new Intent(c, target)); } });
        return r;
    }

    private void confirmLogout() {
        final android.content.Context c = requireContext();
        Ui.show(new AlertDialog.Builder(c).setMessage("تسجيل الخروج؟ ستُحذف ملفات Premium المنزّلة من هذا الجهاز.")
                .setPositiveButton("خروج", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        final android.content.Context app = c.getApplicationContext();
                        Async.run(new Async.Work<Boolean>() {
                            @Override public Boolean run() { Api.logout(app); return Boolean.TRUE; }
                        }, new Async.Done<Boolean>() {
                            @Override public void done(Boolean r, Exception e) { if (isAdded()) build(); }
                        });
                    }
                }).setNegativeButton("إلغاء", null));
    }

    private void requestDialog() {
        final android.content.Context c = requireContext();
        final List<String> keys = new ArrayList<>(), names = new ArrayList<>();
        for (Catalog.Subject s : Catalog.get(c).subjects) { keys.add(s.key); names.add(s.name); }
        LinearLayout box = Ui.column(c);
        box.setPadding(Ui.dp(c, 20), Ui.dp(c, 10), Ui.dp(c, 20), 0);
        final android.widget.Spinner sp = new android.widget.Spinner(c);
        sp.setAdapter(new android.widget.ArrayAdapter<>(c, android.R.layout.simple_spinner_dropdown_item, names));
        box.addView(sp, Ui.lpMargin(c, -1, -2, 0, 0, 0, 8));
        final EditText et = Ui.input(c, "اكتب طلبك (اسم الملف أو السؤال)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        et.setMinLines(3);
        box.addView(et);
        Ui.show(new AlertDialog.Builder(c).setTitle("طلب ملف أو حل سؤال").setView(box)
                .setPositiveButton("إرسال", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        final String subj = keys.get(Math.max(0, sp.getSelectedItemPosition()));
                        final String text = et.getText().toString().trim();
                        if (text.length() < 6 || text.length() > 300) { Ui.toast(c, "اكتب الطلب بين 6 و300 حرف، بدون روابط."); return; }
                        Async.run(new Async.Work<Boolean>() {
                            @Override public Boolean run() throws Exception { Api.premiumRequest("file", subj, text); return Boolean.TRUE; }
                        }, new Async.Done<Boolean>() {
                            @Override public void done(Boolean r, Exception e) {
                                Ui.toast(c, e != null ? Net.friendly(e) : "وصل طلبك إلى الإدارة.");
                            }
                        });
                    }
                }).setNegativeButton("إلغاء", null));
    }
}
