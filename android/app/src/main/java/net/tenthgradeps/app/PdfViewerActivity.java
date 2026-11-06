package net.tenthgradeps.app;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Shows a PDF (Android's own PdfRenderer) or an image. Premium files are fetched only through premium-file, kept as a
 * temporary private copy, shown with a watermark and with screenshots blocked, and removed when the screen closes.
 */
public class PdfViewerActivity extends BaseActivity {
    private FileItem item;
    private File tmp;
    private PdfRenderer renderer;
    private ParcelFileDescriptor pfd;
    private int pageCount;
    private float[] ratios;
    private float firstRatio = 1.414f;
    private float zoom = 1f;
    private RecyclerView rv;
    private PageAdapter adapter;
    private FrameLayout body;
    private TextView pageInd, msg;
    private ProgressBar loading;
    private ImageView favBtn, dlBtn;
    private ImageView imageView;
    private final Object lock = new Object();
    private final ExecutorService renderEx = Executors.newSingleThreadExecutor();
    private LruCache<String, Bitmap> cache;
    private boolean destroyed;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        item = (FileItem) getIntent().getSerializableExtra("item");
        if (item == null) { finish(); return; }
        if (item.premium) getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        if (Store.get(this).keepScreenOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cache = new LruCache<String, Bitmap>(Math.max(8 * 1024 * 1024, (int) (Runtime.getRuntime().maxMemory() / 5))) {
            @Override protected int sizeOf(String k, Bitmap v) { return v.getByteCount(); }
        };

        LinearLayout root = Ui.column(this);
        LinearLayout bar = Ui.row(this);
        bar.setPadding(Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 6));
        ImageView back = Ui.iconButton(this, R.drawable.ic_back, Ui.TEXT);
        back.setScaleX(-1f);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        bar.addView(back);
        TextView t = Ui.tv(this, item.title, 15, Ui.TEXT, true);
        t.setMaxLines(2);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(t, Ui.lpWeight(0, -2, 1f));
        ImageView zo = Ui.iconButton(this, R.drawable.ic_remove, Ui.TEXT);
        ImageView zi = Ui.iconButton(this, R.drawable.ic_add, Ui.TEXT);
        zo.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setZoom(zoom - 0.25f); } });
        zi.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setZoom(zoom + 0.25f); } });
        bar.addView(zo);
        bar.addView(zi);
        favBtn = Ui.iconButton(this, R.drawable.ic_star, Ui.MUTED);
        favBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { FileActions.toggleFav(PdfViewerActivity.this, item); refreshButtons(); }
        });
        bar.addView(favBtn);
        dlBtn = Ui.iconButton(this, R.drawable.ic_download, Ui.MUTED);
        dlBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                FileActions.toggleDownload(PdfViewerActivity.this, item, new Runnable() { @Override public void run() { refreshButtons(); } });
            }
        });
        if (!item.premium || AppConfig.PREMIUM_OFFLINE_ENABLED) bar.addView(dlBtn);
        if (!item.premium) {
            ImageView share = Ui.iconButton(this, R.drawable.ic_share, Ui.TEXT);
            share.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Intent s = new Intent(Intent.ACTION_SEND);
                    s.setType("text/plain");
                    s.putExtra(Intent.EXTRA_TEXT, item.title + "\n" + item.url());
                    startActivity(Intent.createChooser(s, "مشاركة"));
                }
            });
            bar.addView(share);
        }
        root.addView(bar, Ui.lp(-1, -2));

        pageInd = Ui.tv(this, "", 12, Ui.MUTED, false);
        pageInd.setGravity(Gravity.CENTER);
        pageInd.setPadding(0, 0, 0, Ui.dp(this, 4));
        root.addView(pageInd, Ui.lp(-1, -2));

        body = new FrameLayout(this);
        body.setBackgroundColor(Ui.SURFACE2);
        root.addView(body, Ui.lpWeight(-1, 0, 1f));
        loading = new ProgressBar(this);
        body.addView(loading, new FrameLayout.LayoutParams(Ui.dp(this, 44), Ui.dp(this, 44), Gravity.CENTER));
        msg = Ui.tv(this, "", 15, Ui.TEXT, false);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        msg.setVisibility(View.GONE);
        body.addView(msg, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));

        setRoot(root);
        refreshButtons();
        load();
    }

    private void refreshButtons() {
        Store s = Store.get(this);
        favBtn.setColorFilter(s.isFav(item) ? Ui.accent() : Ui.MUTED, android.graphics.PorterDuff.Mode.SRC_IN);
        boolean have = s.isDownloaded(item);
        dlBtn.setImageResource(have ? R.drawable.ic_check : R.drawable.ic_download);
        dlBtn.setColorFilter(have ? Ui.accent() : Ui.MUTED, android.graphics.PorterDuff.Mode.SRC_IN);
    }

    private void showMessage(String m) {
        loading.setVisibility(View.GONE);
        msg.setText(m);
        msg.setVisibility(View.VISIBLE);
    }

    private void load() {
        Async.run(new Async.Work<File>() {
            @Override public File run() throws Exception { return prepare(); }
        }, new Async.Done<File>() {
            @Override public void done(File f, Exception e) {
                if (destroyed || isFinishing()) { if (f != null) //noinspection ResultOfMethodCallIgnored
                    f.delete(); return; }
                if (e != null) { showMessage(Net.friendly(e)); return; }
                tmp = f;
                loading.setVisibility(View.GONE);
                if (item.isPdf()) openPdf(f); else openImage(f);
                if (item.premium) body.addView(new WatermarkView(PdfViewerActivity.this, Session.username() + " · 10thgradeps"), new FrameLayout.LayoutParams(-1, -1));
            }
        });
    }

    /** Produces a plain temporary copy: from the offline download, or fetched from the site / premium-file. */
    private File prepare() throws Exception {
        File dir = new File(getCacheDir(), "view");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        File out = File.createTempFile("v", item.isPdf() ? ".pdf" : item.extension(), dir);
        boolean ok = false;
        try {
            Store store = Store.get(this);
            if (store.isDownloaded(item)) {
                if (item.premium && !Session.premiumUsable())
                    throw new IOException("انتهت صلاحية التحقق من اشتراكك. اتصل بالإنترنت لتجديد التحقق ثم أعد المحاولة.");
                store.copyDownloadedTo(item, out);
            } else {
                OutputStream os = new FileOutputStream(out);
                try {
                    if (item.premium) {
                        try {
                            Net.download("POST", AppConfig.FN + "premium-file", new JSONObject().put("file", item.id), true, os, null);
                        } catch (Net.ApiException e) {
                            if (e.code == 401) Api.sessionExpired(this);
                            throw e;
                        }
                    } else {
                        Net.download("GET", item.url(), null, false, os, null);
                        Api.trackOpen(item.title, item.id);
                    }
                } finally {
                    os.close();
                }
            }
            ok = true;
            return out;
        } finally {
            if (!ok) //noinspection ResultOfMethodCallIgnored
                out.delete();
        }
    }

    // ---------------- PDF ----------------
    private int pageWidth() { return Math.round(getResources().getDisplayMetrics().widthPixels * zoom); }

    private void openPdf(File f) {
        try {
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(pfd);
            pageCount = renderer.getPageCount();
            if (pageCount == 0) { showMessage("الملف فارغ."); return; }
            ratios = new float[pageCount];
            synchronized (lock) {
                PdfRenderer.Page p = renderer.openPage(0);
                firstRatio = (float) p.getHeight() / p.getWidth();
                p.close();
            }
        } catch (Exception e) {
            showMessage("تعذر عرض هذا الملف.");
            return;
        }
        rv = new RecyclerView(this);
        final LinearLayoutManager lm = new LinearLayoutManager(this);
        rv.setLayoutManager(lm);
        adapter = new PageAdapter();
        rv.setAdapter(adapter);
        rv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView r, int dx, int dy) { updateIndicator(lm); }
        });
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setFillViewport(false);
        hsv.addView(rv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        body.addView(hsv, 0, new FrameLayout.LayoutParams(-1, -1));
        pageInd.setText("1 / " + pageCount);
    }

    private void updateIndicator(LinearLayoutManager lm) {
        int p = lm.findFirstVisibleItemPosition();
        if (p >= 0) pageInd.setText((p + 1) + " / " + pageCount);
    }

    private final class PageAdapter extends RecyclerView.Adapter<PageAdapter.VH> {
        final class VH extends RecyclerView.ViewHolder {
            final ImageView iv;
            VH(ImageView v) { super(v); iv = v; }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ImageView iv = new ImageView(parent.getContext());
            iv.setBackgroundColor(Color.WHITE);
            iv.setScaleType(ImageView.ScaleType.FIT_XY);
            return new VH(iv);
        }

        @Override public int getItemCount() { return pageCount; }

        @Override public void onBindViewHolder(@NonNull final VH h, final int pos) {
            final int w = pageWidth();
            float r = ratios[pos] > 0 ? ratios[pos] : firstRatio;
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(w, Math.round(w * r));
            lp.bottomMargin = Ui.dp(PdfViewerActivity.this, 8);
            h.iv.setLayoutParams(lp);
            h.iv.setTag(pos);
            Bitmap cached = cache.get(pos + "@" + w);
            if (cached != null) { h.iv.setImageBitmap(cached); return; }
            h.iv.setImageDrawable(null);
            renderEx.execute(new Runnable() {
                @Override public void run() {
                    final Bitmap bmp = renderPage(pos, w);
                    if (bmp == null) return;
                    cache.put(pos + "@" + w, bmp);
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (destroyed) return;
                            if (Integer.valueOf(pos).equals(h.iv.getTag())) {
                                h.iv.setImageBitmap(bmp);
                                ViewGroup.LayoutParams l = h.iv.getLayoutParams();
                                if (l != null && l.height != bmp.getHeight()) { l.height = bmp.getHeight(); h.iv.setLayoutParams(l); }
                            }
                        }
                    });
                }
            });
        }
    }

    private Bitmap renderPage(int pos, int w) {
        synchronized (lock) {
            if (renderer == null || destroyed) return null;
            try {
                PdfRenderer.Page p = renderer.openPage(pos);
                try {
                    int h = Math.max(1, Math.round(w * (float) p.getHeight() / p.getWidth()));
                    ratios[pos] = (float) p.getHeight() / p.getWidth();
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return bmp;
                } finally {
                    p.close();
                }
            } catch (Throwable t) {
                return null;
            }
        }
    }

    // ---------------- images ----------------
    private void openImage(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        int s = 1;
        while (o.outWidth / s > 4096 || o.outHeight / s > 4096) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
        if (bmp == null) { showMessage("تعذر عرض الصورة."); return; }
        imageView = new ImageView(this);
        imageView.setImageBitmap(bmp);
        imageView.setAdjustViewBounds(true);
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.addView(imageView, new FrameLayout.LayoutParams(pageWidth(), ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView sv = new ScrollView(this);
        sv.addView(hsv, new FrameLayout.LayoutParams(-2, -2));
        body.addView(sv, 0, new FrameLayout.LayoutParams(-1, -1));
    }

    // ---------------- zoom ----------------
    private void setZoom(float z) {
        z = Math.max(1f, Math.min(3f, z));
        if (z == zoom) return;
        zoom = z;
        if (adapter != null) { cache.evictAll(); adapter.notifyDataSetChanged(); }
        if (imageView != null) {
            ViewGroup.LayoutParams lp = imageView.getLayoutParams();
            lp.width = pageWidth();
            imageView.setLayoutParams(lp);
        }
    }

    @Override public void onConfigurationChanged(@NonNull Configuration c) {
        super.onConfigurationChanged(c);
        if (adapter != null) { cache.evictAll(); adapter.notifyDataSetChanged(); }
        if (imageView != null) {
            ViewGroup.LayoutParams lp = imageView.getLayoutParams();
            lp.width = pageWidth();
            imageView.setLayoutParams(lp);
        }
    }

    @Override protected void onDestroy() {
        destroyed = true;
        renderEx.shutdownNow();
        synchronized (lock) {
            try { if (renderer != null) renderer.close(); } catch (Exception ignored) { }
            renderer = null;
            try { if (pfd != null) pfd.close(); } catch (Exception ignored) { }
        }
        if (tmp != null) //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        super.onDestroy();
    }
}
