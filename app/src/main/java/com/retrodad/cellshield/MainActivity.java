package com.retrodad.cellshield;

import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.DateFormat;
import java.util.Date;

/**
 * Single screen. The primary button runs a quick read-only check; the "quick
 * fixes" rows explain a task then open the matching Android settings screen.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "cellshield";
    private static final String KEY_SEEN_WELCOME = "seen_welcome";
    private static final String KEY_LAST_TITLE = "last_title";
    private static final String KEY_LAST_DETAIL = "last_detail";
    private static final String KEY_LAST_SEV = "last_sev";

    private SharedPreferences prefs;
    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusDetail;
    private LinearLayout findingsContainer;
    private LinearLayout fixesContainer;
    private int lastSev;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusDetail = findViewById(R.id.status_detail);
        findingsContainer = findViewById(R.id.findings_container);
        fixesContainer = findViewById(R.id.fixes_container);

        populateFixes();

        findViewById(R.id.check_button).setOnClickListener(v -> runCheck());
        findViewById(R.id.capabilities_button).setOnClickListener(v -> dialog(
                getString(R.string.capabilities_title),
                getString(R.string.capabilities_body) + "\n\nVersion " + appVersion()));
        findViewById(R.id.share_button).setOnClickListener(v -> shareReport());

        restoreLast();

        if (!prefs.getBoolean(KEY_SEEN_WELCOME, false)) {
            dialog(getString(R.string.welcome_title), getString(R.string.welcome_body));
            prefs.edit().putBoolean(KEY_SEEN_WELCOME, true).apply();
        }
    }

    private void populateFixes() {
        LayoutInflater inf = getLayoutInflater();
        addFix(inf, R.drawable.ic_dns, R.string.row_dns_title, R.string.row_dns_sub,
                R.string.dns_guidance, () -> Links.openPrivateDns(this));
        addFix(inf, R.drawable.ic_storage, R.string.row_storage_title, R.string.row_storage_sub,
                R.string.storage_guidance, () -> Links.openInternalStorage(this));
        addFix(inf, R.drawable.ic_browser, R.string.row_browser_title, R.string.row_browser_sub,
                R.string.browser_guidance, () -> Links.openDefaultBrowser(this));
        addFix(inf, R.drawable.ic_backup, R.string.row_backup_title, R.string.row_backup_sub,
                R.string.backup_guidance, () -> Links.openBackup(this));
        addFix(inf, R.drawable.ic_protect, R.string.row_protect_title, R.string.row_protect_sub,
                R.string.protect_guidance, () -> Links.openPlayProtect(this));
    }

    private void addFix(LayoutInflater inf, int iconRes, int titleRes, int subRes,
                        int guidanceRes, Runnable open) {
        View row = inf.inflate(R.layout.item_fix_row, fixesContainer, false);
        ((ImageView) row.findViewById(R.id.row_icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.row_title)).setText(titleRes);
        ((TextView) row.findViewById(R.id.row_sub)).setText(subRes);
        row.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle(titleRes)
                .setMessage(guidanceRes)
                .setPositiveButton("Open settings", (d, w) -> open.run())
                .setNegativeButton("Not now", null)
                .show());
        fixesContainer.addView(row);
    }

    private void runCheck() {
        statusIcon.setImageResource(R.drawable.ic_scan);
        statusIcon.setColorFilter(getColor(R.color.text_secondary));
        statusTitle.setText(R.string.checking);
        statusDetail.setText("");
        findingsContainer.removeAllViews();
        statusTitle.post(() -> {
            PhoneCheck.Result r = PhoneCheck.run(this);
            renderResult(r);
            prefs.edit()
                    .putString(KEY_LAST_TITLE, statusTitle.getText().toString())
                    .putString(KEY_LAST_DETAIL, statusDetail.getText().toString())
                    .putInt(KEY_LAST_SEV, lastSev)
                    .apply();
        });
    }

    private void renderResult(PhoneCheck.Result r) {
        int n = r.riskyApps.size();
        boolean warn = n > 0;
        lastSev = warn ? 1 : 0;

        String dnsPart;
        if (!r.dnsKnown) {
            dnsPart = "Private DNS status needs Android 9 or newer";
        } else if (r.dnsOn) {
            dnsPart = "Private DNS on"
                    + (TextUtils.isEmpty(r.dnsName) ? "" : " (" + r.dnsName + ")");
        } else {
            dnsPart = "Private DNS off";
        }
        String when = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date());

        String detail;
        if (warn) {
            setStatus(R.drawable.ic_alert, R.color.warn,
                    n + (n == 1 ? " app to review" : " apps to review"));
            detail = "Checked " + when + ". " + dnsPart + ".\n\n" + getString(R.string.findings_note);
        } else {
            setStatus(R.drawable.ic_check_circle, R.color.ok, getString(R.string.status_all_clear));
            detail = "Checked " + when + ". " + dnsPart + ".\n\n" + getString(R.string.no_findings);
        }
        statusDetail.setText(detail);

        findingsContainer.removeAllViews();
        int shown = Math.min(n, 12);
        LayoutInflater inf = getLayoutInflater();
        for (int i = 0; i < shown; i++) {
            addFindingRow(inf, r.riskyApps.get(i));
        }
        if (n > shown) {
            TextView more = new TextView(this);
            more.setText("+ " + (n - shown) + " more not shown");
            more.setTextColor(getColor(R.color.text_secondary));
            more.setTextSize(13);
            more.setPadding(dp(16), dp(12), 0, dp(2));
            findingsContainer.addView(more);
        }
    }

    private void addFindingRow(LayoutInflater inf, PhoneCheck.RiskyApp app) {
        View row = inf.inflate(R.layout.item_finding, findingsContainer, false);
        row.findViewById(R.id.finding_dot).setBackgroundTintList(ColorStateList.valueOf(
                getColor(app.isHigh() ? R.color.danger : R.color.warn)));
        ((TextView) row.findViewById(R.id.finding_name)).setText(app.label);
        ((TextView) row.findViewById(R.id.finding_reason))
                .setText(sentence(TextUtils.join(", ", app.reasons)));
        MaterialButton info = row.findViewById(R.id.finding_info);
        MaterialButton remove = row.findViewById(R.id.finding_remove);
        info.setOnClickListener(v -> Links.openAppInfo(this, app.pkg));
        remove.setOnClickListener(v -> Links.requestUninstall(this, app.pkg));
        findingsContainer.addView(row);
    }

    private void setStatus(int iconRes, int colorRes, String title) {
        statusIcon.setImageResource(iconRes);
        statusIcon.setColorFilter(getColor(colorRes));
        statusTitle.setText(title);
    }

    private void restoreLast() {
        String title = prefs.getString(KEY_LAST_TITLE, null);
        if (title == null) {
            return;
        }
        if (prefs.getInt(KEY_LAST_SEV, 0) == 1) {
            statusIcon.setImageResource(R.drawable.ic_alert);
            statusIcon.setColorFilter(getColor(R.color.warn));
        } else {
            statusIcon.setImageResource(R.drawable.ic_check_circle);
            statusIcon.setColorFilter(getColor(R.color.ok));
        }
        statusTitle.setText(title);
        statusDetail.setText(prefs.getString(KEY_LAST_DETAIL, ""));
    }

    private void shareReport() {
        CharSequence detail = statusDetail.getText();
        String body;
        if (TextUtils.isEmpty(detail) || getString(R.string.results_idle).contentEquals(detail)) {
            body = "I have not run a check in Unef Checkup yet.";
        } else {
            body = statusTitle.getText() + "\n\n" + detail;
        }
        Links.shareText(this, "Unef Checkup report", body);
    }

    private void dialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show();
    }

    private static String sentence(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1) + ".";
    }

    private String appVersion() {
        try {
            String v = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return v != null ? v : "1.1";
        } catch (Exception e) {
            return "1.1";
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
