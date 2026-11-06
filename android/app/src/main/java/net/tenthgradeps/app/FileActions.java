package net.tenthgradeps.app;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Actions shared by every list of files: open, favorite, download / remove download. */
final class FileActions {
    private FileActions() {}

    private static final Set<String> BUSY = Collections.synchronizedSet(new HashSet<String>());

    static boolean isBusy(FileItem f) { return BUSY.contains(f.key); }

    static void open(Context c, FileItem f) {
        if (f.premium) {
            if (!Session.loggedIn()) {
                Ui.toast(c, "سجّل الدخول بحساب الموقع أولًا.");
                c.startActivity(new Intent(c, LoginActivity.class));
                return;
            }
            if (!Session.premiumUsable() && !Store.get(c).isDownloaded(f)) {
                Ui.toast(c, "ملفات Premium تحتاج اشتراكًا مفعّلًا على حسابك.");
                c.startActivity(new Intent(c, SubscriptionActivity.class));
                return;
            }
        }
        Intent i = new Intent(c, PdfViewerActivity.class);
        i.putExtra("item", f);
        c.startActivity(i);
    }

    static boolean toggleFav(Context c, FileItem f) { return Store.get(c).toggleFav(f); }

    /** Downloads the file, or (after confirmation) removes the existing download. {@code changed} runs on the main thread. */
    static void toggleDownload(final Context c, final FileItem f, final Runnable changed) {
        final Store store = Store.get(c);
        if (store.isDownloaded(f)) {
            Ui.show(new AlertDialog.Builder(c).setMessage("حذف الملف من التنزيلات؟")
                    .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int w) { store.delete(f); changed.run(); }
                    }).setNegativeButton("إلغاء", null));
            return;
        }
        if (f.premium && !Session.premiumUsable()) {
            Ui.toast(c, "ملفات Premium تحتاج اشتراكًا مفعّلًا على حسابك.");
            return;
        }
        if (!BUSY.add(f.key)) return;
        Ui.toast(c, "جارٍ التنزيل…");
        changed.run();
        Async.run(new Async.Work<Boolean>() {
            @Override public Boolean run() throws Exception { Downloader.download(c, f); return Boolean.TRUE; }
        }, new Async.Done<Boolean>() {
            @Override public void done(Boolean r, Exception e) {
                BUSY.remove(f.key);
                if (e != null) Ui.toast(c, Net.friendly(e)); else Ui.toast(c, "تم التنزيل. يمكنك فتحه دون إنترنت.");
                changed.run();
            }
        });
    }
}
