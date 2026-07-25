# WA Notify Bridge (Android detector)

A deliberately small Android app. It watches WhatsApp and WhatsApp Business notifications,
and when a genuinely new message arrives it POSTs a JSON summary to a webhook. That is the
whole app.

It does **not** read chat history, does **not** use an AccessibilityService, and cannot send
a message. Replying happens elsewhere — in the Claude Code Routine driving WhatsApp Web (see
[`../ROUTINE.md`](../ROUTINE.md)). That split is intentional: the phone is a sensor, not an
actor.

## Why a notification listener instead of polling

Whatauto-style apps scan on a timer, so a reply lands whenever the next scan happens. A
`NotificationListenerService` is woken by the system the instant WhatsApp posts a
notification, so the webhook fires in well under a second. There is no scan interval to tune
because there is no scanning.

## What gets forwarded

```json
{
  "sender": "Ravi Kumar",
  "chat_type": "individual",
  "group_name": null,
  "message": "Hi, can I book a scaling this Friday?",
  "timestamp": "2026-07-25T14:32:07+08:00",
  "app": "whatsapp_business"
}
```

Sent with `X-Webhook-Secret: <your secret>`. Three attempts, backing off 2s then 6s;
permanent failures (a 4xx that isn't 408/429 — wrong URL, wrong secret) are not retried.

### What gets dropped before it ever reaches the network

| Dropped | Why |
|---|---|
| Repeat notifications for a message already forwarded | WhatsApp re-posts a chat's notification constantly. See "De-duplication" below. |
| Group-summary notifications (`FLAG_GROUP_SUMMARY`) | They duplicate their children. |
| Ongoing notifications | Calls in progress, not messages. |
| `"3 new messages"` rollups | Placeholder text, not content. |
| Bare `Photo` / `Missed voice call` / `voice message` bodies | Nothing to read. A caption *is* forwarded (`📷 Photo\nIs this tooth ok?`). |
| Group chats | Unless you switch groups on; off by default. |
| Block-listed senders | Always wins over the allow-list. |
| Anything arriving in a quiet window | Staff can answer those themselves — see below. |

### When the bridge is active

Not "business hours" — the inverse. Forwarding runs when nobody is free to answer:

- outside the clinic's opening hours;
- during the **peak windows** inside opening hours, when the team is with patients.

The same schedule applies every day of the week — the booking line is staffed seven days,
Sundays included, so there is no day-of-week setting to configure.

During the calm stretches of the working day it stays quiet on purpose: staff can see those
messages themselves, and a bot replying over someone who is sitting right there is worse than a
slightly slower human.

Configured as opening time, closing time, and a list of peak windows. The settings
screen previews the resulting active and quiet windows, so a mistyped window is obvious before it
costs a day of coverage. Defaults are the clinic's real schedule — open 09:00–21:00 every day, peaks
`08:00-09:30`, `12:45-14:15`, `17:00-18:30`, `20:00-22:00` — which works out to active
00:00–09:30, 12:45–14:15, 17:00–18:30 and 20:00–24:00.

A malformed peak-window line is dropped rather than crashing. Worth knowing which way that fails:
losing every peak window makes the bridge go **quiet** during opening hours, not spam — off-hours
forwarding keeps working either way. That's what the preview is for.

Turn the schedule switch off to forward around the clock.

### De-duplication

The listener keys on message content, not on the notification: app + group + sender +
message timestamp + text hash. A key stays suppressed for 10 minutes and the window refreshes
on every re-post, so a chat that re-posts every few seconds is forwarded exactly once. The
cache holds 300 keys, in memory only — on a listener reconnect it is cleared deliberately,
since stale keys could suppress a message that really is new.

For MessagingStyle notifications (what modern WhatsApp posts) only the **newest** entry in
`EXTRA_MESSAGES` is read. WhatsApp attaches the recent history of the chat on every re-post;
reading all of it would re-forward old messages.

## Build in CI (no local Android SDK needed)

[`.github/workflows/build.yml`](../.github/workflows/build.yml) builds the debug APK on every
push that touches `android-detector/`. GitHub's `ubuntu-latest` runner image already has the
Android SDK, so there is nothing to install and no local dev environment to maintain.

To get an APK:

1. Push to any branch (or trigger the workflow by hand: **Actions → Build APK → Run workflow**).
2. Open the run under the repo's **Actions** tab.
3. Download the **`wa-notify-bridge-debug-<sha>`** artifact — a zip containing `app-debug.apk`.
4. Unzip and install it on the phone with `adb install -r app-debug.apk`, or copy the APK across
   and open it.

The workflow also runs the unit tests and uploads the test and lint reports as a separate
artifact, kept even when the build fails — that's when they're worth reading. Lint is
`continue-on-error` so a style warning doesn't cost you the APK; the tests are not, so a broken
dedupe or schedule fails the build.

Artifacts are debug-signed with the standard Android debug key. That's fine for installing on
your own phone and wrong for anything else — a release build needs the signing setup below, run
locally, because the keystore must not go in the repo or in CI secrets for a project like this.

## Build locally

Requirements: JDK 17, Android SDK with platform 35, Android Studio Ladybug or newer (or just
the command-line SDK).

```bash
cd android-detector
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew test                 # unit tests for the dedupe and body-filter logic
```

Point Gradle at your SDK if it isn't already, via `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
```

### Release build and signing

Generate a keystore once and keep it off the repo:

```bash
keytool -genkeypair -v -keystore wa-bridge.jks -alias wa-bridge \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then create `android-detector/keystore.properties` (gitignored):

```properties
storeFile=wa-bridge.jks
storePassword=…
keyAlias=wa-bridge
keyPassword=…
```

```bash
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
```

If `keystore.properties` is absent the release build is simply left unsigned rather than
failing — convenient for CI, useless for installing.

## Install: this is a sideload, not a Play Store app

**This app cannot be distributed on the Play Store, and you should not try.** Google's
[Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379)
and permissions policies restrict `BIND_NOTIFICATION_LISTENER_SERVICE` to apps whose core
function genuinely requires reading notifications, and forwarding another app's message
content to a third-party server is precisely the pattern reviewers reject. Whatauto and its
peers survive on Play by shipping a *user-facing* reply feature and keeping the automation
on-device; a bridge that exfiltrates message text to a webhook does not fit any approved use
case.

So: build the APK and install it directly.

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

Or copy the APK to the phone and open it, having allowed installs from that file manager.

## Setup on the phone, in order

1. **Install and open the app.** Grant the notification permission it asks for — that one is
   only for its own persistent notification.
2. **Notification access.** Settings → Notifications → Notification access (on some OEMs:
   Settings → Apps → Special app access → Notification access) → *WA Notify Bridge* → on →
   confirm the scary dialog. **Android does not let an app grant this to itself**; the in-app
   button only opens the right screen. The app shows GRANTED / NOT GRANTED so you can check.
3. **Battery optimisation exemption.** Tap the button in the app and choose *Allow* /
   *Unrestricted*. Without it, Doze eventually kills the listener and detection silently
   stops. On Xiaomi/Redmi, Oppo/Realme, Vivo and Huawei also enable **Autostart** for the app
   and lock it in the recents screen — those skins ignore the standard exemption.
4. **Fill in the webhook URL and shared secret**, then Save. Both live in
   `EncryptedSharedPreferences`, are excluded from cloud backup and device transfer, and are
   never written to a log.
5. **Turn the master switch on.** A silent "Auto-reply bridge active" notification appears and
   stays; that notification is what keeps the process alive, so don't disable the channel.
6. **Populate the block-list** with staff, suppliers, family and the clinic's own numbers
   before going live. The allow-list is left empty on purpose — empty means *forward from
   everyone*, including first-time unknown numbers, which is what the operator asked for.
7. Send yourself a test message from another phone and confirm your webhook received it.

### Known limits worth accepting up front

- **No notification, no detection.** If a chat is muted in WhatsApp, or the chat is already
  open on screen when the message arrives, WhatsApp posts nothing and the bridge sees nothing.
- **Notification text can be truncated** for very long messages. The Routine reads the real
  thread on WhatsApp Web before replying, so the payload is a trigger and a hint, not the
  source of truth.
- **Reboots need the app to have been enabled**; `BootReceiver` restarts the foreground
  service, but Android may take a minute to rebind the listener.
- **A phone with no data connection queues nothing.** Three attempts over ~8 seconds, then the
  failure is logged and the message is dropped. Check the failure list in the app after any
  connectivity outage.

## Data handling

There is no message database. A message exists in memory for the duration of its POST
attempts and is then gone. The only thing written to disk is the failure log: timestamp, a
partially redacted sender (`Ra…ar`), and an error reason — capped at 100 lines, no message
bodies. Clear it from the settings screen.

## Layout

```
app/src/main/java/com/clinic/wanotifybridge/
├── WaBridgeApp.kt              application + notification channel
├── data/BridgeSettings.kt      EncryptedSharedPreferences-backed config
├── data/MessageEvent.kt        the payload, its JSON, and its dedupe key
├── notify/WaNotificationListener.kt   the detector; filters then forwards
├── notify/NotificationParser.kt       notification → MessageEvent
├── notify/BodyFilter.kt        is this body a real message?
├── notify/Deduper.kt           content-keyed TTL cache
├── net/WebhookClient.kt        POST with retry/backoff
├── service/BridgeForegroundService.kt persistent notification, keeps process alive
├── service/BootReceiver.kt     restart after reboot / app update
├── ui/MainActivity.kt          settings + the permission walkthrough
└── util/                       ActiveSchedule, SenderFilter, FailureLog
```
