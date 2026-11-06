package net.tenthgradeps.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.security.MessageDigest;

/** Loads public site images (thumbnails / subject covers) with a memory and a disk cache. */
final class ImageLoader {
    private ImageLoader() {}

    private static final LruCache<String, Bitmap> MEM = new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 12)) {
        @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
    };

    private static String sha1(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return Integer.toHexString(s.hashCode()); }
    }

    /** {@code path} is relative to the site root (for example "thumbs/math-exam-01.webp"). */
    static void load(final Context ctx, final ImageView iv, final String path, final int reqW, final int reqH) {
        iv.setTag(path);
        iv.setImageDrawable(null);
        if (path == null || path.isEmpty()) return;
        final String memKey = path + "@" + reqW;
        Bitmap hit = MEM.get(memKey);
        if (hit != null) { iv.setImageBitmap(hit); return; }
        Async.IO.execute(new Runnable() {
            @Override public void run() {
                final Bitmap bmp = fetch(ctx, path, reqW, reqH);
                if (bmp == null) return;
                MEM.put(memKey, bmp);
                Async.MAIN.post(new Runnable() {
                    @Override public void run() { if (path.equals(iv.getTag())) iv.setImageBitmap(bmp); }
                });
            }
        });
    }

    private static Bitmap fetch(Context ctx, String path, int reqW, int reqH) {
        try {
            File dir = new File(ctx.getCacheDir(), "img");
            if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            File f = new File(dir, sha1(path));
            if (!f.exists()) {
                byte[] data = Net.getBytes(encode(path));
                File tmp = new File(dir, f.getName() + ".tmp");
                FileOutputStream os = new FileOutputStream(tmp);
                try { os.write(data); } finally { os.close(); }
                //noinspection ResultOfMethodCallIgnored
                tmp.renameTo(f);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int s = 1;
            while (o.outWidth / (s * 2) >= reqW && o.outHeight / (s * 2) >= reqH) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        } catch (Exception e) {
            return null;
        }
    }

    private static String encode(String path) {
        StringBuilder sb = new StringBuilder(AppConfig.SITE);
        for (String p : path.split("/")) {
            sb.append('/');
            try { sb.append(java.net.URLEncoder.encode(p, "UTF-8").replace("+", "%20")); } catch (Exception e) { sb.append(p); }
        }
        return sb.toString();
    }

    static void clearDisk(Context ctx) {
        File[] fs = new File(ctx.getCacheDir(), "img").listFiles();
        if (fs != null) for (File f : fs) //noinspection ResultOfMethodCallIgnored
            f.delete();
        MEM.evictAll();
    }
}
