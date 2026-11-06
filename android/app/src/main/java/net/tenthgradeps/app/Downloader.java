package net.tenthgradeps.app;

import android.content.Context;
import android.net.ConnectivityManager;

import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

/** Saves a file for offline use. Premium files are fetched only through premium-file (server-side check) and stored encrypted. */
final class Downloader {
    private Downloader() {}

    /** Blocking. Throws a readable error if the download is not allowed or fails. */
    static void download(Context c, FileItem item) throws Exception {
        Store store = Store.get(c);
        if (store.wifiOnly()) {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null && cm.isActiveNetworkMetered()) throw new IOException("التنزيل مفعّل عبر Wi‑Fi فقط (يمكنك تغيير ذلك من الإعدادات).");
        }
        if (item.premium) {
            if (!AppConfig.PREMIUM_OFFLINE_ENABLED) throw new IOException("تنزيل ملفات Premium غير مفعّل.");
            if (!Session.loggedIn()) throw new Net.ApiException(401, "سجّل الدخول أولًا.");
        }
        OutputStream os = store.openForWrite(item);
        boolean ok = false;
        try {
            if (item.premium) {
                try {
                    Net.download("POST", AppConfig.FN + "premium-file", new JSONObject().put("file", item.id), true, os, null);
                } catch (Net.ApiException e) {
                    if (e.code == 401) Api.sessionExpired(c);
                    throw e;
                }
            } else {
                Net.download("GET", item.url(), null, false, os, null);
            }
            ok = true;
        } finally {
            try { os.close(); } catch (IOException ignored) { }
            if (!ok) store.delete(item);
        }
        store.register(item);
    }
}
