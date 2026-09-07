package com.retrodad.cellshield;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Read-only checks. Nothing here changes device state or touches the network:
 * it reads the current Private DNS setting and looks at the permissions each
 * installed app has asked for.
 */
final class PhoneCheck {
    private PhoneCheck() {}

    static final class RiskyApp {
        final String label;
        final String pkg;
        final int score;
        final List<String> reasons;

        RiskyApp(String label, String pkg, int score, List<String> reasons) {
            this.label = label;
            this.pkg = pkg;
            this.score = score;
            this.reasons = reasons;
        }

        boolean isHigh() {
            return score >= 5;
        }
    }

    static final class Result {
        boolean dnsKnown;
        boolean dnsOn;
        String dnsName;
        int scannedApps;
        final List<RiskyApp> riskyApps = new ArrayList<>();
    }

    static Result run(Context c) {
        Result r = new Result();
        readPrivateDns(c, r);
        scanApps(c, r);
        return r;
    }

    private static void readPrivateDns(Context c, Result r) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return; // getPrivateDnsServerName() needs Android 9 (API 28)
        }
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            Network active = cm.getActiveNetwork();
            if (active == null) return;
            LinkProperties lp = cm.getLinkProperties(active);
            if (lp == null) return;
            r.dnsKnown = true;
            r.dnsOn = lp.isPrivateDnsActive();
            r.dnsName = lp.getPrivateDnsServerName();
        } catch (Exception ignored) {
            // Leave dnsKnown = false so the UI shows "unknown".
        }
    }

    private static void scanApps(Context c, Result r) {
        PackageManager pm = c.getPackageManager();
        List<PackageInfo> packages;
        try {
            packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        } catch (Exception e) {
            packages = new ArrayList<>();
        }

        for (PackageInfo info : packages) {
            ApplicationInfo ai = info.applicationInfo;
            if (ai == null) continue;
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (c.getPackageName().equals(info.packageName)) continue;
            r.scannedApps++;

            String[] perms = info.requestedPermissions;
            if (perms == null) continue;

            int score = 0;
            LinkedHashSet<String> reasons = new LinkedHashSet<>();
            for (String p : perms) {
                if (p == null) continue;
                String reason = null;
                int pts = 0;
                if (p.endsWith("SYSTEM_ALERT_WINDOW")) {
                    pts = 3; reason = "can draw over other apps";
                } else if (p.endsWith("BIND_ACCESSIBILITY_SERVICE")) {
                    pts = 4; reason = "can control the screen (accessibility)";
                } else if (p.endsWith("BIND_NOTIFICATION_LISTENER_SERVICE")) {
                    pts = 3; reason = "can read all notifications";
                } else if (p.endsWith("READ_SMS") || p.endsWith("RECEIVE_SMS")) {
                    pts = 3; reason = "can read text messages";
                } else if (p.endsWith("REQUEST_INSTALL_PACKAGES")) {
                    pts = 2; reason = "can install other apps";
                } else if (p.endsWith("BIND_DEVICE_ADMIN")) {
                    pts = 3; reason = "can act as a device admin";
                } else if (p.endsWith("PACKAGE_USAGE_STATS")) {
                    pts = 2; reason = "can see which apps you open";
                }
                if (reason != null && reasons.add(reason)) {
                    score += pts;
                }
            }
            if (score <= 0) continue;

            r.riskyApps.add(new RiskyApp(
                    String.valueOf(ai.loadLabel(pm)),
                    info.packageName,
                    score,
                    new ArrayList<>(reasons)));
        }

        Collections.sort(r.riskyApps, new Comparator<RiskyApp>() {
            @Override
            public int compare(RiskyApp a, RiskyApp b) {
                if (a.score != b.score) return b.score - a.score;
                return a.label.compareToIgnoreCase(b.label);
            }
        });
    }
}
