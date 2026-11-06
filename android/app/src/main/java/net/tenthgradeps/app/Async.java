package net.tenthgradeps.app;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny helper to run blocking work off the main thread and deliver the result on it. */
final class Async {
    private Async() {}

    static final ExecutorService IO = Executors.newFixedThreadPool(4);
    static final Handler MAIN = new Handler(Looper.getMainLooper());

    interface Work<T> { T run() throws Exception; }
    interface Done<T> { void done(T result, Exception error); }

    static <T> void run(final Work<T> work, final Done<T> done) {
        IO.execute(new Runnable() {
            @Override public void run() {
                T r = null;
                Exception e = null;
                try { r = work.run(); } catch (Exception ex) { e = ex; }
                final T fr = r;
                final Exception fe = e;
                MAIN.post(new Runnable() {
                    @Override public void run() { done.done(fr, fe); }
                });
            }
        });
    }
}
