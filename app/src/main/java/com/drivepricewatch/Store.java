package com.drivepricewatch;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Saved prices and history, kept in the app's private storage (works offline). */
final class Store {
    private static final Object LOCK = new Object();

    static JSONObject load(Context ctx) {
        synchronized (LOCK) {
            try {
                File f = file(ctx);
                String text;
                if (f.exists()) {
                    text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                } else {
                    text = readAsset(ctx, "seed.json");
                }
                return new JSONObject(text);
            } catch (IOException | JSONException e) {
                try {
                    return new JSONObject(readAsset(ctx, "seed.json"));
                } catch (IOException | JSONException e2) {
                    throw new RuntimeException(e2);
                }
            }
        }
    }

    static void save(Context ctx, JSONObject data) throws IOException {
        synchronized (LOCK) {
            File tmp = new File(ctx.getFilesDir(), "data.json.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(data.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(file(ctx))) throw new IOException("could not save data");
        }
    }

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "data.json");
    }

    static String readAsset(Context ctx, String name) throws IOException {
        try (InputStream in = ctx.getAssets().open(name)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toString("UTF-8");
        }
    }

    /** Put (date, id, price) into history, replacing a same-day value. */
    static void upsertHistory(JSONObject data, String date, String id, double price) throws JSONException {
        JSONArray h = data.getJSONArray("history");
        for (int i = 0; i < h.length(); i++) {
            JSONObject p = h.getJSONObject(i);
            if (date.equals(p.getString("date")) && id.equals(p.getString("id"))) {
                p.put("price", price);
                return;
            }
        }
        JSONObject p = new JSONObject();
        p.put("date", date);
        p.put("id", id);
        p.put("price", price);
        h.put(p);
    }
}
