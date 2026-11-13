package com.drivepricewatch;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean checking = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/app.html");

        Notifier.ensureChannel(this);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        Scheduler.scheduleDaily(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.refresh && refresh()", null);
    }

    private void js(String code) {
        main.post(() -> web.evaluateJavascript(code, null));
    }

    private static String q(String s) {
        return JSONObject.quote(s);
    }

    /** Check now: fast download for every store, then an invisible browser for any that got blocked. */
    private void runCheck() {
        if (checking) return;
        checking = true;
        new Thread(() -> {
            final Map<String, Checker.Result> results = new HashMap<>();
            final List<JSONObject> retry = new ArrayList<>();
            try {
                JSONArray ls = Store.load(this).getJSONArray("listings");
                for (int i = 0; i < ls.length(); i++) {
                    JSONObject l = ls.getJSONObject(i);
                    js("onCheckProgress(" + q("Checking " + (i + 1) + " of " + ls.length() + ": " + l.getString("store")) + ")");
                    try {
                        results.put(l.getString("id"), Checker.fetchHttp(l.getString("store"), l.getString("url")));
                    } catch (Exception e) {
                        retry.add(l);
                    }
                }
            } catch (Exception e) {
                finishCheck(results);
                return;
            }
            main.post(() -> retryInBrowser(retry, 0, results, null));
        }).start();
    }

    private void retryInBrowser(List<JSONObject> retry, int idx, Map<String, Checker.Result> results, WebViewFetcher f0) {
        if (idx >= retry.size()) {
            if (f0 != null) f0.destroy();
            new Thread(() -> finishCheck(results)).start();
            return;
        }
        final WebViewFetcher f = f0 != null ? f0 : new WebViewFetcher(this);
        final JSONObject l = retry.get(idx);
        final String store = l.optString("store");
        js("onCheckProgress(" + q("Retrying " + (idx + 1) + " of " + retry.size() + " in browser: " + store) + ")");
        f.fetch(l.optString("url"), (html, err) -> {
            if (html != null) {
                try {
                    results.put(l.optString("id"), Checker.parse(store, html));
                } catch (Exception ignored) {
                }
            }
            retryInBrowser(retry, idx + 1, results, f);
        });
    }

    private void finishCheck(Map<String, Checker.Result> results) {
        try {
            Checker.Outcome o = Checker.apply(this, results);
            Notifier.report(this, o, true);
        } catch (Exception e) {
            js("onCheckProgress(" + q("Check failed: " + e.getMessage()) + ")");
        }
        checking = false;
        js("onCheckDone()");
    }

    final class Bridge {
        @JavascriptInterface
        public String getData() {
            return Store.load(MainActivity.this).toString();
        }

        @JavascriptInterface
        public void checkNow() {
            main.post(MainActivity.this::runCheck);
        }

        @JavascriptInterface
        public void openUrl(String url) {
            if (url != null && url.startsWith("https://")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        }
    }
}
