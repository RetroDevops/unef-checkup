package com.retrodad.cellshield;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

/**
 * Helpers that hand the user off to Android's own screens. Every call has a
 * fallback chain so a button never silently does nothing on an unusual device.
 */
final class Links {
    private Links() {}

    static void openPrivateDns(Context c) {
        if (start(c, new Intent("android.settings.PRIVATE_DNS_SETTINGS"))) return;
        if (start(c, new Intent(Settings.ACTION_WIRELESS_SETTINGS))) return;
        start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    static void openInternalStorage(Context c) {
        if (start(c, new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))) return;
        start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    static void openBackup(Context c) {
        if (start(c, new Intent(Settings.ACTION_SYNC_SETTINGS))) return;
        start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    static void openPlayProtect(Context c) {
        Intent gms = new Intent().setComponent(new ComponentName(
                "com.google.android.gms",
                "com.google.android.gms.security.settings.VerifyAppsSettingsActivity"));
        if (start(c, gms)) return;
        if (start(c, new Intent(Settings.ACTION_SECURITY_SETTINGS))) return;
        start(c, new Intent(Settings.ACTION_SETTINGS));
    }

    static void openAppInfo(Context c, String pkg) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", pkg, null));
        if (!start(c, i)) start(c, new Intent(Settings.ACTION_APPLICATION_SETTINGS));
    }

    static void requestUninstall(Context c, String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", pkg, null));
        if (!start(c, i)) openAppInfo(c, pkg);
    }

    static void openDefaultBrowser(Context c) {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
        ResolveInfo ri = c.getPackageManager()
                .resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY);
        if (ri != null && ri.activityInfo != null
                && ri.activityInfo.packageName != null
                && !ri.activityInfo.packageName.equals("android")) {
            openAppInfo(c, ri.activityInfo.packageName);
        } else if (!start(c, new Intent(Settings.ACTION_APPLICATION_SETTINGS))) {
            start(c, new Intent(Settings.ACTION_SETTINGS));
        }
    }

    static void shareText(Context c, String subject, String body) {
        Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, subject);
        send.putExtra(Intent.EXTRA_TEXT, body);
        start(c, Intent.createChooser(send, subject));
    }

    private static boolean start(Context c, Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
