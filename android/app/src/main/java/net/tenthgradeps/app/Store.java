package net.tenthgradeps.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedFile;
import androidx.security.crypto.MasterKeys;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Favorites and offline downloads.
 * Free files are kept as plain files in private app storage. Premium files are written through EncryptedFile
 * (AES-256-GCM, Keystore-backed), are never shared or exported, and are deleted on sign-out.
 */
final class Store {
    private static Store instance;

    static synchronized Store get(Context c) {
        if (instance == null) instance = new Store(c.getApplicationContext());
        return instance;
    }

    private final Context ctx;
    private final SharedPreferences prefs;

    private Store(Context c) {
        ctx = c;
        prefs = c.getSharedPreferences("tgps_store", Context.MODE_PRIVATE);
    }

    // ---------- settings ----------
    boolean wifiOnly() { return prefs.getBoolean("wifi_only", false); }
    void setWifiOnly(boolean v) { prefs.edit().putBoolean("wifi_only", v).apply(); }
    boolean keepScreenOn() { return prefs.getBoolean("keep_on", true); }
    void setKeepScreenOn(boolean v) { prefs.edit().putBoolean("keep_on", v).apply(); }

    /** 0 = black (default), 1 = white, 2 = follow the device. */
    int themeMode() { return prefs.getInt("theme_mode", 0); }
    void setThemeMode(int m) { prefs.edit().putInt("theme_mode", m).apply(); }

    // ---------- favorites (kept per account, or per device when signed out) ----------
    private String favKey() { return "fav_" + (Session.loggedIn() ? Session.username() : "guest"); }

    boolean isFav(FileItem f) { return prefs.getStringSet(favKey(), new HashSet<String>()).contains(f.key); }

    boolean toggleFav(FileItem f) {
        Set<String> s = new HashSet<>(prefs.getStringSet(favKey(), new HashSet<String>()));
        boolean now;
        if (s.contains(f.key)) { s.remove(f.key); now = false; } else { s.add(f.key); now = true; }
        prefs.edit().putStringSet(favKey(), s).apply();
        return now;
    }

    List<FileItem> favorites() {
        List<FileItem> out = new ArrayList<>();
        Catalog cat = Catalog.get(ctx);
        for (String k : prefs.getStringSet(favKey(), new HashSet<String>())) {
            FileItem f = cat.findByKey(k);
            if (f == null) continue;
            if (f.premium && !Session.premiumUsable()) continue;
            out.add(f);
        }
        return out;
    }

    // ---------- downloads ----------
    private File dir(boolean premium) {
        File d = new File(ctx.getFilesDir(), premium ? "downloads_premium" : "downloads_free");
        if (!d.exists()) //noinspection ResultOfMethodCallIgnored
            d.mkdirs();
        return d;
    }

    private static String sha1(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }

    File fileFor(FileItem f) {
        return new File(dir(f.premium), sha1(f.key) + (f.premium ? ".enc" : f.extension()));
    }

    private JSONArray index() {
        try { return new JSONArray(prefs.getString("dl_index", "[]")); } catch (Exception e) { return new JSONArray(); }
    }

    synchronized boolean isDownloaded(FileItem f) {
        JSONArray a = index();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && f.key.equals(o.optString("key")) && fileFor(f).exists()) return true;
        }
        return false;
    }

    synchronized List<FileItem> downloads() {
        List<FileItem> out = new ArrayList<>();
        JSONArray a = index();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o == null) continue;
            FileItem f = FileItem.fromJson(o.optJSONObject("item") == null ? new JSONObject() : o.optJSONObject("item"));
            if (f.key.isEmpty() || !fileFor(f).exists()) continue;
            out.add(f);
        }
        return out;
    }

    synchronized void register(FileItem f) {
        JSONArray a = index();
        JSONArray n = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !f.key.equals(o.optString("key"))) n.put(o);
        }
        try { n.put(new JSONObject().put("key", f.key).put("item", f.toJson()).put("at", System.currentTimeMillis())); } catch (Exception ignored) { }
        prefs.edit().putString("dl_index", n.toString()).apply();
    }

    synchronized void delete(FileItem f) {
        //noinspection ResultOfMethodCallIgnored
        fileFor(f).delete();
        JSONArray a = index();
        JSONArray n = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !f.key.equals(o.optString("key"))) n.put(o);
        }
        prefs.edit().putString("dl_index", n.toString()).apply();
    }

    synchronized void wipePremium() {
        File[] files = dir(true).listFiles();
        if (files != null) for (File x : files) //noinspection ResultOfMethodCallIgnored
            x.delete();
        JSONArray a = index();
        JSONArray n = new JSONArray();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            JSONObject item = o == null ? null : o.optJSONObject("item");
            if (o != null && item != null && !item.optBoolean("premium")) n.put(o);
        }
        prefs.edit().putString("dl_index", n.toString()).apply();
    }

    synchronized void deleteAll() {
        for (FileItem f : downloads()) delete(f);
    }

    long usedBytes() {
        long total = 0;
        for (FileItem f : downloads()) total += fileFor(f).length();
        return total;
    }

    // ---------- encrypted Premium files ----------
    private EncryptedFile encrypted(File f) throws Exception {
        String alias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
        return new EncryptedFile.Builder(f, ctx, alias, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build();
    }

    /** Opens the destination stream for a download of {@code f} (encrypted for Premium). The file must not exist yet. */
    OutputStream openForWrite(FileItem f) throws Exception {
        File dest = fileFor(f);
        if (dest.exists()) //noinspection ResultOfMethodCallIgnored
            dest.delete();
        if (f.premium) return encrypted(dest).openFileOutput();
        return new FileOutputStream(dest);
    }

    /** Copies a downloaded file (decrypting Premium ones) to a temporary plain file used by the viewer. */
    void copyDownloadedTo(FileItem f, File out) throws Exception {
        File src = fileFor(f);
        InputStream in = f.premium ? encrypted(src).openFileInput() : new FileInputStream(src);
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            try { in.close(); } catch (IOException ignored) { }
            os.close();
        }
    }
}
