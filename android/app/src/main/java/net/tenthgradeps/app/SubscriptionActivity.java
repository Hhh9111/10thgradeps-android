package net.tenthgradeps.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Premium status of the signed-in account (as confirmed by the server) and the subscription request form.
 * Payment is not handled in the app: the request goes to premium-applications and the administration activates the account.
 */
public class SubscriptionActivity extends BaseActivity {
    private static final String[] SUBJECT_KEYS = {"english", "math", "arabic", "physics", "chemistry", "biology", "digital"};

    private LinearLayout content;
    private String reqType = "full";
    private String platform = "TikTok";
    private String payment = "jawwal-credit";
    private final Set<String> chosen = new HashSet<>();
    private String nameVal = "", contactVal = "", phoneVal = "";
    private EditText nameEt, contactEt, phoneEt;
    private boolean busy;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = Ui.column(this);
        root.addView(topBar("الاشتراك Premium"), Ui.lp(-1, -2));
        ScrollView sv = new ScrollView(this);
        content = Ui.column(this);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 28));
        sv.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, Ui.lpWeight(-1, 0, 1f));
        setRoot(root);
        nameVal = Session.displayName();
        build();
        if (Session.loggedIn() && Net.online(this)) refresh(false);
    }

    private void capture() {
        if (nameEt != null) nameVal = nameEt.getText().toString();
        if (contactEt != null) contactVal = contactEt.getText().toString();
        if (phoneEt != null) phoneVal = phoneEt.getText().toString();
    }

    private void refresh(final boolean announce) {
        Async.run(new Async.Work<Boolean>() {
            @Override public Boolean run() throws Exception { Api.refreshAccount(SubscriptionActivity.this); return Boolean.TRUE; }
        }, new Async.Done<Boolean>() {
            @Override public void done(Boolean r, Exception e) {
                if (isFinishing()) return;
                if (e != null && announce) Ui.toast(SubscriptionActivity.this, Net.friendly(e));
                capture();
                build();
            }
        });
    }

    private void build() {
        content.removeAllViews();
        final int accent = Ui.accent();

        LinearLayout status = Ui.card(this);
        if (!Session.loggedIn()) {
            status.addView(Ui.tv(this, "سجّل الدخول أولًا", 17, Ui.TEXT, true));
            status.addView(Ui.tv(this, "يُربط الاشتراك بحسابك في الموقع، وتتحقق منه الخدمة نفسها التي يستخدمها الموقع.", 13, Ui.MUTED, false), Ui.lpMargin(this, -2, -2, 0, 6, 0, 12));
            TextView login = Ui.button(this, "تسجيل الدخول", true);
            login.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { startActivity(new Intent(SubscriptionActivity.this, LoginActivity.class)); }
            });
            status.addView(login);
            content.addView(status, Ui.lp(-1, -2));
            return;
        }

        if (Session.premiumUsable()) {
            status.addView(Ui.tv(this, "اشتراك Premium مفعّل ✓", 19, Ui.TEXT, true));
            StringBuilder d = new StringBuilder();
            if (Session.premiumOwner()) d.append("حساب المالك — وصول كامل");
            else if ("subjects".equals(Session.premiumType())) {
                d.append("اشتراك مواد محددة");
                List<String> names = new ArrayList<>();
                for (Catalog.Subject s : Catalog.get(this).subjects) if (Session.premiumSubjects().contains(s.key)) names.add(s.name);
                if (!names.isEmpty()) d.append("\nالمواد: ").append(android.text.TextUtils.join("، ", names));
            } else d.append("اشتراك كامل لكل المواد");
            String exp = Session.premiumExpiresAt();
            d.append("\nالانتهاء: ").append(exp.isEmpty() ? "غير محدد (دائم)" : exp.substring(0, Math.min(10, exp.length())));
            status.addView(Ui.tv(this, d.toString(), 14, Ui.TEXT, false), Ui.lpMargin(this, -2, -2, 0, 8, 0, 0));
        } else {
            status.addView(Ui.tv(this, "حسابك مجاني حاليًا", 18, Ui.TEXT, true));
            String m = Session.premiumMessage();
            String text = "الحساب: @" + Session.username();
            if (!m.isEmpty()) text += "\n" + m;
            status.addView(Ui.tv(this, text, 13, Ui.MUTED, false), Ui.lpMargin(this, -2, -2, 0, 6, 0, 0));
        }
        TextView refresh = Ui.button(this, "تحديث حالة الاشتراك", false);
        refresh.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { capture(); refresh(true); } });
        status.addView(refresh, Ui.lpMargin(this, -1, -2, 0, 14, 0, 0));
        content.addView(status, Ui.lp(-1, -2));

        final String reqId = Session.get("reqId");
        if (!reqId.isEmpty()) {
            LinearLayout pend = Ui.card(this);
            pend.addView(Ui.tv(this, "لديك طلب اشتراك مرسل", 15, Ui.TEXT, true));
            TextView chk = Ui.button(this, "معرفة حالة الطلب", false);
            chk.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { checkStatus(); } });
            pend.addView(chk, Ui.lpMargin(this, -1, -2, 0, 10, 0, 0));
            content.addView(pend, Ui.lpMargin(this, -1, -2, 0, 12, 0, 0));
        }

        LinearLayout form = Ui.card(this);
        form.addView(Ui.tv(this, Session.premiumUsable() ? "طلب تجديد أو إضافة مواد" : "طلب اشتراك", 16, Ui.TEXT, true));
        form.addView(Ui.tv(this, "الدفع لا يتم داخل التطبيق: بعد إرسال الطلب تراجعه الإدارة وتفعّل الاشتراك على حسابك.", 12, Ui.MUTED, false), Ui.lpMargin(this, -2, -2, 0, 4, 0, 12));

        LinearLayout types = Ui.row(this);
        types.addView(typeChip("اشتراك كامل — 10 ₪", "full"));
        types.addView(typeChip("مواد محددة — 2 ₪ للمادة", "subjects"));
        types.addView(typeChip("سحب مجاني", "draw"));
        android.widget.HorizontalScrollView th = new android.widget.HorizontalScrollView(this);
        th.setHorizontalScrollBarEnabled(false);
        th.addView(types);
        form.addView(th, Ui.lpMargin(this, -1, -2, 0, 0, 0, 12));

        nameEt = Ui.input(this, "الاسم", InputType.TYPE_CLASS_TEXT);
        nameEt.setText(nameVal);
        form.addView(nameEt, Ui.lpMargin(this, -1, -2, 0, 0, 0, 8));
        contactEt = Ui.input(this, "حساب التواصل (بدون @)", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        contactEt.setText(contactVal);
        contactEt.setTextDirection(View.TEXT_DIRECTION_LTR);
        form.addView(contactEt, Ui.lpMargin(this, -1, -2, 0, 0, 0, 8));

        LinearLayout plats = Ui.row(this);
        plats.addView(platChip("TikTok"), Ui.lpMargin(this, -2, -2, 0, 0, 8, 0));
        plats.addView(platChip("Instagram"));
        form.addView(plats, Ui.lpMargin(this, -1, -2, 0, 0, 0, 8));

        phoneEt = Ui.input(this, "رقم الهاتف (اختياري)", InputType.TYPE_CLASS_PHONE);
        phoneEt.setText(phoneVal);
        form.addView(phoneEt, Ui.lpMargin(this, -1, -2, 0, 0, 0, 8));

        if ("subjects".equals(reqType)) {
            form.addView(Ui.tv(this, "اختر المواد:", 13, Ui.MUTED, false), Ui.lpMargin(this, -2, -2, 0, 4, 0, 6));
            android.widget.HorizontalScrollView sh = new android.widget.HorizontalScrollView(this);
            sh.setHorizontalScrollBarEnabled(false);
            LinearLayout subs = Ui.row(this);
            for (Catalog.Subject s : Catalog.get(this).subjects) {
                boolean allowed = false;
                for (String k : SUBJECT_KEYS) if (k.equals(s.key)) allowed = true;
                if (!allowed) continue;
                final String key = s.key;
                final TextView c = Ui.chip(this, s.name, chosen.contains(key));
                c.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) { capture(); if (!chosen.add(key)) chosen.remove(key); build(); }
                });
                subs.addView(c, Ui.lpMargin(this, -2, -2, 0, 0, 8, 0));
            }
            sh.addView(subs);
            form.addView(sh, Ui.lpMargin(this, -1, -2, 0, 0, 0, 8));
        }
        if (!"draw".equals(reqType)) {
            form.addView(Ui.tv(this, "طريقة الدفع:", 13, Ui.MUTED, false), Ui.lpMargin(this, -2, -2, 0, 4, 0, 6));
            LinearLayout pays = Ui.row(this);
            if ("full".equals(reqType)) pays.addView(payChip("رصيد جوّال", "jawwal-credit"), Ui.lpMargin(this, -2, -2, 0, 0, 8, 0));
            pays.addView(payChip("Reflect", "reflect"));
            form.addView(pays, Ui.lpMargin(this, -1, -2, 0, 0, 0, 8));
        }

        TextView submit = Ui.button(this, "إرسال الطلب", true);
        submit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { submit(); } });
        form.addView(submit, Ui.lpMargin(this, -1, -2, 0, 8, 0, 0));
        content.addView(form, Ui.lpMargin(this, -1, -2, 0, 12, 0, 0));

        TextView offers = Ui.button(this, "الأسعار وطرق الدفع في الموقع", false);
        offers.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.SITE + "/premium-offers.html")));
            }
        });
        content.addView(offers, Ui.lpMargin(this, -1, -2, 0, 12, 0, 0));
    }

    private TextView typeChip(String label, final String type) {
        TextView c = Ui.chip(this, label, reqType.equals(type));
        c.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                capture();
                reqType = type;
                if ("subjects".equals(type)) payment = "reflect";
                else if ("full".equals(type) && !"jawwal-credit".equals(payment) && !"reflect".equals(payment)) payment = "jawwal-credit";
                build();
            }
        });
        c.setLayoutParams(Ui.lpMargin(this, -2, -2, 0, 0, 8, 0));
        return c;
    }

    private TextView platChip(final String p) {
        TextView c = Ui.chip(this, p, platform.equals(p));
        c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { capture(); platform = p; build(); } });
        return c;
    }

    private TextView payChip(String label, final String p) {
        TextView c = Ui.chip(this, label, payment.equals(p));
        c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { capture(); payment = p; build(); } });
        return c;
    }

    private void submit() {
        if (busy) return;
        capture();
        if (nameVal.trim().length() < 2 || contactVal.trim().length() < 2) { Ui.toast(this, "تأكد من الاسم وحساب التواصل."); return; }
        if ("subjects".equals(reqType) && chosen.isEmpty()) { Ui.toast(this, "اختر مادة واحدة على الأقل."); return; }
        final String session = UUID.randomUUID().toString();
        final JSONObject body = new JSONObject();
        try {
            body.put("type", reqType).put("name", nameVal.trim()).put("contact", contactVal.trim().replace("@", ""))
                    .put("platform", platform).put("phone", phoneVal.trim()).put("session", session);
            if (!"draw".equals(reqType)) body.put("payment", "subjects".equals(reqType) ? "reflect" : payment);
            if ("subjects".equals(reqType)) body.put("subjects", new JSONArray(new ArrayList<String>(chosen)));
        } catch (Exception e) { Ui.toast(this, "تعذر تجهيز الطلب."); return; }
        busy = true;
        Async.run(new Async.Work<String>() {
            @Override public String run() throws Exception { return Api.applicationSubmit(body); }
        }, new Async.Done<String>() {
            @Override public void done(String id, Exception e) {
                busy = false;
                if (e != null) { Ui.toast(SubscriptionActivity.this, Net.friendly(e)); return; }
                Session.put("reqId", id);
                Session.put("reqSession", session);
                Ui.toast(SubscriptionActivity.this, "وصل طلبك. ستراجعه الإدارة وتتواصل معك.");
                build();
            }
        });
    }

    private void checkStatus() {
        final String id = Session.get("reqId"), sess = Session.get("reqSession");
        if (id.isEmpty()) return;
        Async.run(new Async.Work<JSONObject>() {
            @Override public JSONObject run() throws Exception { return Api.applicationStatus(id, sess); }
        }, new Async.Done<JSONObject>() {
            @Override public void done(JSONObject j, Exception e) {
                if (isFinishing()) return;
                if (e != null) { Ui.toast(SubscriptionActivity.this, Net.friendly(e)); return; }
                String st = j.optString("status");
                String label = "pending".equals(st) ? "قيد المراجعة" : "rejected".equals(st) ? "مرفوض" : st;
                StringBuilder m = new StringBuilder("حالة الطلب: " + label);
                if ("rejected".equals(st) && !j.optString("rejectionReason").isEmpty()) m.append("\nالسبب: ").append(j.optString("rejectionReason"));
                if (j.optBoolean("activatedOnAccount")) m.append("\nتم تفعيل الاشتراك على حسابك.");
                Ui.show(new AlertDialog.Builder(SubscriptionActivity.this).setMessage(m.toString()).setPositiveButton("حسنًا", null));
                if (j.optBoolean("activatedOnAccount")) refresh(false);
            }
        });
    }
}
