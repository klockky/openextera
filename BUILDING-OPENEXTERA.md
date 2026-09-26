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

There is no `google-services.json` in the repository. Without it the
`com.google.gms.google-services` plugin is simply not applied, Firebase is not
initialised and the app relies on Telegram's own background connection for
notifications. If you have your own Firebase project, create Android apps for
`com.openextera.messenger` and `com.openextera.messenger.beta` (debug), enable Cloud
Messaging and put the downloaded `google-services.json` into `TMessagesProj_App/` (and
`TMessagesProj/`). It is listed in `.gitignore` — do not commit it.
Firebase Analytics and Crashlytics are not used.

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
