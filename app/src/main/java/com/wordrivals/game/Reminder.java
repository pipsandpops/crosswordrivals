package com.wordrivals.game;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

/**
 * Evening Daily Puzzle reminder, done entirely on the phone: an inexact daily alarm wakes this receiver,
 * which posts a local notification (skipped if today's puzzle is already done). Re-armed after a restart.
 */
public class Reminder extends BroadcastReceiver {
    static final String CHANNEL = "daily_puzzle";
    static final String PREFS = "wordrivals";
    static final int HOUR = 19;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || "android.intent.action.MY_PACKAGE_REPLACED".equals(action)) {
            schedule(context, enabled(context));
            return;
        }
        if (!enabled(context) || doneToday(context)) return;
        notify(context);
    }

    static boolean enabled(Context c) {
        return "1".equals(c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("reminder", "0"));
    }

    private static boolean doneToday(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        int today = (int) Math.floorDiv(now + java.util.TimeZone.getDefault().getOffset(now), 86400000L);
        return String.valueOf(today).equals(p.getString("dailyDay", ""));
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, Reminder.class).setAction("com.wordrivals.game.REMIND");
        return PendingIntent.getBroadcast(c, 7, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c, boolean on) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = pending(c);
        am.cancel(pi);
        if (!on) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, HOUR);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
    }

    private static void notify(Context c) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Daily Puzzle", NotificationManager.IMPORTANCE_DEFAULT));
        }
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 8, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(c, CHANNEL) : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_notify)
                .setContentTitle("Today's Daily Puzzle is ready 🧩")
                .setContentText("Beat your rivals and keep your 🔥 streak going!")
                .setContentIntent(pi).setAutoCancel(true);
        try { nm.notify(9, b.build()); } catch (SecurityException e) { /* notifications not allowed */ }
    }
}
