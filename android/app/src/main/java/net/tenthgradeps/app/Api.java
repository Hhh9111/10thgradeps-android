package net.tenthgradeps.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/** Calls to the website's existing Netlify Functions. All methods block: run them through {@link Async}. */
final class Api {
    private Api() {}

    private static JSONObject parse(String t) {
        try { return new JSONObject(t); } catch (Exception e) { return new JSONObject(); }
    }

    private static void ensureOk(Net.Resp r, JSONObject j, String fallback) throws Net.ApiException {
        if (r.code < 200 || r.code >= 300 || (j.has("ok") && !j.optBoolean("ok"))) {
            String m = j.optString("error", "");
            throw new Net.ApiException(r.code, m.isEmpty() ? fallback : m);
        }
    }

    // ---------- accounts (student-auth) ----------
    static void login(String username, String password) throws Exception {
        JSONObject b = new JSONObject().put("username", username).put("password", password);
        Net.Resp r = Net.request("POST", AppConfig.FN + "student-auth?action=login", b, false);
        JSONObject j = parse(r.text);
        ensureOk(r, j, "تعذر تسجيل الدخول.");
        if (Session.token().isEmpty()) throw new Net.ApiException(500, "لم يُرجع الخادم جلسة صالحة.");
        Session.saveUser(j.optJSONObject("user"));
    }

    static void register(String first, String family, String username, String password) throws Exception {
        JSONObject b = new JSONObject().put("firstName", first).put("familyName", family)
                .put("username", username).put("password", password);
        Net.Resp r = Net.request("POST", AppConfig.FN + "student-auth?action=register", b, false);
        JSONObject j = parse(r.text);
        ensureOk(r, j, "تعذر إنشاء الحساب.");
        if (Session.token().isEmpty()) throw new Net.ApiException(500, "لم يُرجع الخادم جلسة صالحة.");
        Session.saveUser(j.optJSONObject("user"));
    }

    static String forgot(String username, String contact) throws Exception {
        JSONObject b = new JSONObject().put("username", username).put("contact", contact);
        Net.Resp r = Net.request("POST", AppConfig.FN + "student-auth?action=forgot", b, false);
        JSONObject j = parse(r.text);
        ensureOk(r, j, "تعذر إرسال الطلب.");
        return j.optString("message", "وصل طلبك.");
    }

    static void logout(Context c) {
        try { Net.request("POST", AppConfig.FN + "student-auth?action=logout", new JSONObject(), true); } catch (Exception ignored) { }
        sessionExpired(c);
    }

    /** Forget the session locally and remove everything that only a signed-in Premium user may keep. */
    static void sessionExpired(Context c) {
        Session.clear();
        Store.get(c).wipePremium();
    }

    /** Reads the account from the server and re-checks Premium (the server is the only authority). */
    static void refreshAccount(Context c) throws Exception {
        if (!Session.loggedIn()) return;
        Net.Resp r = Net.request("GET", AppConfig.FN + "student-auth?action=me", null, true);
        JSONObject j = parse(r.text);
        if (r.code != 200) throw new Net.ApiException(r.code, Net.messageOf(r.text, r.code));
        if (!j.optBoolean("authenticated")) { sessionExpired(c); return; }
        JSONObject user = j.optJSONObject("user");
        Session.saveUser(user);
        Session.saveMe(j);
        JSONObject prem = j.optJSONObject("premium");
        boolean owner = user != null && "owner".equals(user.optString("role"));
        if (owner || prem != null) verifyPremium(prem, owner);
        else Session.setPremium(false, false, "", "", "");
        Session.markRefreshed();
    }

    private static void verifyPremium(JSONObject prem, boolean owner) throws Exception {
        Net.Resp r = Net.request("POST", AppConfig.FN + "premium-login", new JSONObject().put("source", "app"), true);
        if (r.code == 200) {
            String type = prem == null ? "full" : prem.optString("type", "full");
            String exp = (prem == null || prem.isNull("expiresAt")) ? "" : prem.optString("expiresAt", "");
            JSONArray subs = prem == null ? null : prem.optJSONArray("subjects");
            StringBuilder csv = new StringBuilder();
            if ("subjects".equals(type) && subs != null) for (int i = 0; i < subs.length(); i++) {
                if (csv.length() > 0) csv.append(',');
                csv.append(subs.optString(i));
            }
            Session.setPremium(true, owner, type, csv.toString(), exp);
        } else if (r.code == 401 || r.code == 403) {
            Session.setPremium(false, owner, "", "", "");
            Session.setPremiumMessage(Net.messageOf(r.text, r.code));
        } else {
            // 503 (maintenance) or a transient failure: keep the last confirmed state, remember the message.
            Session.setPremiumMessage(Net.messageOf(r.text, r.code));
        }
    }

    static void updateProfile(String displayName) throws Exception {
        Net.Resp r = Net.request("POST", AppConfig.FN + "student-auth?action=update-profile", new JSONObject().put("displayName", displayName), true);
        JSONObject j = parse(r.text);
        ensureOk(r, j, "تعذر حفظ الاسم.");
        Session.saveUser(j.optJSONObject("user"));
    }

    static void changePassword(String current, String next) throws Exception {
        JSONObject b = new JSONObject().put("current", current).put("next", next);
        Net.Resp r = Net.request("POST", AppConfig.FN + "student-auth?action=change-password", b, true);
        ensureOk(r, parse(r.text), "تعذر تغيير كلمة المرور.");
    }

    static void markRead() {
        try { Net.request("POST", AppConfig.FN + "student-auth?action=mark-read", new JSONObject(), true); } catch (Exception ignored) { }
        Session.put("unread", "0");
    }

    /** Same call the website makes when a signed-in student opens a file (feeds the account's "opened files"). */
    static void trackOpen(String title, String file) {
        try {
            if (Session.loggedIn()) Net.request("POST", AppConfig.FN + "student-auth?action=track-open",
                    new JSONObject().put("title", title).put("file", file), true);
        } catch (Exception ignored) { }
    }

    // ---------- catalog / status ----------
    static void loadStatus(Context c) throws IOException {
        Net.Resp r = Net.request("GET", AppConfig.FN + "status", null, false);
        if (r.code == 200) Catalog.get(c).applyStatus(parse(r.text));
    }

    static void loadSiteFiles(Context c) throws IOException {
        Net.Resp r = Net.request("GET", AppConfig.FN + "site-files", null, false);
        if (r.code == 200) Catalog.get(c).setRemoteFree(parse(r.text).optJSONArray("files"));
    }

    static void loadPremiumList(Context c) throws Exception {
        Net.Resp r = Net.request("POST", AppConfig.FN + "premium-list", new JSONObject(), true);
        JSONObject j = parse(r.text);
        if (r.code == 401) { sessionExpired(c); throw new Net.ApiException(401, Net.messageOf(r.text, 401)); }
        if (r.code != 200) throw new Net.ApiException(r.code, Net.messageOf(r.text, r.code));
        Catalog.get(c).setRemotePremium(j.optJSONArray("files"), j.optJSONArray("locked"));
    }

    static JSONArray premiumSearch(String q) throws Exception {
        Net.Resp r = Net.request("POST", AppConfig.FN + "premium-search", new JSONObject().put("q", q), true);
        if (r.code != 200) throw new Net.ApiException(r.code, Net.messageOf(r.text, r.code));
        JSONArray a = parse(r.text).optJSONArray("results");
        return a == null ? new JSONArray() : a;
    }

    static void premiumRequest(String type, String subjectKey, String text) throws Exception {
        JSONObject b = new JSONObject().put("type", type).put("subject", subjectKey).put("text", text);
        Net.Resp r = Net.request("POST", AppConfig.FN + "premium-request", b, true);
        ensureOk(r, parse(r.text), "تعذر إرسال الطلب.");
    }

    // ---------- subscription requests (premium-applications) ----------
    static String applicationSubmit(JSONObject body) throws Exception {
        body.put("action", "submit");
        Net.Resp r = Net.request("POST", AppConfig.FN + "premium-applications", body, true);
        JSONObject j = parse(r.text);
        ensureOk(r, j, "تعذر إرسال الطلب.");
        return j.optString("id");
    }

    static JSONObject applicationStatus(String id, String session) throws Exception {
        JSONObject b = new JSONObject().put("action", "status").put("id", id).put("session", session);
        Net.Resp r = Net.request("POST", AppConfig.FN + "premium-applications", b, true);
        JSONObject j = parse(r.text);
        ensureOk(r, j, "تعذر قراءة حالة الطلب.");
        return j;
    }

    @SuppressWarnings("unused")
    private static void unused() throws JSONException { }
}
