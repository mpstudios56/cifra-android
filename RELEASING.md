# Releasing Cifra

## Which Java to build with

Gradle 8.14 cannot read the JDK that ships inside Android Studio, which is Java 25:
any edit to a build script then fails with `Unsupported class file major version
69`. Builds appear to work until a build script changes, because Gradle reuses the
previously compiled script. Point `JAVA_HOME` at a Java 21 JDK instead, for example
the one Android Studio downloads to `~/.jdks/`:

    export JAVA_HOME="$USERPROFILE/.jdks/jbr-21.0.11"

## One-off setup: the signing key

Published builds must be signed with a key that is yours. The debug key Android
generates automatically is rejected by Google Play, and until the steps below are
done the release build silently falls back to it.

Create the key. It asks for the passwords interactively, so nobody has to write
them into a file that could be read later:

    keytool -genkeypair -v -keystore cifra-release.jks \
            -alias cifra -keyalg RSA -keysize 4096 -validity 10000

Then copy `keystore.properties.example` to `keystore.properties` and fill in the
passwords you just chose. Both `keystore.properties` and `*.jks` are in
`.gitignore` and must stay out of the repository.

**Back up `cifra-release.jks` somewhere safe.** If it is lost, the published app can
never be updated again: Android accepts an update only when it carries the same
signature. Turning on Play App Signing when the app is first uploaded means Google
keeps a copy of the signing key, which is the usual protection against this.

## Each release

1. Raise `android:versionName` and `android:versionCode` in
   `app/src/main/AndroidManifest.xml`. The version code must increase on every
   upload; Play refuses a build that reuses one.
2. Add what changed to `app/src/main/assets/whatsnew.htm`.
3. Build the bundle:

       ./gradlew bundleRelease

   The result is `app/build/outputs/bundle/release/app-release.aab`. Play wants
   this bundle, not an APK.
4. Confirm it is signed with your key and not the debug one:

       apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk

   The subject must not say `CN=Android Debug`.
5. Upload the bundle in the Play Console and fill in the release notes.

## What the Play Console asks for

- A privacy policy at a public address. `PRIVACY.md` in this repository serves, via
  its GitHub URL.
- The Data safety form. Cifra collects nothing; the optional Google Drive, Dropbox
  and notification-reading features are described in `PRIVACY.md`.
- The content rating questionnaire.
- Store listing: description, screenshots, feature graphic.

A new personal developer account must additionally run a closed test with at least
twelve testers, opted in for fourteen consecutive days, before production access is
granted. Check the current rule in the console, as Google changes it.

## Licence obligations

Cifra is a fork of Financisto Holo, which is GPL-2.0. Distributing it is allowed
provided the source stays available and the original authors keep their credit,
which the About page carries. The store description should say the app is based on
Financisto.
