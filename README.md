# Cell Shield Lite

A small Android helper app for non-technical family members. It runs a quick
read-only check of the phone and then opens the correct built-in Android screen
for each common cleanup or safety task.

It is a **guide**, not an antivirus. See "What it does and does not do" below.

## What it does

- **Check my phone**: reports whether Private DNS pop-up and ad blocking is on,
  and lists installed apps that hold powerful permissions (screen overlay,
  accessibility control, reading SMS, installing apps, device admin, usage
  access). Results are ranked by how much scrutiny they deserve.
- **Block pop-ups and ads**: opens Private DNS settings with a plain-language tip
  (and a note about temporarily switching back if a website CAPTCHA breaks).
- **Review risky apps**: same scan as above, with a direct "Open app info" and
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
app/src/main/res/      layout, strings, colors, theme, launcher icon
.github/workflows/     cloud build + one-time signing-key helper
```

Toolchain: Android Gradle Plugin 8.7.2, Gradle 8.9, JDK 17, `compileSdk` 35,
`minSdk` 26. Pure Java, no third-party dependencies.

## Getting the APK (cloud build)

This repo builds itself on GitHub Actions. There is no need for Android Studio
or the Android SDK on your computer.

1. Push to `main` (or run the **Build APK** workflow manually from the Actions
   tab, or with `gh workflow run "Build APK"`).
2. Open the finished run in the **Actions** tab.
3. Download the **CellShieldLite-apk** artifact. Inside is `CellShieldLite.apk`.

With the GitHub CLI:

```bash
gh run download --name CellShieldLite-apk
```

That `.apk` is what you send to your parents. See
[INSTALL-FOR-PARENTS.md](INSTALL-FOR-PARENTS.md) for the instructions to send
along with it.

## Signing

The first build works with no setup: if no signing key is configured, the
release APK is signed with the standard Android debug key. That APK installs
fine for sideloading. The catch is that a future update must be signed with the
**same** key or Android will refuse to install it over the old one, and the
debug key is not something you control long-term.

To use your own stable key (recommended once you know you will send updates):

1. Pick a strong password and an alias, and store them as repository secrets:

   ```bash
   gh secret set KEYSTORE_PASSWORD      # type a strong password, keep a copy
   gh secret set KEY_ALIAS             # for example: cellshield
   ```

2. Run the one-time key generator and download the result:

   ```bash
   gh workflow run "Create signing key"
   # wait for it to finish, then:
   gh run download --name signing-keystore
   ```

3. Store the key and its password as the remaining secrets:

   ```bash
   gh secret set KEYSTORE_BASE64 < release.keystore.base64
   gh secret set KEY_PASSWORD           # type the SAME password as step 1
   ```

4. Back up `release.keystore` somewhere safe and private (a password manager or
   an encrypted drive). If you lose it you cannot ship updates. Then you can
   delete the downloaded copies from your working folder.

5. Re-run **Build APK**. From now on the APK is signed with your key.

## Building locally (optional)

Open the folder in Android Studio (Koala or newer). It will offer to finish the
Gradle wrapper setup. Then Run, or:

```bash
gradle wrapper --gradle-version 8.9   # first time only, if you have Gradle installed
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/`.

## A note on the Play Store

This build targets direct download (sideload). Publishing to Google Play would
require a paid Play Console account, a privacy policy URL, the Data safety form,
and review, which is stricter for anything presented as security software.
`QUERY_ALL_PACKAGES` in particular needs a declared justification on Play. None
of that is needed for sending the APK to your own family.
