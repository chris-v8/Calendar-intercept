# Calendar intercept

An LSPosed / Vector module for **Niagara Launcher** (`bitpit.launcher`).

When you tap **today's date** in Niagara's date widget, Niagara normally opens the
calendar app on that exact day (`content://com.android.calendar/time/<millis>`).
With this module, tapping today opens your default calendar app's **main screen**
instead. If no calendar app is set as the default, Android asks which one to use.

Tapping any other date, or an event, works exactly as before.

## Install

1. Install the APK.
2. In the LSPosed / Vector manager, enable **Calendar intercept**. Niagara Launcher is
   pre-selected as its scope.
3. Force-stop Niagara Launcher (or reboot) so the module loads.

## Build

Requires JDK 17 and the Android SDK (path in `local.properties`).

```
gradlew assembleDebug
```

Release signing reads `keystore.properties` (not committed) or the `CI_KEYSTORE_*`
environment variables.

## Licence

GPL-3.0-or-later. See [LICENSE](LICENSE).
