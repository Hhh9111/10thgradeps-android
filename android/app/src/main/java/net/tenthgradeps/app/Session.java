package net.tenthgradeps.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * The signed-in session. The session cookie value (the same token the website uses) and the cached account /
 * Premium state live in EncryptedSharedPreferences (AES-256, key held in the Android Keystore).
 * Nothing secret is bundled in the app: the Premium decision is always taken by the server.
 */
final class Session {
    private Session() {}

    static final String COOKIE = "10gp_student_session";
    private static final String FILE = "tgps_secure";

    interface Listener { void onSessionChanged(); }

    private static SharedPreferences sp;
    private static final Map<String, String> mem = new HashMap<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static long lastRefresh = 0;

    static synchronized void init(Context c) {
        if (sp != null) return;
        Context app = c.getApplicationContext();
        try {
            sp = create(app);
        } catch (Exception e) {
            // The keystore entry can become unusable (e.g. after a device restore). Start clean instead of crashing.
            try { app.deleteSharedPreferences(FILE); } catch (Exception ignored) { }
            try { sp = create(app); } catch (Exception e2) { sp = null; }
        }
    }

    private static SharedPreferences create(Context c) throws Exception {
        String alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        return EncryptedSharedPreferences.create(FILE, alias, c,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }

    // ---- raw storage (falls back to memory only if secure storage is unavailable) ----
    static synchronized String get(String k) {
        if (sp != null) return sp.getString(k, "");
        String v = mem.get(k);
        return v == null ? "" : v;
    }

    static synchronized void put(String k, String v) {
        if (sp != null) sp.edit().putString(k, v == null ? "" : v).apply();
        else mem.put(k, v == null ? "" : v);
    }

    static synchronized void remove(String k) {
        if (sp != null) sp.edit().remove(k).apply();
        else mem.remove(k);
    }

    // ---- identity ----
    static String token() { return get("token"); }
    static boolean loggedIn() { return !token().isEmpty(); }
    static String username() { return get("username"); }
    static String displayName() { String d = get("display"); return d.isEmpty() ? username() : d; }
    static boolean isOwner() { return "owner".equals(get("role")); }

    static void setToken(String t) {
        put("token", t);
        if (t == null || t.isEmpty()) clearAccountState();
        notifyChanged();
    }

    static void saveUser(org.json.JSONObject user) {
        if (user == null) return;
        put("username", user.optString("username", ""));
        put("display", user.optString("displayName", ""));
        put("role", user.optString("role", "student"));
        put("state", user.optString("state", "active"));
        notifyChanged();
    }

    static void saveMe(org.json.JSONObject me) {
        put("unread", String.valueOf(me.optInt("unread", 0)));
        org.json.JSONArray n = me.optJSONArray("notifications");
        put("notifications", n == null ? "[]" : n.toString());
    }

    static int unread() { try { return Integer.parseInt(get("unread")); } catch (Exception e) { return 0; } }

    static org.json.JSONArray notifications() {
        try { return new org.json.JSONArray(get("notifications")); } catch (Exception e) { return new org.json.JSONArray(); }
    }

    private static void clearAccountState() {
        String[] keys = {"username", "display", "role", "state", "unread", "notifications", "pActive", "pOwner", "pType",
                "pSubjects", "pExp", "pAt", "pMsg", "reqId", "reqSession"};
        for (String k : keys) remove(k);
    }

    /** Local sign-out: forgets the token and every cached account value. */
    static void clear() {
        clearAccountState();
        remove("token");
        lastRefresh = 0;
        notifyChanged();
    }

    // ---- Premium (as last confirmed by the server) ----
    static void setPremium(boolean active, boolean owner, String type, String subjectsCsv, String expiresAt) {
        put("pActive", active ? "1" : "0");
        put("pOwner", owner ? "1" : "0");
        put("pType", type);
        put("pSubjects", subjectsCsv);
        put("pExp", expiresAt);
        put("pAt", String.valueOf(System.currentTimeMillis()));
        put("pMsg", "");
        notifyChanged();
    }

    static void setPremiumMessage(String m) { put("pMsg", m); }
    static String premiumMessage() { return get("pMsg"); }
    static String premiumType() { return get("pType"); }
    static String premiumExpiresAt() { return get("pExp"); }
    static boolean premiumOwner() { return "1".equals(get("pOwner")); }

    /** Subjects the subscription is limited to; empty means every subject. */
    static Set<String> premiumSubjects() {
        Set<String> s = new HashSet<>();
        for (String p : get("pSubjects").split(",")) if (!p.trim().isEmpty()) s.add(p.trim());
        return s;
    }

    /** True while the last server confirmation is recent enough and the subscription has not expired. */
    static boolean premiumUsable() {
        if (!loggedIn() || !"1".equals(get("pActive"))) return false;
        long at;
        try { at = Long.parseLong(get("pAt")); } catch (Exception e) { return false; }
        long now = System.currentTimeMillis();
        if (now - at > AppConfig.PREMIUM_OFFLINE_GRACE_MS) return false;
        String exp = get("pExp");
        if (!exp.isEmpty()) {
            long t = parseIso(exp);
            if (t > 0 && t <= now) return false;
        }
        return true;
    }

    static long parseIso(String s) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = f.parse(s.length() >= 19 ? s.substring(0, 19) : s);
            return d == null ? 0 : d.getTime();
        } catch (Exception e) { return 0; }
    }

    static boolean refreshDue() { return System.currentTimeMillis() - lastRefresh > AppConfig.ACCOUNT_REFRESH_MS; }
    static void markRefreshed() { lastRefresh = System.currentTimeMillis(); }

    // ---- listeners ----
    static void addListener(Listener l) { synchronized (listeners) { if (!listeners.contains(l)) listeners.add(l); } }
    static void removeListener(Listener l) { synchronized (listeners) { listeners.remove(l); } }

    private static void notifyChanged() {
        final List<Listener> copy;
        synchronized (listeners) { copy = new ArrayList<>(listeners); }
        if (copy.isEmpty()) return;
        Async.MAIN.post(new Runnable() {
            @Override public void run() { for (Listener l : copy) l.onSessionChanged(); }
        });
    }
}
