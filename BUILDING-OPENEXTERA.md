# Building OpenExtera

OpenExtera is an open-source rebuild of exteraGram lite on top of the official
Telegram for Android sources (currently Telegram 12.10.5). Only the regular app module
(`TMessagesProj_App`) is part of the build; the other upstream flavours (Huawei,
HockeyApp, Standalone, Tests) stay in the tree for easier merges but are excluded in
`settings.gradle`.

## Requirements

| Tool | Version |
| --- | --- |
| JDK | 17 or newer (21 is tested) |
| Android SDK platform | 36 |
| Android SDK build-tools | 36.0.0 |
| Android NDK | 27.2.12479018 |
| CMake (SDK package) | 3.22.1 |
| Gradle | 8.13 (wrapper included) |
| Android Gradle Plugin | 8.13.2 |
| Kotlin | 2.3.21 (KSP 2.3.12) |

Install the SDK packages with:

```bash
sdkmanager "platforms;android-36" "build-tools;36.0.0" "ndk;27.2.12479018" "cmake;3.22.1" "platform-tools"
```

The native part (`TMessagesProj/jni`) uses git submodules, so clone recursively:

```bash
git clone --recursive --shallow-submodules <repo-url> openextera
# or, in an existing checkout:
git submodule update --init --recursive --depth=1
```

## Telegram API credentials

Every Telegram client needs its own `api_id` / `api_hash`. OpenExtera does not ship any.

1. Log in at <https://my.telegram.org>, open **API development tools** and create an
   application (see <https://core.telegram.org/api/obtaining_api_id>).
2. Put the values into `local.properties` in the repository root (this file is ignored by git):

   ```properties
   sdk.dir=/path/to/android-sdk
   openextera.appId=1234567
   openextera.appHash=0123456789abcdef0123456789abcdef
   ```

   or export them as environment variables (handy for CI):

   ```bash
   export OPENEXTERA_APP_ID=1234567
   export OPENEXTERA_APP_HASH=0123456789abcdef0123456789abcdef
   ```

   `local.properties` takes precedence over the environment. Without either, the build
   still succeeds but uses `APP_ID = 0` / `APP_HASH = ""` and the app cannot log in.

The values end up in `org.telegram.messenger.BuildConfig.APP_ID` (int) and
`BuildConfig.APP_HASH` (String) of the `TMessagesProj` module.

### Optional keys

| Property (`local.properties`) | Environment variable | Used for |
| --- | --- | --- |
| `openextera.mapsApiKey` | `OPENEXTERA_MAPS_API_KEY` | Google Maps SDK key (`com.google.android.maps.v2.API_KEY`). Without it, map previews/location picking show an empty map. The key must be restricted to your application id and signing certificate. |

### Firebase (push notifications)

FCM needs two different files with different purposes:

1. Register the debug Android app `com.openextera.messenger.beta` in your own
   Firebase project. Put its downloaded client config at
   `TMessagesProj_App/src/debug/google-services.json`. For a release build, also
   register `com.openextera.messenger` and put that app's config at
   `TMessagesProj_App/src/release/google-services.json`. A single config at
   `TMessagesProj_App/google-services.json` also works if it contains matching
   clients for both package names. These files are ignored by Git. Do not put a
   config in `TMessagesProj/`, which is a library module.
2. Enable the Firebase Cloud Messaging API in the Firebase project. In the
   settings of the matching Telegram API application at `my.telegram.org`,
   configure push delivery using the Firebase project's **service-account
   JSON**. This is a server credential with a private key: upload it only to
   Telegram's application settings, never put it in the APK, this repository,
   GitHub Actions, or chat.

The build applies the Google Services plugin only when an Android client config
is present. Without one, the APK still builds and uses Telegram's background
connection; it cannot receive FCM pushes. With one, the existing Telegram code
gets an FCM token and registers it with `account.registerDevice` as token type 2.
Telegram can deliver push messages only after its application settings also
have the matching server credential. Debug and release packages need their own
Firebase Android app entries. Firebase Analytics and Crashlytics are not used.

## App identity

Set in `gradle.properties`:

```properties
APP_PACKAGE=com.openextera.messenger
APP_VERSION_NAME=12.10.5
APP_VERSION_CODE=7105
```

Debug builds install as `com.openextera.messenger.beta`, so they can live next to a
release build.

## Signing

`TMessagesProj/config/release.keystore` is the publicly known dummy keystore from the
upstream repository (passwords in `gradle.properties`). It is fine for local builds, but
**use your own keystore for anything you distribute**: replace the file and set
`RELEASE_KEY_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_STORE_PASSWORD` (for example in
`~/.gradle/gradle.properties`, so the secrets stay out of the repository).

## Building

```bash
# debug APK (all ABIs) -> TMessagesProj_App/build/outputs/apk/afat/debug/app.apk
./gradlew :TMessagesProj_App:assembleAfatDebug

# release APK -> TMessagesProj_App/build/outputs/apk/afat/release/app.apk
./gradlew :TMessagesProj_App:assembleAfatRelease

# release app bundle
./gradlew :TMessagesProj_App:bundleBundleAfatRelease
```

The first build compiles the native library (`libtmessages.49.so`) for four ABIs and takes
a while. Give Gradle enough memory (`org.gradle.jvmargs` in `gradle.properties`, 8 GB by
default; `-Dorg.gradle.jvmargs=-Xmx4g` works for dependency-only tasks).

### GitHub Actions

`.github/workflows/android.yml` builds an arm64 debug APK on every push and pull request,
and can also be started from the repository's **Actions** tab. Download the APK from the
completed run's **Artifacts** section. The artifact is kept for 14 days.

To make the CI APK able to log in, add your own Telegram API credentials under the
repository's **Settings → Secrets and variables → Actions** as `OPENEXTERA_APP_ID` and
`OPENEXTERA_APP_HASH`. Without them the build succeeds with the documented empty defaults,
but the app cannot log in. Never put exteraGram's credentials in these secrets.

For FCM in the CI debug APK, add `OPENEXTERA_GOOGLE_SERVICES_JSON_B64` as an Actions
secret. Its value is the base64 encoding of your **debug Android client**
`google-services.json` (on Linux: `base64 -w0 google-services.json`). The workflow
decodes it only during the build and labels the artifact `fcm-enabled` or
`fcm-disabled`. Do not use the Firebase service-account JSON here.

The `Dockerfile` in the repository root builds the release APK and bundles in a clean
environment.

## Notes

* Strings: Telegram strings live in `TMessagesProj/src/main/res/values*/strings.xml`,
  exteraGram strings in `values*/strings_extera.xml`. At build time both are packed into
  `assets/localization_<lang>.bin` (see `buildSrc/.../TelegramStringsTask.kt`), and the
  `strings_extera.xml` files are also mirrored to `assets/extera_locales/<values dir>/extera.xml`,
  which `LocaleController` uses to overlay exteraGram strings on server language packs.
* The exteraGram native ad blocker (`libetgadblock.so`) is closed source and is not
  included; the corresponding features are disabled when the library is missing.
* `.icons` / `.extera` files opened from other apps are handled by `LaunchActivity`
  (icon packs and settings backups).
