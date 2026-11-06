package net.tenthgradeps.app;

import android.app.Application;

import java.io.File;

public class App extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Session.init(this);
        Ui.applyTheme(this);
        Ui.initFonts(this);
        // Temporary decrypted copies used by the viewer never survive an app start.
        File view = new File(getCacheDir(), "view");
        File[] old = view.listFiles();
        if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
            f.delete();
    }
}
