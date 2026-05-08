package com.dataflow.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import android.util.Base64;

public class MainActivity extends Activity {

    private WebView webView;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setDatabasePath(getApplicationContext().getDir("database", MODE_PRIVATE).getPath());

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AppBridge(), "AppBridge");

        // Handle file downloads (Excel export)
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition,
                                        String mimetype, long contentLength) {
                if (url.startsWith("data:")) {
                    // Handle base64 data URI
                    saveDataUri(url);
                } else {
                    // Handle regular URL download
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setMimeType(mimetype);
                    request.addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url));
                    request.setDescription("下载文件...");
                    request.setTitle(getFileName(contentDisposition, url));
                    request.allowScanningByMediaScanner();
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, getFileName(contentDisposition, url));
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(request);
                    Toast.makeText(MainActivity.this, "开始下载...", Toast.LENGTH_SHORT).show();
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void saveDataUri(String dataUri) {
        try {
            // Parse data URI: data:mimetype;base64,data
            String[] parts = dataUri.split(",");
            String meta = parts[0];
            String base64Data = parts[1];
            String extension = ".xlsx";

            if (meta.contains("csv")) extension = ".csv";

            byte[] data = Base64.decode(base64Data, Base64.DEFAULT);

            // Save to Downloads folder
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) downloadsDir.mkdirs();

            String fileName = "DataFlow_" + System.currentTimeMillis() + extension;
            File file = new File(downloadsDir, fileName);

            OutputStream os = new FileOutputStream(file);
            os.write(data);
            os.close();

            // Notify media scanner
            android.media.MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);

            Toast.makeText(this, "已保存到: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // JavaScript bridge for native features
    class AppBridge {
        @JavascriptInterface
        public void shareText(String text) {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, text);
            intent.putExtra(Intent.EXTRA_SUBJECT, "DataFlow 数据");
            startActivity(Intent.createChooser(intent, "分享到"));
        }

        @JavascriptInterface
        public void shareFile(String base64Data, String filename) {
            try {
                byte[] data = Base64.decode(base64Data, Base64.DEFAULT);
                File file = new File(getCacheDir(), filename);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(data);
                fos.close();

                Uri uri = Uri.fromFile(file);
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(intent, "分享 Excel 文件"));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }
    }

    private String getFileName(String contentDisposition, String url) {
        if (contentDisposition != null && contentDisposition.contains("filename=")) {
            int start = contentDisposition.indexOf("filename=") + 9;
            int end = contentDisposition.indexOf(";", start);
            if (end < 0) end = contentDisposition.length();
            return contentDisposition.substring(start, end).replace("\"", "").trim();
        }
        return "DataFlow_" + System.currentTimeMillis() + ".xlsx";
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.evaluateJavascript(
            "(function(){try{localStorage.setItem('dataflow_data', JSON.stringify(appData));}catch(e){}})();",
            null
        );
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
