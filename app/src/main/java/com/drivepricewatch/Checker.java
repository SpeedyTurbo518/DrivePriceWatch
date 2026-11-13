package com.drivepricewatch;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads prices from store pages and applies them to the saved data. */
final class Checker {
    static final String END_DATE = "2026-11-25";
    static final double CAP = 200.0;
    private static final String UA = "Mozilla/5.0 (Linux; Android 14; SM-S918U) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36";

    static final class Result {
        final double price;
        final boolean inStock;
        final Double stars;
        final int ratings;

        Result(double price, boolean inStock, Double stars, int ratings) {
            this.price = price;
            this.inStock = inStock;
            this.stars = stars;
            this.ratings = ratings;
        }
    }

    static final class Outcome {
        final List<String> changes = new ArrayList<>();
        final List<String> failed = new ArrayList<>();
        int total;
    }

    // ---------- fetching ----------

    static Result fetchHttp(String store, String url) throws Exception {
        if ("ServerPartDeals".equals(store)) {
            return parseShopify(get(url.replaceAll("/+$", "") + ".js"));
        }
        return parse(store, get(url));
    }

    static String get(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(40000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        int code = c.getResponseCode();
        if (code != 200) {
            c.disconnect();
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0 && bo.size() < (8 << 20)) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    // ---------- parsing ----------

    private static final Pattern LD = Pattern.compile("<script[^>]*application/ld\\+json[^>]*>(.*?)</script>", Pattern.DOTALL);
    private static final Pattern EBAY_ENDED = Pattern.compile("(?i)This listing (was|has) ended|no longer available");
    private static final Pattern NEWEGG = Pattern.compile("\"Item\":\"(\\w+)\",\"Qty\":\\d+,\"UnitCost\":[\\d.]+,\"InstantRebateAmount\":[\\d.]+,\"ExchangeRate\":[\\d.]+,\"FinalPrice\":([\\d.]+),\"Instock\":(true|false)");

    static Result parse(String store, String body) throws Exception {
        if ("Newegg".equals(store)) return parseNewegg(body);
        if ("ServerPartDeals".equals(store) && body.trim().startsWith("{")) return parseShopify(body);
        try {
            return parseJsonLd(body);
        } catch (Exception e) {
            if ("eBay".equals(store) && EBAY_ENDED.matcher(body).find()) throw new Exception("listing ended");
            throw e;
        }
    }

    static Result parseShopify(String body) throws Exception {
        JSONObject j = new JSONObject(body);
        double cents = j.getDouble("price");
        if (cents <= 0) throw new Exception("no price");
        return new Result(cents / 100.0, j.optBoolean("available", true), null, 0);
    }

    static Result parseJsonLd(String body) throws Exception {
        Matcher m = LD.matcher(body);
        while (m.find()) {
            Object v;
            try {
                v = new JSONTokener(m.group(1).trim()).nextValue();
            } catch (JSONException e) {
                continue;
            }
            for (JSONObject node : flatten(v)) {
                if (!"Product".equals(node.optString("@type"))) continue;
                Result r = fromProduct(node);
                if (r != null) return r;
            }
        }
        throw new Exception("no product price on page");
    }

    private static Result fromProduct(JSONObject node) {
        for (JSONObject o : flatten(node.opt("offers"))) {
            double p = num(o.opt("price"));
            if (Double.isNaN(p)) p = num(o.opt("lowPrice"));
            if (Double.isNaN(p) || p <= 0) continue;
            String av = o.optString("availability", "");
            boolean in = av.isEmpty() || av.contains("InStock") || av.contains("LimitedAvailability") || av.contains("OnlineOnly");
            Double stars = null;
            int count = 0;
            JSONObject ar = node.optJSONObject("aggregateRating");
            if (ar != null) {
                double s = num(ar.opt("ratingValue"));
                if (!Double.isNaN(s)) stars = Math.round(s * 10) / 10.0;
                double c = num(ar.opt("ratingCount"));
                if (Double.isNaN(c)) c = num(ar.opt("reviewCount"));
                if (!Double.isNaN(c)) count = (int) c;
            }
            return new Result(p, in, stars, count);
        }
        return null;
    }

    private static List<JSONObject> flatten(Object v) {
        List<JSONObject> out = new ArrayList<>();
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            for (int i = 0; i < a.length(); i++) out.addAll(flatten(a.opt(i)));
        } else if (v instanceof JSONObject) {
            JSONObject o = (JSONObject) v;
            out.add(o);
            if (o.has("@graph")) out.addAll(flatten(o.opt("@graph")));
        }
        return out;
    }

    private static double num(Object v) {
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            try {
                return Double.parseDouble(((String) v).replace(",", "").trim());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    static Result parseNewegg(String body) throws Exception {
        Map<String, Integer> count = new HashMap<>();
        Map<String, Result> first = new HashMap<>();
        String best = null;
        Matcher m = NEWEGG.matcher(body);
        while (m.find()) {
            String item = m.group(1);
            if (!first.containsKey(item)) {
                first.put(item, new Result(Double.parseDouble(m.group(2)), "true".equals(m.group(3)), null, 0));
            }
            int n = count.containsKey(item) ? count.get(item) + 1 : 1;
            count.put(item, n);
            if (best == null || n > count.get(best)) best = item;
        }
        if (best == null) return parseJsonLd(body);
        return first.get(best);
    }

    // ---------- applying results ----------

    /** Background check: plain HTTP for every listing. */
    static Outcome checkAllHttp(Context ctx) throws Exception {
        JSONObject data = Store.load(ctx);
        JSONArray ls = data.getJSONArray("listings");
        Map<String, Result> results = new HashMap<>();
        for (int i = 0; i < ls.length(); i++) {
            JSONObject l = ls.getJSONObject(i);
            try {
                results.put(l.getString("id"), fetchHttp(l.getString("store"), l.getString("url")));
            } catch (Exception e) {
                // left out of results = failed
            }
        }
        return apply(ctx, results);
    }

    static Outcome apply(Context ctx, Map<String, Result> results) throws Exception {
        JSONObject data = Store.load(ctx);
        JSONArray ls = data.getJSONArray("listings");
        Outcome out = new Outcome();
        out.total = ls.length();
        Date now = new Date();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now);
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));
        String nowIso = iso.format(now);

        for (int i = 0; i < ls.length(); i++) {
            JSONObject l = ls.getJSONObject(i);
            String id = l.getString("id");
            String label = l.getString("store") + " · " + l.getString("drive");
            Result r = results.get(id);
            if (r == null) {
                l.put("status", "Check failed");
                out.failed.add(label);
                continue;
            }
            double p = Math.round(r.price * 100) / 100.0;
            Object oldObj = l.opt("current");
            double old = oldObj instanceof Number ? ((Number) oldObj).doubleValue() : Double.NaN;
            l.put("previous", Double.isNaN(old) ? JSONObject.NULL : old);
            l.put("current", p);
            l.put("status", r.inStock ? "Active" : "Ended");
            if (r.stars != null) {
                l.put("stars", r.stars);
                l.put("ratings", r.ratings);
            }
            l.put("checked", nowIso);
            Store.upsertHistory(data, today, id, p);
            if (!Double.isNaN(old) && Math.abs(old - p) >= 0.005) {
                double diff = p - old;
                out.changes.add(String.format(Locale.US, "%s %s: %s → %s (%s, %+.1f%%)",
                        label, l.getString("model"), money(old), money(p),
                        (diff > 0 ? "+" : "−") + money(Math.abs(diff)), diff / old * 100));
                if (old <= CAP && p > CAP) out.changes.add(label + " is now over $200");
            }
            if (!r.inStock) out.changes.add(label + " is out of stock / ended");
        }
        data.put("lastCheck", nowIso);
        data.put("lastChanges", new JSONArray(out.changes));
        Store.save(ctx, data);
        return out;
    }

    static String money(double v) {
        return String.format(Locale.US, "$%.2f", v);
    }

    static boolean pastEnd() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        return today.compareTo(END_DATE) > 0;
    }
}
