package net.tenthgradeps.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends Activity {
    private WebView webView;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        webView.setBackgroundColor(0xFFF8FAFC);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setBlockNetworkLoads(true); // التطبيق لا يجري اتصالات إنترنت
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new OfflineFilesBridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class OfflineFilesBridge {
        @JavascriptInterface public void openPdf(String filename, String base64) {
            try {
                File dir = new File(getCacheDir(), "offline-pdfs");
                if (!dir.exists() && !dir.mkdirs()) throw new Exception("تعذر إنشاء مجلد الملفات");
                File pdf = new File(dir, safeName(filename));
                try (FileOutputStream out = new FileOutputStream(pdf)) {
                    out.write(android.util.Base64.decode(base64, android.util.Base64.DEFAULT));
                }
                Uri uri = FileProvider.getUriForFile(MainActivity.this,
                        getPackageName() + ".files", pdf);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/pdf");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                runOnUiThread(() -> {
                    try { startActivity(Intent.createChooser(intent, "فتح ملف PDF")); }
                    catch (Exception e) { Toast.makeText(MainActivity.this,
                            "لا يوجد تطبيق لفتح PDF. ثبّت قارئ PDF ثم حاول مجددًا.", Toast.LENGTH_LONG).show(); }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر فتح الملف: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface public void savePdf(String filename, String base64) {
            try {
                byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                String name = safeName(filename);
                if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name += ".pdf";
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    values.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                    values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/10thgradeps");
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (uri == null) throw new Exception("تعذر إنشاء الملف في التنزيلات");
                    try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                        if (out == null) throw new Exception("تعذر كتابة الملف");
                        out.write(bytes);
                    }
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "تم حفظ الملف في التنزيلات/10thgradeps", Toast.LENGTH_LONG).show());
                } else {
                    throw new Exception("يتطلب الحفظ إصدار أندرويد 10 أو أحدث");
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "تعذر حفظ الملف: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }
    }

    private String safeName(String name) {
        if (name == null || name.trim().isEmpty()) return "10thgradeps-file.pdf";
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        return cleaned.length() > 120 ? cleaned.substring(0, 120) : cleaned;
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.removeJavascriptInterface("AndroidBridge"); webView.destroy(); }
        super.onDestroy();
    }
}
