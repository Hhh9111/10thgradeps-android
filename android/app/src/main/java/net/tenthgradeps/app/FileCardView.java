package net.tenthgradeps.app;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Grid card like the website's: file image on top, subject label, title, meta, favorite and download. */
final class FileCardView extends LinearLayout {
    private final ImageView cover;
    private final LinearLayout lockBox;
    private final TextView lockLabel, subjectPill, title, meta;
    private final ImageView fav, dl;
    private final ProgressBar busy;
    private FileItem item;
    private Runnable onChanged;

    FileCardView(Context c) {
        super(c);
        setOrientation(VERTICAL);
        setBackground(Ui.round(c, Ui.SURFACE, 20));
        setClipToOutline(true);
        setClickable(true);
        Ui.pressable(this);

        FrameLayout box = new FrameLayout(c);
        box.setBackgroundColor(Ui.SURFACE2);
        cover = new ImageView(c);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        box.addView(cover, new FrameLayout.LayoutParams(-1, -1));

        lockBox = Ui.column(c);
        lockBox.setGravity(Gravity.CENTER);
        lockBox.addView(Ui.icon(c, R.drawable.ic_lock, Ui.MUTED, 24));
        lockLabel = Ui.tv(c, "", 15, Ui.TEXT, true);
        lockLabel.setGravity(Gravity.CENTER);
        lockBox.addView(lockLabel, Ui.lpMargin(c, -2, -2, 8, 8, 8, 0));
        box.addView(lockBox, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        subjectPill = Ui.tv(c, "", 11, Ui.TEXT, true);
        subjectPill.setPadding(Ui.dp(c, 10), Ui.dp(c, 4), Ui.dp(c, 10), Ui.dp(c, 4));
        subjectPill.setBackground(Ui.round(c, Ui.tint(Ui.BG, 0xDD), 12));
        FrameLayout.LayoutParams pl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        pl.setMargins(Ui.dp(c, 8), 0, Ui.dp(c, 8), Ui.dp(c, 8));
        box.addView(subjectPill, pl);
        addView(box, new LinearLayout.LayoutParams(-1, Ui.dp(c, 124)));

        LinearLayout info = Ui.column(c);
        info.setPadding(Ui.dp(c, 10), Ui.dp(c, 9), Ui.dp(c, 4), Ui.dp(c, 4));
        title = Ui.tv(c, "", 14, Ui.TEXT, true);
        title.setMinLines(2);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        info.addView(title);
        LinearLayout bottom = Ui.row(c);
        meta = Ui.tv(c, "", 11, Ui.MUTED, false);
        meta.setMaxLines(1);
        meta.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bottom.addView(meta, Ui.lpWeight(0, -2, 1f));
        fav = Ui.iconButton(c, R.drawable.ic_star, Ui.MUTED);
        fav.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        fav.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        bottom.addView(fav);
        Holder h = new Holder(c);
        dl = h.icon;
        busy = h.bar;
        bottom.addView(h, new LinearLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
        info.addView(bottom, Ui.lpMargin(c, -1, -2, 0, 2, 0, 0));
        addView(info, new LinearLayout.LayoutParams(-1, -2));
    }

    private static final class Holder extends FrameLayout {
        final ImageView icon;
        final ProgressBar bar;
        Holder(Context c) {
            super(c);
            icon = Ui.iconButton(c, R.drawable.ic_download, Ui.MUTED);
            icon.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
            addView(icon, new FrameLayout.LayoutParams(Ui.dp(c, 36), Ui.dp(c, 36)));
            bar = new ProgressBar(c);
            bar.setIndeterminate(true);
            bar.setVisibility(GONE);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(Ui.dp(c, 20), Ui.dp(c, 20));
            p.gravity = Gravity.CENTER;
            addView(bar, p);
        }
    }

    void bind(final FileItem f, final Runnable changed) {
        item = f;
        onChanged = changed;
        final Context c = getContext();
        title.setText(f.title);
        subjectPill.setText(f.subject);
        if (f.premium) {
            cover.setImageDrawable(null);
            cover.setTag(null);
            lockBox.setVisibility(VISIBLE);
            lockLabel.setText(f.subject);
            subjectPill.setVisibility(GONE);
            meta.setText(f.desc.isEmpty() ? "Premium" : f.desc);
        } else {
            lockBox.setVisibility(GONE);
            subjectPill.setVisibility(VISIBLE);
            String path = f.thumb;
            if (path == null || path.isEmpty()) {
                String sc = Catalog.get(c).coverFor(f.subject);
                path = sc == null ? "" : sc;
            }
            ImageLoader.load(c, cover, path, Ui.dp(c, 180), Ui.dp(c, 124));
            meta.setText(f.meta.isEmpty() ? "PDF" : f.meta);
        }
        refreshState();
        setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { FileActions.open(c, f); } });
        fav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean on = FileActions.toggleFav(c, f);
                refreshState();
                if (on) Ui.pop(fav);
                if (onChanged != null) onChanged.run();
            }
        });
        dl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                FileActions.toggleDownload(c, f, new Runnable() {
                    @Override public void run() { if (item == f) refreshState(); if (onChanged != null) onChanged.run(); }
                });
            }
        });
    }

    void refreshState() {
        Store store = Store.get(getContext());
        fav.setColorFilter(store.isFav(item) ? Ui.accent() : Ui.MUTED, android.graphics.PorterDuff.Mode.SRC_IN);
        boolean running = FileActions.isBusy(item);
        busy.setVisibility(running ? VISIBLE : GONE);
        dl.setVisibility(running ? INVISIBLE : VISIBLE);
        boolean have = store.isDownloaded(item);
        dl.setImageResource(have ? R.drawable.ic_check : R.drawable.ic_download);
        dl.setColorFilter(have ? Ui.accent() : Ui.MUTED, android.graphics.PorterDuff.Mode.SRC_IN);
    }
}
