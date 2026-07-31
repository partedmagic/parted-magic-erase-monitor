# Parted Magic Erase Monitor

Android companion app for the **PM Report Server**
(`pmagic_report_server`), which ships with
[Parted Magic](https://partedmagic.com/). It watches live secure-erase
sessions on your own network and raises phone notifications when a
drive fails, a session stalls, or a job finishes — including while the
phone is in a pocket during a multi-hour rack wipe.

**Monitoring only.** The app cannot start, stop, or modify an erase. It
reads. It talks to nothing except the server address you type into it.
No accounts, no cloud service, no analytics, no telemetry, no ads.

The source is public so you don't have to take that on faith. Every
network call the app can make lives in
`app/src/main/java/com/partedmagic/erasemonitor/Api.kt` and
`MonitorService.kt`.

## Requirements

- Android 8.0 (API 26) or newer.
- A machine on your network running `pmagic_report_server`, and a phone
  that can reach it — shop Wi-Fi, or your own VPN into that network.
  If the dashboard opens in the phone's browser, the app will work.

## What it does

- **Sessions screen** — one row per machine with the same
  ERASING / COMPLETE / FAILURES / STALLED badges as the web dashboard,
  plus drive counts. Refreshes every 5 seconds while open.
- **Session detail** — per-drive rows (device, model, method, live
  status) and the uploaded files; tapping a file opens it in the
  browser to download or share.
- **Settings** — server address and alert toggles. There is no token
  field: the server's `--token` guards *submitting* reports, and this
  app only reads.
- **Background monitoring** — an optional foreground service polls
  `/api/sessions` every 30 seconds and posts a high-priority
  notification when a drive newly fails, a session goes STALLED (the
  power-outage tell), or a session finishes. Alert state persists, so
  restarting the service does not replay old alerts. While monitoring
  is on, Android shows a small persistent "monitoring" notification —
  that is the OS rule for reliable background work, not a bug.

## Permissions, and why each one is here

Four permissions, no others. `app/src/main/AndroidManifest.xml` is
short enough to read in one sitting.

| Permission | Why |
|---|---|
| `INTERNET` | To reach your report server. Android has no permission that means "local network only," so this is the only permission that exists for the job. What constrains it is the code: the app builds every request from the address you entered in Settings and contacts nothing else. |
| `POST_NOTIFICATIONS` | To post the drive-failed / stalled / complete alerts. Denying it leaves the rest of the app working. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | To keep polling while the app is in the background, which is the entire point of alerts. Android requires the visible ongoing notification that comes with it. |

Not requested: location, storage, camera, microphone, contacts,
accounts, phone state, or the ability to run at boot.

`res/xml/network_security_config.xml` permits cleartext HTTP, because
report servers live on a LAN and speak plain `http://`. That setting
allows unencrypted traffic to the address you choose; it does not allow
anything to leave your network.

## Build from source

Requires JDK 17 and the Android SDK (compile/target SDK 35). Either
open the project in Android Studio and press Run, or from the command
line:

```
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. To install it on a
phone over USB with `adb`:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Sideloading any app that did not come from a store makes Android warn
about an unknown developer. That warning is about the distribution
channel, not the contents.

## Verifying a release build

Released APKs are published with a SHA-256 checksum. After downloading,
compare:

```
sha256sum parted-magic-erase-monitor-<version>.apk
```

If you would rather trust your own compiler than a checksum, check out
the tag matching the release and run `./gradlew assembleRelease` —
that is the tree the release was built from.

One thing worth knowing: Google Play re-signs apps with its own key, so
a copy installed from Play is not byte-identical to one built here or
downloaded from a release page. That is how Play works for every app on
it, not a sign that something was changed.

## Design notes

- Kotlin and Jetpack Compose. No networking or serialization
  libraries — `HttpURLConnection` and Android's built-in `org.json`
  keep the dependency surface small, which also keeps this repository
  easy to audit.
- The palette mirrors the erase app and the dashboard: dark navy,
  `#2A7FFF` accent, the same four status colors.
- Off-network, the app says so rather than spinning.

## License

Apache License 2.0 — see [LICENSE](LICENSE).

The license covers the code. It does not grant rights to the licensor's
trademarks: **the name "Parted Magic", the Parted Magic logo, and the
app icon and launcher artwork in this repository are not covered by the
Apache License.** Fork it, build it, modify it freely — but if you
redistribute a modified version, give it your own name and your own
icon so nobody mistakes it for Parted Magic software.

## Issues

Bug reports and patches for the app are welcome here. Questions about
Parted Magic itself, the erase utility, or licensing belong on the
[Parted Magic support page](https://partedmagic.com/support/).
