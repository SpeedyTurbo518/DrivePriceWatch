package com.drivepricewatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DailyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Checker.pastEnd()) {
            Scheduler.scheduleDaily(ctx); // cancels the alarm
            Notifier.show(ctx, "Drive Price Watch", "The price watch period ended (Nov 25). Daily checks stopped.");
            return;
        }
        Scheduler.runCheckJob(ctx); // runs as soon as there's internet
    }
}
