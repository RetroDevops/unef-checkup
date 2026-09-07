# Unef Checkup

A small Android helper app for non-technical family members. It runs a quick
read-only check of the phone and then opens the correct built-in Android screen
for each common cleanup or safety task.

It is a **guide**, not an antivirus. See "What it does and does not do" below.

Package id: `com.retrodad.cellshield` (kept stable so updates install cleanly;
it is not shown to users). Display name: **Unef Checkup**.

## What it does

- **Check my phone**: reports whether Private DNS pop-up and ad blocking is on,
  and lists installed apps that hold powerful permissions (screen overlay,
  accessibility control, reading SMS, installing apps, device admin, usage
  access), ranked by how much scrutiny they deserve.
- **Block pop-ups and ads**: opens Private DNS settings with a plain-language tip
  (and a note about temporarily switching back if a website CAPTCHA breaks).
- **Review risky apps**: the same scan, with a direct "Open app info" and
  "Uninstall" button for each flagged app.
- **Free up storage / Clear browser data / Back up my phone / Check Play
  Protect**: one tap straight to the matching Android screen.
- **Send this report to my family**: shares the last check as plain text.

## What it does and does not do

Android does not let an app installed from a link (sideloaded) silently remove
other apps, read every app's private data, or act as a real malware scanner.
This app does the safe, allowed pieces and hands you to Android's own controls
for the rest. Keep **Google Play Protect** on for malware scanning.

## Permissions

| Permission | Why |
| --- | --- |
| `ACCESS_NETWORK_STATE` | Read whether Private DNS is active |
| `QUERY_ALL_PACKAGES` | See installed apps and their requested permissions for the scan |

No internet permission. The app makes no network calls and stores only a
"seen the welcome screen" flag and the text of the last check, on the device.

## Project layout

```
app/src/main/java/com/retrodad/cellshield/
  MainActivity.java   UI wiring, one screen
  PhoneCheck.java     read-only checks (Private DNS + app permission heuristic)
  Links.java          open Android settings screens, with fallbacks
app/src/main/res/      layout, item rows, vector icons, slate/amber theme, launcher icon
.github/workflows/     cloud build + one-time signing-key helper
```

Toolchain: Android Gradle Plugin 8.7.2, Gradle 8.9, JDK 17, `compileSdk` 35,
`minSdk` 26. Java, one dependency (`com.google.android.material` for the
Material 3 components and theme). Release APK is roughly 4.5 MB.

## Getting the APK (cloud build)

This repo builds itself on GitHub Actions. No Android Studio or Android SDK is
needed on your computer.

- Every push to `main` builds the APK and publishes it to the
  [latest release](../../releases/latest) as `UnefCheckup.apk`, plus a build
  artifact named `UnefCheckup-apk`.
- To cut a versioned release, push a tag: `git tag v1.0.0 && git push --tags`.
- Manual run: Actions tab -> **Build APK** -> Run workflow, or
  `gh workflow run "Build APK"`.

The direct download link to hand to family is:

```
https://github.com/RetroDevops/unef-checkup/releases/latest/download/UnefCheckup.apk
```

Send it together with [INSTALL-FOR-PARENTS.md](INSTALL-FOR-PARENTS.md).

## Signing

The first build works with no setup: with no signing key configured, the
release APK is signed with the standard Android debug key, which installs fine
for sideloading. The catch: a future update must be signed with the **same**
key or Android refuses to install it over the old copy, and the debug key is
not something you control long-term.

To use your own stable key (do this once you know you will ship updates):

1. Store a password and an alias as repository secrets:

   ```bash
   gh secret set KEYSTORE_PASSWORD      # a strong password, keep a copy
   gh secret set KEY_ALIAS             # for example: unef
   ```

2. Run the one-time generator and download the result:

   ```bash
   gh workflow run "Create signing key"
   # when it finishes:
   gh run download --name signing-keystore
   ```

3. Store the key and its password as the remaining secrets:

   ```bash
   gh secret set KEYSTORE_BASE64 < release.keystore.base64
   gh secret set KEY_PASSWORD           # the SAME password as step 1
   ```

4. Back up `release.keystore` somewhere safe and private. If you lose it you
   cannot ship updates. Then delete the downloaded copies from your working
   folder.

5. Re-run **Build APK**. The APK is now signed with your key.

## Updating the app on your parents' phones

Android only lets an update install over an existing app if it is signed with
the same key. The build generates a fallback signing key once and keeps it in
the Actions cache (`unef-fallback-keystore-v1`), so a rebuild months later
still produces a compatible update: your parents just download the new
`UnefCheckup.apk` and install it over the old one.

Two things break that compatibility:

- Setting up your own signing secrets later (the key changes once).
- Deleting the `unef-fallback-keystore-v1` GitHub Actions cache.

If either happens, the next update will fail to install and your parents need
to uninstall the old app first, then install the new one. Nothing is lost
except the "you have seen the welcome screen" flag.

## Building locally (optional)

Open the folder in Android Studio (Koala or newer); it will finish the Gradle
wrapper setup and let you Run. Or, with Gradle installed:

```bash
gradle wrapper --gradle-version 8.9   # first time only
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/`.

## A note on the Play Store

This build targets direct download (sideload). Publishing to Google Play would
require a paid Play Console account, a privacy policy URL, the Data safety form,
and review, which is stricter for anything presented as security software.
`QUERY_ALL_PACKAGES` also needs a declared justification on Play. None of that
is needed for sending the APK to your own family.
