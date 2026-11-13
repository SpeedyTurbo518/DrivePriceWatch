package com.drivepricewatch;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class CheckJobService extends JobService {
    private volatile Thread worker;

    @Override
    public boolean onStartJob(JobParameters params) {
        worker = new Thread(() -> {
            try {
                Checker.Outcome o = Checker.checkAllHttp(getApplicationContext());
                Notifier.report(getApplicationContext(), o, true, false);
            } catch (Exception e) {
                Notifier.show(getApplicationContext(), "Drive Price Watch", "Daily check failed: " + e.getMessage());
            }
            jobFinished(params, false);
        });
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread t = worker;
        if (t != null) t.interrupt();
        return true; // retry later
    }
}
