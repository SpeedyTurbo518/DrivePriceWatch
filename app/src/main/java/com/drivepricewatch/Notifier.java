package com.drivepricewatch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class Notifier {
    private static final String CHANNEL = "price_changes";

    static void ensureChannel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Price changes", NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Alerts when a tracked drive's price goes up or down");
        nm.createNotificationChannel(ch);
    }

    static void show(Context ctx, String title, String text) {
        ensureChannel(ctx);
        Intent open = new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, open, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        ctx.getSystemService(NotificationManager.class).notify((int) (System.currentTimeMillis() % 100000), n);
    }

    static void report(Context ctx, Checker.Outcome o, boolean quietIfNothing) {
        report(ctx, o, quietIfNothing, true);
    }

    /** notifyPartialFailures=false: background runs stay quiet when only some stores blocked the check. */
    static void report(Context ctx, Checker.Outcome o, boolean quietIfNothing, boolean notifyPartialFailures) {
        if (!o.changes.isEmpty()) {
            String body = String.join("\n", o.changes);
            if (!o.failed.isEmpty()) body += "\n(" + o.failed.size() + " listing(s) couldn't be checked)";
            show(ctx, "Drive price change (" + o.changes.size() + ")", body);
        } else if (!o.failed.isEmpty() && (notifyPartialFailures || o.failed.size() >= o.total)) {
            show(ctx, "Drive Price Watch", "No price changes. " + o.failed.size() + " listing(s) couldn't be checked — open the app and tap Check prices now.");
        } else if (!quietIfNothing) {
            show(ctx, "Drive Price Watch", "No price changes.");
        }
    }
}
