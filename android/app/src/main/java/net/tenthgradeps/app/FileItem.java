package net.tenthgradeps.app;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/** One file in the library: either a free (public) file or a Premium (protected) file. */
final class FileItem implements Serializable {
    private static final long serialVersionUID = 1L;

    String key = "";        // "f:<href>" for free, "p:<id>" for Premium
    boolean premium;
    String id = "";         // free: href or function url, Premium: protected-file id (unchanged from the server catalog)
    String title = "";
    String subject = "";    // Arabic subject name
    String subjectKey = "";
    int color = 0xFF2A2A2E;
    String meta = "";
    String extra = "";
    String desc = "";
    String thumb = "";
    String kind = "pdf";    // pdf | img
    String date = "";
    String type = "";       // category used by the type filter

    boolean isPdf() { return !"img".equals(kind); }

    /** Absolute URL of a free file. Path segments are percent-encoded. */
    String url() {
        if (id.startsWith("http")) return id;
        if (id.startsWith("/")) return AppConfig.SITE + id;
        String[] parts = id.split("/");
        StringBuilder sb = new StringBuilder(AppConfig.SITE);
        for (String p : parts) {
            sb.append('/');
            try { sb.append(java.net.URLEncoder.encode(p, "UTF-8").replace("+", "%20")); } catch (Exception e) { sb.append(p); }
        }
        return sb.toString();
    }

    String extension() {
        String low = id.toLowerCase(java.util.Locale.US);
        if (low.endsWith(".png")) return ".png";
        if (low.endsWith(".jpg") || low.endsWith(".jpeg")) return ".jpg";
        if (low.endsWith(".webp")) return ".webp";
        return isPdf() ? ".pdf" : ".img";
    }

    JSONObject toJson() {
        try {
            return new JSONObject().put("key", key).put("premium", premium).put("id", id).put("title", title)
                    .put("subject", subject).put("subjectKey", subjectKey).put("color", color).put("meta", meta)
                    .put("extra", extra).put("desc", desc).put("thumb", thumb).put("kind", kind).put("date", date)
                    .put("type", type);
        } catch (JSONException e) { return new JSONObject(); }
    }

    static FileItem fromJson(JSONObject j) {
        FileItem f = new FileItem();
        f.key = j.optString("key"); f.premium = j.optBoolean("premium"); f.id = j.optString("id");
        f.title = j.optString("title"); f.subject = j.optString("subject"); f.subjectKey = j.optString("subjectKey");
        f.color = j.optInt("color", 0xFF2A2A2E); f.meta = j.optString("meta"); f.extra = j.optString("extra");
        f.desc = j.optString("desc"); f.thumb = j.optString("thumb"); f.kind = j.optString("kind", "pdf");
        f.date = j.optString("date"); f.type = j.optString("type");
        return f;
    }
}
