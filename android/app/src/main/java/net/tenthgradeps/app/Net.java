package net.tenthgradeps.app;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Minimal HTTPS client. The session is the site's own cookie, kept in {@link Session}. */
final class Net {
    private Net() {}

    static final class ApiException extends IOException {
        final int code;
        ApiException(int code, String message) { super(message); this.code = code; }
    }

    static final class Resp {
        int code;
        String text = "";
    }

    interface Progress { void onBytes(long done, long total); }

    private static HttpURLConnection open(String method, String path, String json, boolean auth) throws IOException {
        URL u = new URL(path.startsWith("http") ? path : AppConfig.SITE + path);
        HttpURLConnection c = (HttpURLConnection) u.openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestMethod(method);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", AppConfig.UA);
        c.setRequestProperty("Accept", "application/json, application/pdf, */*");
        if (auth) {
            String t = Session.token();
            if (!t.isEmpty()) c.setRequestProperty("Cookie", Session.COOKIE + "=" + t);
        }
        if (json != null) {
            byte[] b = json.getBytes(StandardCharsets.UTF_8);
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setFixedLengthStreamingMode(b.length);
            OutputStream o = c.getOutputStream();
            try { o.write(b); } finally { o.close(); }
        }
        return c;
    }

    /** Picks the session cookie out of Set-Cookie (header names are lower-case over HTTP/2, so compare ignoring case). */
    private static void captureCookie(HttpURLConnection c) {
        Map<String, List<String>> h = c.getHeaderFields();
        if (h == null) return;
        for (Map.Entry<String, List<String>> e : h.entrySet()) {
            if (e.getKey() == null || !"set-cookie".equalsIgnoreCase(e.getKey())) continue;
            for (String line : e.getValue()) {
                String prefix = Session.COOKIE + "=";
                if (line == null || !line.startsWith(prefix)) continue;
                int end = line.indexOf(';');
                String value = (end < 0 ? line.substring(prefix.length()) : line.substring(prefix.length(), end)).trim();
                Session.setToken(value);
            }
        }
    }

    static Resp request(String method, String path, JSONObject body, boolean auth) throws IOException {
        HttpURLConnection c = open(method, path, body == null ? null : body.toString(), auth);
        try {
            int code = c.getResponseCode();
            captureCookie(c);
            InputStream is = code >= 400 ? c.getErrorStream() : c.getInputStream();
            Resp r = new Resp();
            r.code = code;
            r.text = is == null ? "" : readText(is);
            return r;
        } finally {
            c.disconnect();
        }
    }

    /** Streams the response body into {@code out}. Any non-200 answer becomes an {@link ApiException} with the server's message. */
    static long download(String method, String path, JSONObject body, boolean auth, OutputStream out, Progress p) throws IOException {
        HttpURLConnection c = open(method, path, body == null ? null : body.toString(), auth);
        try {
            int code = c.getResponseCode();
            captureCookie(c);
            if (code != 200) {
                InputStream es = c.getErrorStream();
                throw new ApiException(code, messageOf(es == null ? "" : readText(es), code));
            }
            long total = c.getContentLengthLong();
            InputStream in = c.getInputStream();
            byte[] buf = new byte[16384];
            long done = 0;
            int n;
            try {
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    done += n;
                    if (p != null) p.onBytes(done, total);
                }
            } finally {
                in.close();
            }
            return done;
        } finally {
            c.disconnect();
        }
    }

    static byte[] getBytes(String path) throws IOException {
        HttpURLConnection c = open("GET", path, null, false);
        try {
            if (c.getResponseCode() != 200) throw new ApiException(c.getResponseCode(), "http " + c.getResponseCode());
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            try { while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); } finally { in.close(); }
            return bo.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    static String readText(InputStream is) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try { while ((n = is.read(buf)) > 0) bo.write(buf, 0, n); } finally { is.close(); }
        return new String(bo.toByteArray(), StandardCharsets.UTF_8);
    }

    static String messageOf(String text, int code) {
        try {
            String m = new JSONObject(text).optString("error", "");
            if (!m.isEmpty()) return m;
        } catch (Exception ignored) { }
        if (code == 401) return "سجّل الدخول أولًا.";
        if (code == 403) return "غير مسموح لك بالوصول إلى هذا الملف.";
        if (code == 404) return "الملف غير موجود.";
        if (code == 423) return "هذه المادة مقفلة مؤقتًا.";
        if (code == 429) return "محاولات كثيرة. حاول بعد قليل.";
        return "تعذر إكمال الطلب (" + code + ").";
    }

    static boolean online(android.content.Context c) {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager) c.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo ni = cm == null ? null : cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) { return true; }
    }

    static String friendly(Exception e) {
        if (e instanceof ApiException) return e.getMessage();
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException || e instanceof java.net.SocketTimeoutException)
            return "لا يوجد اتصال بالإنترنت أو الخادم غير متاح.";
        String m = e.getMessage();
        return (m == null || m.isEmpty()) ? "حدث خطأ غير متوقع." : m;
    }
}
