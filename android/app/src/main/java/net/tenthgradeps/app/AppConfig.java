package net.tenthgradeps.app;

/** Central constants. Every endpoint used by the app is an existing Netlify Function of the site. */
final class AppConfig {
    private AppConfig() {}

    static final String SITE = "https://10thgradeps.netlify.app";
    static final String FN = "/.netlify/functions/";
    static final String UA = "10thgradeps-android/1.0.0";

    /** Premium PDFs may be saved for offline reading, encrypted inside the app's private storage. Set to false to disable. */
    static final boolean PREMIUM_OFFLINE_ENABLED = true;
    /** Offline Premium files stay readable this long after the last successful server check of the subscription. */
    static final long PREMIUM_OFFLINE_GRACE_MS = 7L * 24 * 60 * 60 * 1000;
    /** Minimum time between automatic account refreshes (each one also performs the server-side Premium check). */
    static final long ACCOUNT_REFRESH_MS = 10L * 60 * 1000;
}
