# WhatsApp Instant Auto-Reply

Two halves that do one job: notice a WhatsApp message the moment it arrives, and answer it with
a real, context-aware reply — or, when answering isn't clearly safe, hand it to a human.

This is a replacement for keyword-based Android auto-reply apps (Whatauto and its clones), which
have two problems this system fixes:

- **They poll.** A scheduled scan means the reply lands whenever the next scan runs. Here the
  Android side is woken by the notification itself, so the trigger fires in well under a second.
- **They match keywords.** A keyword table can't read a thread, can't tell a booking question
  from a complaint, and cheerfully replies to things it shouldn't. Here the reply is drafted by
  Claude after reading the actual conversation, against the clinic's own written facts.

## Architecture

```
  Phone (WhatsApp Business)
        │  notification posted
        ▼
  ┌─────────────────────────┐
  │  WA Notify Bridge       │   /android-detector
  │  NotificationListener   │   detects only — cannot send
  │  + foreground service   │
  └───────────┬─────────────┘
              │  HTTPS POST + X-Webhook-Secret
              ▼
  ┌─────────────────────────┐
  │  Claude Code Routine    │   ROUTINE.md
  │  validate → safety gate │
  │  → read thread → draft  │
  └───────────┬─────────────┘
              │  Claude in Chrome
              ▼
  ┌─────────────────────────┐        ┌──────────────────────────────┐
  │  WhatsApp Web Business  │        │  Clinic-Automation-Logs/     │
  │  reads context, sends   │───────▶│  WhatsApp_AutoReply_Log.md   │
  └─────────────────────────┘        └──────────────────────────────┘
```

The split matters. **The phone detects; the cloud decides and sends.** The Android app has no
AccessibilityService and no send path at all — not disabled, not present. That keeps the
riskiest capability (typing into someone else's chat) in one place, behind the Routine's safety
gates, where it's auditable and can be switched off with a single config flag.

## What's here

| Path | What it is |
|---|---|
| [`android-detector/`](android-detector/) | The Kotlin/Gradle Android app. Build, signing and phone-setup instructions in its own [README](android-detector/README.md). |
| [`ROUTINE.md`](ROUTINE.md) | The Routine spec — validation, safety gates, drafting rules, dry-run mode. Paste into a webhook Routine in Claude Code Desktop. |
| [`context/clinic-info.md`](context/clinic-info.md) | **Placeholder — fill this in.** The only facts the Routine is allowed to state to a patient. |
| [`logs/README.md`](logs/README.md) | Log row format, action/reason vocabulary, and where the real file lives. |

## Setup order

Do it in this order; each step depends on the one before.

1. **Fill in [`context/clinic-info.md`](context/clinic-info.md).** Hours, branches, services,
   price policy, tone, escalation contacts. Until this is real, every run correctly decides it
   doesn't know enough to reply, and the system does nothing.
2. **Create the Routine** in Claude Code Desktop from `ROUTINE.md`, trigger type Webhook. Leave
   `DRY_RUN = true`. Copy out the webhook URL and secret.
3. **Confirm the Routine's environment**: Claude in Chrome, a WhatsApp Web Business tab already
   signed in, and access to the `Clinic-Automation-Logs` Desktop folder.
4. **Build and sideload the Android app** (`./gradlew assembleRelease`, then `adb install`).
   It is not a Play Store app — see [why](android-detector/README.md#install-this-is-a-sideload-not-a-play-store-app).
5. **On the phone**: grant Notification access, grant the battery-optimisation exemption, paste
   the webhook URL and secret, populate the block-list with staff and supplier numbers, then
   flip the master switch on.
6. **Test end to end** from another phone. Confirm a log row appears with `action: dry_run`.
7. **Read a few days of dry-run drafts.** Then, and only then, decide about `DRY_RUN = false`.

## Configuration decisions already made

Recorded here because they're the ones that change behaviour most, and because a future reader
should know they were deliberate:

- **Unknown numbers get replies.** `ALLOW_LIST` is empty, which means "everyone" — a first-time
  patient messaging the clinic is the main case this exists for. Filtering is done by
  `BLOCK_LIST` instead. Consequence, stated plainly: with the allow-list open, the emergency
  gate and the staff-reply gate are the only things standing between an unusual message and an
  automated answer. Keep them intact.
- **Filtering runs in both layers.** The Android app drops group chats, block-listed senders and
  out-of-hours messages before spending a network call; the Routine re-checks its own lists as
  the authoritative gate. Two lists to maintain, but a stale phone config can't open a hole in
  the cloud policy.
- **Outside business hours, nothing is forwarded at all** when the window is enabled. The
  message just sits in WhatsApp for a human in the morning. No "we're closed" auto-reply — that
  was an explicit choice, not an omission, and it's a one-line change in `BusinessHours` if you
  want the other behaviour.
- **Groups are never auto-replied to**, at any setting. The Android switch only controls whether
  group messages get forwarded for logging.
- **Dry-run is the default** and stays true until a human turns it off.

## Risk note: WhatsApp Terms of Service

**Automating sends on a real WhatsApp Business number carries a genuine risk of that number
being banned, and this is true regardless of the method used.**

Be clear about what this system is. WhatsApp's
[Terms of Service](https://www.whatsapp.com/legal/terms-of-service) prohibit accessing the
service through unauthorised or automated means; the sanctioned path for programmatic messaging
is the WhatsApp Business API (through Meta or a BSP), not a browser session driven by a tool.
Driving WhatsApp Web with Claude in Chrome is exactly the kind of automated access those terms
address. A notification listener reading incoming messages is the less exposed half — the
sending half is what carries the risk.

What that means in practice:

- **The number can be banned, with little warning and no appeal you can rely on.** For a clinic
  the WhatsApp number *is* a patient channel, often the primary one. Losing it loses the chat
  history and every patient's ability to reach you at the number they've saved.
- **Volume and pattern raise the risk.** Bursts, identical repeated text, replies at inhuman
  speed at 3am, and messaging numbers that never messaged you first are the signals most likely
  to draw enforcement. Business hours and dry-run mode reduce exposure; they don't remove it.
- **This system does not make automated sending compliant.** The safety gates exist to stop the
  Routine saying something wrong to a patient. They are not a ToS workaround and don't make the
  automation authorised.
- **The compliant alternative exists**: the WhatsApp Business Platform (Cloud API) with approved
  message templates. It costs money and needs approval, and it's the only route that gives you
  automated messaging you can actually build a clinic's patient communication on. If this
  channel matters to the business, migrate to it rather than scaling this up.
- **Do not use this on the clinic's only number** while evaluating it. A secondary number for
  testing costs nothing next to the downside.

Run this with those facts on the table. Keeping `DRY_RUN = true` gives you the drafting quality
with none of the sending risk, and is a legitimate long-term configuration — a human copying an
approved draft into WhatsApp is fast, and it's not automated sending.

## Privacy

The Android app stores no messages: an event lives in memory for the duration of its POST
attempts and is then gone. The failure log holds timestamps, partially redacted senders and
error reasons — no message bodies. The webhook URL and secret are in
`EncryptedSharedPreferences`, excluded from cloud backup and device transfer.

That makes `WhatsApp_AutoReply_Log.md` the single place patient message text is durably stored.
Set a retention period for it with the clinic; see [`logs/README.md`](logs/README.md#retention).
