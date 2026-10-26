package com.lzplastik.tripwallet;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.CancellationSignal;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Environment;
import android.print.PdfExporter;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/assets/www/index.html";
    private static final int FILE_REQ = 1;

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serveAsset(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                String[] types = params.getAcceptTypes();
                boolean image = types != null && types.length > 0 && types[0].startsWith("image");
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType(image ? "image/*" : "*/*");
                if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
                    pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                }
                try {
                    startActivityForResult(Intent.createChooser(pick, image ? "选择照片" : "选择文件"), FILE_REQ);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);
    }

    /** 用 https://appassets.androidplatform.net/assets/... 提供安装包里的网页文件，网页存储才能正常保存。 */
    private WebResourceResponse serveAsset(Uri u) {
        String path = u.getPath();
        if (!HOST.equals(u.getHost()) || path == null || !path.startsWith("/assets/")) return null;
        String name = path.substring("/assets/".length());
        try {
            InputStream in = getAssets().open(name);
            String mime = name.endsWith(".html") ? "text/html"
                    : name.endsWith(".png") ? "image/png"
                    : name.endsWith(".js") ? "application/javascript"
                    : name.endsWith(".webmanifest") ? "application/manifest+json"
                    : "application/octet-stream";
            return new WebResourceResponse(mime, "UTF-8", in);
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, null);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_REQ || fileCallback == null) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK && data != null) {
            ClipData clip = data.getClipData();
            if (clip != null) {
                result = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) result[i] = clip.getItemAt(i).getUri();
            } else if (data.getData() != null) {
                result = new Uri[]{data.getData()};
            }
        }
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    public void onBackPressed() {
        // 先让网页关掉弹层；没有弹层时才退出
        web.evaluateJavascript("window.__back?window.__back():false", v -> {
            if (!"true".equals(v)) finish();
        });
    }

    private static String safeName(String n) {
        String s = n == null ? "file" : n.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        return s.isEmpty() ? "file" : s;
    }

    private void shareFile(File f, String mime, String title) {
        Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/" + Uri.encode(f.getName()));
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.putExtra(Intent.EXTRA_SUBJECT, title);
        send.setClipData(ClipData.newRawUri(f.getName(), uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, title);
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(chooser);
        } catch (Exception e) {
            toastJs("分享失败");
        }
    }

    private void pdfFallback(String title) {
        toastJs("PDF 生成失败，已打开打印界面，请选「另存为 PDF」");
        PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (pm != null) pm.print(title, web.createPrintDocumentAdapter(title),
                new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
    }

    private void toastJs(String msg) {
        web.evaluateJavascript("window.__toast&&window.__toast(" + org.json.JSONObject.quote(msg) + ")", null);
    }

    private void bioReply(boolean ok) {
        web.evaluateJavascript("window.__bio&&window.__bio(" + ok + ")", null);
    }

    private class Bridge {
        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("出差钱袋", text));
        }

        /** 按键时轻微震动一下。 */
        @JavascriptInterface
        public void haptic() {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v == null || !v.hasVibrator()) return;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK));
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(12);
                }
            } catch (Exception ignored) {
            }
        }

        /** 生成 PDF 并打开系统分享菜单；失败时退回打印界面。 */
        @JavascriptInterface
        public void sharePdf(String title, String fileName) {
            runOnUiThread(() -> {
                File out = new File(ShareProvider.dir(MainActivity.this), safeName(fileName));
                try {
                    PdfExporter.export(web.createPrintDocumentAdapter(title), out, ok -> {
                        if (ok) shareFile(out, "application/pdf", title);
                        else pdfFallback(title);
                    });
                } catch (Throwable t) {
                    pdfFallback(title);
                }
            });
        }

        /** 把文本内容（备份 JSON、CSV）存成文件并分享。 */
        @JavascriptInterface
        public void shareText(String fileName, String mime, String text) {
            runOnUiThread(() -> {
                try {
                    File out = new File(ShareProvider.dir(MainActivity.this), safeName(fileName));
                    try (FileOutputStream os = new FileOutputStream(out)) {
                        os.write(text.getBytes(StandardCharsets.UTF_8));
                    }
                    shareFile(out, mime, fileName);
                } catch (Exception e) {
                    toastJs("分享失败");
                }
            });
        }

        /** 设备是否可以用指纹（Android 10 及以上）。 */
        @JavascriptInterface
        public boolean hasBiometric() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
            BiometricManager bm = getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        }

        /** 弹出系统指纹验证，结果回调网页里的 window.__bio(true/false)。 */
        @JavascriptInterface
        public void biometric(String title, String cancel) {
            runOnUiThread(() -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { bioReply(false); return; }
                BiometricPrompt prompt = new BiometricPrompt.Builder(MainActivity.this)
                        .setTitle(title)
                        .setNegativeButton(cancel, getMainExecutor(), (d, w) -> bioReply(false))
                        .build();
                prompt.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) { bioReply(true); }

                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) { bioReply(false); }
                });
            });
        }

        /** 打开系统打印（A4），可选打印机或「另存为 PDF」。 */
        @JavascriptInterface
        public void print(String title) {
            runOnUiThread(() -> {
                PrintManager pm = (PrintManager) getSystemService(Context.PRINT_SERVICE);
                if (pm == null) return;
                PrintAttributes attrs = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .build();
                pm.print(title, web.createPrintDocumentAdapter(title), attrs);
            });
        }

        /** 保存到「下载」文件夹，返回保存位置；失败返回空字符串。 */
        @JavascriptInterface
        public String save(String name, String mime, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return "";
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        if (os == null) return "";
                        os.write(bytes);
                    }
                    return "下载/" + name;
                }
                File dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null) return "";
                File f = new File(dir, name);
                try (FileOutputStream os = new FileOutputStream(f)) {
                    os.write(bytes);
                }
                return f.getAbsolutePath();
            } catch (Exception e) {
                return "";
            }
        }
    }
}
