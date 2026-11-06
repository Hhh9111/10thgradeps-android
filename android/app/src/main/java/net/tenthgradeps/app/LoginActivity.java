package net.tenthgradeps.app;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/** Signs in with the website's own accounts (student-auth). The same screen can create a site account via the site's register action. */
public class LoginActivity extends BaseActivity {
    private boolean register;
    private EditText first, family, user, pass;
    private TextView submit, toggle, error, title;
    private LinearLayout nameRow;
    private boolean busy;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = Ui.column(this);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(Ui.dp(this, 24), Ui.dp(this, 56), Ui.dp(this, 24), Ui.dp(this, 24));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        col.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)));
        title = Ui.tv(this, "", 22, Ui.TEXT, true);
        col.addView(title, Ui.lpMargin(this, -2, -2, 0, 14, 0, 4));
        TextView sub = Ui.tv(this, "بنفس حساب موقع 10thgradeps", 13, Ui.MUTED, false);
        col.addView(sub, Ui.lpMargin(this, -2, -2, 0, 0, 0, 22));

        nameRow = Ui.row(this);
        first = Ui.input(this, "الاسم", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        family = Ui.input(this, "اسم العائلة", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        nameRow.addView(first, Ui.lpMargin(this, 0, -2, 0, 0, 5, 0));
        ((LinearLayout.LayoutParams) first.getLayoutParams()).weight = 1f;
        nameRow.addView(family, Ui.lpMargin(this, 0, -2, 5, 0, 0, 0));
        ((LinearLayout.LayoutParams) family.getLayoutParams()).weight = 1f;
        col.addView(nameRow, Ui.lpMargin(this, -1, -2, 0, 0, 0, 10));

        user = Ui.input(this, "اسم المستخدم", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        user.setTextDirection(View.TEXT_DIRECTION_LTR);
        col.addView(user, Ui.lpMargin(this, -1, -2, 0, 0, 0, 10));
        pass = Ui.input(this, "كلمة المرور", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setTextDirection(View.TEXT_DIRECTION_LTR);
        col.addView(pass, Ui.lpMargin(this, -1, -2, 0, 0, 0, 10));

        error = Ui.tv(this, "", 13, Ui.DANGER, false);
        error.setVisibility(View.GONE);
        col.addView(error, Ui.lpMargin(this, -1, -2, 0, 0, 0, 10));

        submit = Ui.button(this, "", true);
        col.addView(submit, Ui.lp(-1, -2));
        submit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { submit(); } });

        toggle = Ui.tv(this, "", 14, Ui.accent(), true);
        toggle.setPadding(Ui.dp(this, 10), Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 8));
        toggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { register = !register; render(); } });
        col.addView(toggle);

        TextView forgot = Ui.tv(this, "نسيت كلمة المرور؟", 13, Ui.MUTED, false);
        forgot.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        forgot.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { forgotDialog(); } });
        col.addView(forgot);

        TextView guest = Ui.tv(this, "تصفّح الملفات المجانية بدون حساب", 13, Ui.MUTED, false);
        guest.setPadding(Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 8));
        guest.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        col.addView(guest);

        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        setRoot(sv);
        render();
    }

    private void render() {
        title.setText(register ? "إنشاء حساب في الموقع" : "تسجيل الدخول");
        submit.setText(register ? "إنشاء الحساب" : "دخول");
        toggle.setText(register ? "لديك حساب؟ سجّل الدخول" : "ليس لديك حساب؟ أنشئ حسابًا في الموقع");
        nameRow.setVisibility(register ? View.VISIBLE : View.GONE);
        error.setVisibility(View.GONE);
    }

    private void showError(String m) {
        error.setText(m);
        error.setVisibility(View.VISIBLE);
    }

    private void submit() {
        if (busy) return;
        final String u = user.getText().toString().trim();
        final String p = pass.getText().toString();
        final String f = first.getText().toString().trim();
        final String fam = family.getText().toString().trim();
        if (u.isEmpty() || p.isEmpty()) { showError("أدخل اسم المستخدم وكلمة المرور."); return; }
        if (register && (f.isEmpty() || fam.isEmpty())) { showError("أدخل اسم الطالب واسم العائلة."); return; }
        busy = true;
        submit.setAlpha(0.5f);
        error.setVisibility(View.GONE);
        final boolean reg = register;
        Async.run(new Async.Work<Boolean>() {
            @Override public Boolean run() throws Exception {
                if (reg) Api.register(f, fam, u, p); else Api.login(u, p);
                try { Api.refreshAccount(LoginActivity.this); } catch (Exception ignored) { }
                return Boolean.TRUE;
            }
        }, new Async.Done<Boolean>() {
            @Override public void done(Boolean r, Exception e) {
                busy = false;
                submit.setAlpha(1f);
                if (e != null) { showError(Net.friendly(e)); return; }
                Ui.toast(LoginActivity.this, "أهلًا " + Session.displayName());
                finish();
            }
        });
    }

    private void forgotDialog() {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), 0);
        final EditText u = Ui.input(this, "اسم المستخدم", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        u.setText(user.getText());
        final EditText contact = Ui.input(this, "حساب التواصل (تيك توك أو إنستغرام)", InputType.TYPE_CLASS_TEXT);
        box.addView(u, Ui.lpMargin(this, -1, -2, 0, 0, 0, 8));
        box.addView(contact, Ui.lp(-1, -2));
        Ui.show(new AlertDialog.Builder(this).setTitle("استرجاع كلمة المرور").setView(box)
                .setMessage("تصل الطلبات إلى الإدارة وتتواصل معك لتعيين كلمة مرور جديدة.")
                .setPositiveButton("إرسال", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        final String un = u.getText().toString().trim();
                        final String ct = contact.getText().toString().trim();
                        Async.run(new Async.Work<String>() {
                            @Override public String run() throws Exception { return Api.forgot(un, ct); }
                        }, new Async.Done<String>() {
                            @Override public void done(String r, Exception e) { Ui.toast(LoginActivity.this, e != null ? Net.friendly(e) : r); }
                        });
                    }
                }).setNegativeButton("إلغاء", null));
    }
}
