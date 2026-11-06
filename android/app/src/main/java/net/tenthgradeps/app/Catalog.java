package net.tenthgradeps.app;

import android.content.Context;
import android.graphics.Color;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The file index. The static part is assets/catalog.json, generated at build time from the site's own index.html and
 * premium.html (it holds titles and ids only, never file contents). The dynamic part (files uploaded from the admin
 * panel, hidden/locked subjects, maintenance flags) is fetched from the site's existing functions.
 */
final class Catalog {
    static final class Subject { String key = "", name = ""; int color; }

    static final String[] TYPES = {"الإجابات", "شروحات وملخصات", "امتحانات يومية", "اختبارات قصيرة", "امتحانات شهرية",
            "امتحانات فصلية", "امتحانات الدروس والوحدات"};

    private static Catalog instance;

    static synchronized Catalog get(Context c) {
        if (instance == null) instance = load(c.getApplicationContext());
        return instance;
    }

    final List<Subject> subjects = new ArrayList<>();
    final Map<String, String> covers = new HashMap<>();
    final List<FileItem> free = new ArrayList<>();
    final List<FileItem> premium = new ArrayList<>();
    final List<String> latest = new ArrayList<>();
    volatile List<FileItem> remoteFree = new ArrayList<>();
    volatile List<FileItem> remotePremium = new ArrayList<>();
    volatile Set<String> hidden = new HashSet<>();
    volatile Set<String> lockedSiteNames = new HashSet<>();
    volatile Set<String> lockedPremiumKeys = new HashSet<>();
    volatile boolean siteMaintenance, premiumMaintenance;
    volatile String message = "";

    private static Catalog load(Context c) {
        Catalog cat = new Catalog();
        try {
            InputStream is = c.getAssets().open("catalog.json");
            JSONObject root = new JSONObject(Net.readText(is));
            JSONArray subs = root.optJSONArray("subjects");
            Map<String, Subject> byKey = new HashMap<>();
            if (subs != null) for (int i = 0; i < subs.length(); i++) {
                JSONObject o = subs.getJSONObject(i);
                Subject s = new Subject();
                s.key = o.optString("key"); s.name = o.optString("name");
                try { s.color = Color.parseColor(o.optString("color", "#475569")); } catch (Exception e) { s.color = 0xFF475569; }
                cat.subjects.add(s);
                byKey.put(s.key, s);
            }
            JSONObject cov = root.optJSONObject("covers");
            if (cov != null) {
                java.util.Iterator<String> it = cov.keys();
                while (it.hasNext()) { String k = it.next(); cat.covers.put(k, cov.optString(k)); }
            }
            JSONArray fr = root.optJSONArray("free");
            if (fr != null) for (int i = 0; i < fr.length(); i++) {
                JSONObject o = fr.getJSONObject(i);
                FileItem f = new FileItem();
                f.id = o.optString("href"); f.key = "f:" + f.id; f.title = o.optString("title");
                f.subject = o.optString("subject"); f.kind = o.optString("kind", "pdf");
                f.meta = o.optString("meta"); f.extra = o.optString("extra") + " " + o.optString("name");
                f.thumb = o.optString("thumb"); f.type = typeOf(f.title);
                for (Subject s : cat.subjects) if (s.name.equals(f.subject)) { f.color = s.color; f.subjectKey = s.key; }
                cat.free.add(f);
            }
            JSONArray pr = root.optJSONArray("premium");
            if (pr != null) for (int i = 0; i < pr.length(); i++) cat.premium.add(premiumItem(pr.getJSONObject(i).optString("id"),
                    pr.getJSONObject(i).optString("s"), pr.getJSONObject(i).optString("t"),
                    pr.getJSONObject(i).optString("d"), pr.getJSONObject(i).optString("a"), byKey));
            JSONArray lt = root.optJSONArray("latest");
            if (lt != null) for (int i = 0; i < lt.length(); i++) cat.latest.add(lt.optString(i));
        } catch (Exception e) {
            // An unreadable catalog leaves the lists empty; the app still starts and the remote lists still work.
        }
        return cat;
    }

    private static FileItem premiumItem(String id, String subjectKey, String title, String desc, String date, Map<String, Subject> byKey) {
        FileItem f = new FileItem();
        f.premium = true; f.id = id; f.key = "p:" + id; f.title = title; f.desc = desc; f.date = date;
        f.subjectKey = subjectKey; f.kind = "pdf"; f.type = typeOf(title); f.meta = "Premium";
        Subject s = byKey.get(subjectKey);
        f.subject = s == null ? "أخرى" : s.name;
        f.color = s == null ? 0xFF475569 : s.color;
        return f;
    }

    // ---- remote data ----
    void setRemoteFree(JSONArray files) {
        List<FileItem> out = new ArrayList<>();
        if (files != null) for (int i = 0; i < files.length(); i++) {
            JSONObject u = files.optJSONObject(i);
            if (u == null) continue;
            FileItem f = new FileItem();
            f.id = u.optString("url"); f.key = "f:" + f.id; f.title = u.optString("title"); f.subject = u.optString("subject");
            f.meta = "ملف PDF"; f.extra = f.title + " " + f.subject; f.kind = "pdf"; f.date = u.optString("createdAt");
            f.type = typeOf(f.title);
            for (Subject s : subjects) if (s.name.equals(f.subject)) { f.color = s.color; f.subjectKey = s.key; }
            if (!f.id.isEmpty()) out.add(f);
        }
        remoteFree = out;
    }

    void setRemotePremium(JSONArray files, JSONArray locked) {
        List<FileItem> out = new ArrayList<>();
        Map<String, Subject> byKey = new HashMap<>();
        for (Subject s : subjects) byKey.put(s.key, s);
        if (files != null) for (int i = 0; i < files.length(); i++) {
            JSONObject u = files.optJSONObject(i);
            if (u == null) continue;
            String id = u.optString("id");
            if (id.isEmpty()) continue;
            out.add(premiumItem(id, u.optString("s"), u.optString("t"), u.optString("d"), u.optString("a"), byKey));
        }
        remotePremium = out;
        if (locked != null) {
            Set<String> l = new HashSet<>();
            for (int i = 0; i < locked.length(); i++) l.add(locked.optString(i));
            lockedPremiumKeys = l;
        }
    }

    void applyStatus(JSONObject s) {
        siteMaintenance = s.optBoolean("site");
        premiumMaintenance = s.optBoolean("premium");
        message = s.optString("message", "");
        Set<String> h = new HashSet<>();
        JSONArray ha = s.optJSONArray("hidden");
        if (ha != null) for (int i = 0; i < ha.length(); i++) h.add(ha.optString(i));
        hidden = h;
        Set<String> ls = new HashSet<>();
        JSONArray la = s.optJSONArray("lockedSite");
        if (la != null) for (int i = 0; i < la.length(); i++) ls.add(la.optString(i));
        lockedSiteNames = ls;
        Set<String> lp = new HashSet<>();
        JSONArray pa = s.optJSONArray("lockedPremium");
        if (pa != null) for (int i = 0; i < pa.length(); i++) lp.add(pa.optString(i));
        lockedPremiumKeys = lp;
    }

    // ---- views of the data ----
    List<FileItem> visibleFree() {
        List<FileItem> out = new ArrayList<>();
        List<FileItem> all = new ArrayList<>(free);
        all.addAll(remoteFree);
        for (FileItem f : all) {
            if (hidden.contains(f.id) || lockedSiteNames.contains(f.subject)) continue;
            out.add(f);
        }
        return out;
    }

    List<FileItem> visiblePremium() {
        List<FileItem> out = new ArrayList<>();
        List<FileItem> all = new ArrayList<>(premium);
        Set<String> have = new HashSet<>();
        for (FileItem f : premium) have.add(f.id);
        for (FileItem f : remotePremium) if (!have.contains(f.id)) all.add(f);
        Set<String> allowed = Session.premiumSubjects();
        for (FileItem f : all) {
            if (lockedPremiumKeys.contains(f.subjectKey)) continue;
            if (!allowed.isEmpty() && !allowed.contains(f.subjectKey)) continue;
            out.add(f);
        }
        return out;
    }

    FileItem findByKey(String key) {
        for (FileItem f : free) if (f.key.equals(key)) return f;
        for (FileItem f : remoteFree) if (f.key.equals(key)) return f;
        for (FileItem f : premium) if (f.key.equals(key)) return f;
        for (FileItem f : remotePremium) if (f.key.equals(key)) return f;
        return null;
    }

    FileItem freeByHref(String href) { return findByKey("f:" + href); }

    String coverFor(String subjectName) { return covers.get(subjectName); }

    int countFor(String subjectName, boolean premiumList) {
        int n = 0;
        for (FileItem f : premiumList ? visiblePremium() : visibleFree()) if (f.subject.equals(subjectName)) n++;
        return n;
    }

    static List<FileItem> filter(List<FileItem> in, String subject, String type, String query) {
        List<FileItem> out = new ArrayList<>();
        String[] words = norm(query).trim().isEmpty() ? new String[0] : norm(query).trim().split("\\s+");
        for (FileItem f : in) {
            if (subject != null && !subject.isEmpty() && !f.subject.equals(subject)) continue;
            if (type != null && !type.isEmpty() && !f.type.equals(type)) continue;
            if (words.length > 0) {
                String hay = norm(f.title + " " + f.subject + " " + f.extra + " " + f.desc + " " + f.meta + " " + f.id);
                boolean ok = true;
                for (String w : words) if (!hay.contains(w)) { ok = false; break; }
                if (!ok) continue;
            }
            out.add(f);
        }
        return out;
    }

    /** Same normalisation the website's search uses: lower-case, strip tashkeel/tatweel, unify alef/yaa/taa-marbuta. */
    static String norm(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (char ch : s.toLowerCase(java.util.Locale.ROOT).toCharArray()) {
            if ((ch >= '\u064B' && ch <= '\u0670') || ch == '\u0640') continue;
            switch (ch) {
                case '\u0623': case '\u0625': case '\u0622': case '\u0671': sb.append('\u0627'); break;
                case '\u0649': sb.append('\u064A'); break;
                case '\u0629': sb.append('\u0647'); break;
                case '\u0624': sb.append('\u0648'); break;
                case '\u0626': sb.append('\u064A'); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** Same categories as the website's own filter. */
    static String typeOf(String t) {
        if (t == null) t = "";
        if (t.matches("(?s)^(الإجابات|نماذج إجابات).*")) return "الإجابات";
        if (t.matches("(?s).*(ملخص|شرح|قواعد|حل الوحدة|الحل).*")) return "شروحات وملخصات";
        if (t.contains("اليومي")) return "امتحانات يومية";
        if (t.contains("اختبار الفيزياء القصير")) return "اختبارات قصيرة";
        if (t.matches("(?s).*(شهري|الشهرين|نصف الكتاب|Monthly|Half Book).*")) return "امتحانات شهرية";
        if (t.matches("(?s).*(فصلي|الفصلي|النهائي|Final|الكتاب كاملًا|الكتاب كاملاً|Full Book).*")) return "امتحانات فصلية";
        return "امتحانات الدروس والوحدات";
    }
}
