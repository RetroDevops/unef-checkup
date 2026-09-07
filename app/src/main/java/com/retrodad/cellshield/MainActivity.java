package com.retrodad.cellshield;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

/**
 * Single screen. The top button runs a quick read-only check; the rest of the
 * buttons open the matching Android settings screen and show a short tip.
 */
public class MainActivity extends Activity {

    private static final String PREFS = "cellshield";
    private static final String KEY_SEEN_WELCOME = "seen_welcome";
    private static final String KEY_LAST_SUMMARY = "last_summary";

    private TextView resultsText;
    private LinearLayout findingsContainer;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        ((TextView) findViewById(R.id.version_text)).setText("Version " + appVersion());
        resultsText = findViewById(R.id.results_text);
        findingsContainer = findViewById(R.id.findings_container);

        String last = prefs.getString(KEY_LAST_SUMMARY, null);
        if (!TextUtils.isEmpty(last)) {
            resultsText.setText(last);
        }

        findViewById(R.id.check_button).setOnClickListener(v -> runCheck());
        findViewById(R.id.apps_button).setOnClickListener(v -> runCheck());
        findViewById(R.id.dns_button).setOnClickListener(v -> {
            Links.openPrivateDns(this);
            tip(getString(R.string.dns_guidance));
        });
        findViewById(R.id.storage_button).setOnClickListener(v -> {
            Links.openInternalStorage(this);
            tip(getString(R.string.storage_guidance));
        });
        findViewById(R.id.browser_button).setOnClickListener(v -> {
            Links.openDefaultBrowser(this);
            tip(getString(R.string.browser_guidance));
        });
        findViewById(R.id.backup_button).setOnClickListener(v -> {
            Links.openBackup(this);
            tip(getString(R.string.backup_guidance));
        });
        findViewById(R.id.protect_button).setOnClickListener(v -> {
            Links.openPlayProtect(this);
            tip(getString(R.string.protect_guidance));
        });
        findViewById(R.id.capabilities_button).setOnClickListener(v -> dialog(
                getString(R.string.capabilities_title), getString(R.string.capabilities_body)));
        findViewById(R.id.share_button).setOnClickListener(v -> shareReport());

        if (!prefs.getBoolean(KEY_SEEN_WELCOME, false)) {
            dialog(getString(R.string.welcome_title), getString(R.string.welcome_body));
            prefs.edit().putBoolean(KEY_SEEN_WELCOME, true).apply();
        }
    }

    private void runCheck() {
        resultsText.setText(R.string.checking);
        findingsContainer.removeAllViews();
        // Let "Checking..." paint before the (brief) synchronous scan runs.
        resultsText.post(() -> {
            PhoneCheck.Result r = PhoneCheck.run(this);
            resultsText.setText(buildSummary(r));
            renderFindings(r);
            prefs.edit().putString(KEY_LAST_SUMMARY, resultsText.getText().toString()).apply();
        });
    }

    private String buildSummary(PhoneCheck.Result r) {
        StringBuilder b = new StringBuilder();
        b.append("Checked ")
         .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                 .format(new Date()))
         .append("\n\n");

        b.append("Pop-up blocking (Private DNS): ");
        if (!r.dnsKnown) {
            b.append("unknown (needs Android 9 or newer)");
        } else if (r.dnsOn) {
            b.append("ON");
            if (!TextUtils.isEmpty(r.dnsName)) {
                b.append(" (").append(r.dnsName).append(")");
            }
        } else {
            b.append("OFF - tap \"Block pop-ups and ads\" to turn it on");
        }
        b.append("\n\n");

        if (r.riskyApps.isEmpty()) {
            b.append(getString(R.string.no_findings));
        } else {
            int high = 0;
            for (PhoneCheck.RiskyApp a : r.riskyApps) {
                if (a.isHigh()) high++;
            }
            b.append(r.riskyApps.size())
             .append(r.riskyApps.size() == 1 ? " app uses " : " apps use ")
             .append("powerful permissions");
            if (high > 0) {
                b.append(" (").append(high).append(" worth a closer look)");
            }
            b.append(".\n").append(getString(R.string.findings_note));
        }

        b.append("\n\nScanned ").append(r.scannedApps)
         .append(" installed apps. Keep Google Play Protect on for malware scanning.");
        return b.toString();
    }

    private void renderFindings(PhoneCheck.Result r) {
        int shown = Math.min(r.riskyApps.size(), 10);
        for (int i = 0; i < shown; i++) {
            findingsContainer.addView(buildFindingRow(r.riskyApps.get(i)));
        }
        if (r.riskyApps.size() > shown) {
            TextView more = new TextView(this);
            more.setText("+ " + (r.riskyApps.size() - shown) + " more not shown");
            more.setTextColor(0xFF5F6368);
            more.setTextSize(14);
            more.setPadding(0, dp(12), 0, 0);
            findingsContainer.addView(more);
        }
    }

    private LinearLayout buildFindingRow(PhoneCheck.RiskyApp app) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.row_bg);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(10);
        row.setLayoutParams(rowParams);

        TextView name = new TextView(this);
        name.setText(app.label);
        name.setTextSize(17);
        name.setTextColor(0xFF202124);
        name.setTypeface(name.getTypeface(), Typeface.BOLD);
        row.addView(name);

        TextView why = new TextView(this);
        why.setText("This app " + TextUtils.join("; ", app.reasons) + ".");
        why.setTextSize(14);
        why.setTextColor(app.isHigh() ? 0xFFC5221F : 0xFFE37400);
        why.setPadding(0, dp(4), 0, dp(10));
        row.addView(why);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button info = compactButton(getString(R.string.open_app_info));
        info.setOnClickListener(v -> Links.openAppInfo(this, app.pkg));
        actions.addView(info);

        Button uninstall = compactButton(getString(R.string.uninstall));
        uninstall.setOnClickListener(v -> Links.requestUninstall(this, app.pkg));
        actions.addView(uninstall);

        row.addView(actions);
        return row;
    }

    private Button compactButton(String label) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextSize(14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void shareReport() {
        String body = resultsText.getText().toString();
        if (TextUtils.isEmpty(body) || getString(R.string.results_idle).equals(body)) {
            body = "I have not run a check in Unef Checkup yet.";
        }
        Links.shareText(this, "Unef Checkup report", body);
    }

    private void dialog(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private void tip(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private String appVersion() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
