package com.drivepricewatch;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONTokener;

/**
 * Loads a store page in an invisible browser (like opening it in Chrome) and returns the page HTML.
 * Used in the app when a plain download gets blocked. Must be created and used on the main thread.
 */
final class WebViewFetcher {
    interface Callback {
        void done(String html, String error);
    }

    private final WebView web;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Callback pending;
    private Runnable timeout;

    @SuppressLint("SetJavaScriptEnabled")
    WebViewFetcher(Context ctx) {
        web = new WebView(ctx);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setBlockNetworkImage(true);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // give scripts a moment, then read the page
                main.postDelayed(() -> grab(), 3000);
            }
        });
    }

    void fetch(String url, Callback cb) {
        pending = cb;
        timeout = () -> finish(null, "timed out");
        main.postDelayed(timeout, 45000);
        web.loadUrl(url);
    }

    private void grab() {
        if (pending == null) return;
        web.evaluateJavascript("document.documentElement.outerHTML", value -> {
            try {
                Object s = new JSONTokener(value).nextValue();
                finish(s instanceof String ? (String) s : null, s instanceof String ? null : "no page");
            } catch (Exception e) {
                finish(null, e.getMessage());
            }
        });
    }

    private void finish(String html, String err) {
        Callback cb = pending;
        pending = null;
        if (timeout != null) main.removeCallbacks(timeout);
        if (cb != null) cb.done(html, err);
    }

    void destroy() {
        web.stopLoading();
        web.destroy();
    }
}
