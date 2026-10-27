package com.anderza.fmnet;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    private static final int FILE_REQUEST = 7;
    private static final String START_URL = "file:///android_asset/index.html";

    static WeakReference<MainActivity> current = new WeakReference<>(null);

    private WebView web;
    private ValueCallback<Uri[]> fileCallback;
    private boolean tv;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);
        tv = detectTv();

        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#07344B"));
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        // Страница лежит внутри приложения: разрешаем ей читать каталоги станций с любых серверов
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        // Масштаб 1:1 по ширине экрана: без автоподгонки и зума, иначе страница «раздувается» при повороте
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setTextZoom(100);
        s.setUserAgentString(s.getUserAgentString() + " RadioNetApp/1" + (tv ? " AndroidTV" : ""));

        web.addJavascriptInterface(new Bridge(), "Android");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openExternal(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternal(Uri.parse(url));
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Выберите файл"), FILE_REQUEST);
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START_URL);

        goImmersive();
        askNotificationPermission();
        handleIntent(getIntent());
    }

    private boolean detectTv() {
        UiModeManager ui = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (ui != null && ui.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) return true;
        PackageManager pm = getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                || pm.hasSystemFeature("android.software.leanback_only");
    }

    @SuppressWarnings("deprecation")
    private void goImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    private boolean openExternal(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("file") || scheme.equals("about") || scheme.equals("data") || scheme.equals("blob")) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException ignored) {
        }
        return true;
    }

    /** Отправляет команду плееру на странице: toggle, play, pause, next, prev, stop. */
    void sendMedia(String action) {
        if (web == null) return;
        final String js = "window.rnMedia && window.rnMedia('" + action + "')";
        web.post(new Runnable() {
            @Override
            public void run() {
                web.evaluateJavascript(js, null);
            }
        });
    }

    static void sendMediaToCurrent(String action) {
        MainActivity a = current.get();
        if (a != null) a.sendMedia(action);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        if (PlaybackService.ACTION_TOGGLE.equals(intent.getAction())) sendMedia("toggle");
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        String action = null;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                action = "toggle"; break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                action = "play"; break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
                action = "pause"; break;
            case KeyEvent.KEYCODE_MEDIA_STOP:
                action = "stop"; break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
            case KeyEvent.KEYCODE_CHANNEL_UP:
                action = "next"; break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_REWIND:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
                action = "prev"; break;
            default:
                break;
        }
        if (action != null) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) sendMedia(action);
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU && event.getAction() == KeyEvent.ACTION_UP) {
            web.evaluateJavascript("window.rnMenu && window.rnMenu()", null);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript("(window.rnBack && window.rnBack()) ? '1' : '0'", new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null || !value.contains("1")) {
                    // Радио продолжает играть в фоне
                    moveTaskToBack(true);
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQUEST) {
            if (fileCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
                fileCallback.onReceiveValue(result);
                fileCallback = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        goImmersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goImmersive();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (web != null) web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, PlaybackService.class));
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    /** Методы, доступные странице как window.Android */
    private class Bridge {

        @JavascriptInterface
        public boolean isTv() {
            return tv;
        }

        @JavascriptInterface
        public boolean isApp() {
            return true;
        }

        @JavascriptInterface
        public void setPlaying(final boolean playing, final String title, final String text) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Intent i = new Intent(MainActivity.this, PlaybackService.class);
                    if (playing) {
                        i.setAction(PlaybackService.ACTION_UPDATE);
                        i.putExtra("title", title);
                        i.putExtra("text", text);
                        try {
                            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i);
                            else startService(i);
                        } catch (Exception ignored) {
                        }
                    } else {
                        stopService(i);
                    }
                }
            });
        }

        @JavascriptInterface
        public void keepScreenOn(final boolean on) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            });
        }

        /** Сохраняет текст в папку «Загрузки». Возвращает путь или пустую строку. */
        @JavascriptInterface
        public String saveFile(String name, String text) {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentResolver cr = getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    if (uri == null) return "";
                    OutputStream out = cr.openOutputStream(uri);
                    if (out == null) return "";
                    out.write(bytes);
                    out.close();
                    return "Загрузки/" + name;
                }
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (dir == null || (!dir.exists() && !dir.mkdirs()) || !dir.canWrite()) {
                    dir = getExternalFilesDir(null);
                }
                if (dir == null) return "";
                File f = new File(dir, name);
                FileOutputStream out = new FileOutputStream(f);
                out.write(bytes);
                out.close();
                return f.getAbsolutePath();
            } catch (Exception e) {
                return "";
            }
        }

        /** Узнаёт текущий трек потока в фоне и вызывает window[callback](строка). */
        @JavascriptInterface
        public void icy(final String url, final String callback) {
            if (callback == null || !callback.matches("[A-Za-z0-9_$]+")) return;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String title = IcyReader.read(url);
                    final String js = "window['" + callback + "'] && window['" + callback + "']("
                            + org.json.JSONObject.quote(title == null ? "" : title) + ")";
                    final WebView w = web;
                    if (w != null) w.post(new Runnable() {
                        @Override
                        public void run() {
                            w.evaluateJavascript(js, null);
                        }
                    });
                }
            }, "icy").start();
        }

        @JavascriptInterface
        public void exitApp() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }
    }
}
