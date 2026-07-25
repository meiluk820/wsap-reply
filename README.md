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
  └───────────┬─────────────┘        └──────────────────────────────┘
              │  can't answer safely?
              ▼
  ┌─────────────────────────┐
  │  WhatsApp ping to Andy  │   "one chat I need your review"
  │  +60182888972           │   patient's chat left untouched
  └─────────────────────────┘
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
| [`.github/workflows/build.yml`](.github/workflows/build.yml) | CI that builds the debug APK and runs the unit tests, so no local Android SDK is needed. |

## Setup order

Do it in this order; each step depends on the one before.

1. **Fill in [`context/clinic-info.md`](context/clinic-info.md).** Hours, branches, services,
   price policy, tone, escalation contacts. Until this is real, every run correctly decides it
   doesn't know enough to reply, and the system does nothing.
2. **Create the Routine** in Claude Code Desktop from `ROUTINE.md`, trigger type Webhook. Leave
   `DRY_RUN = true`. Copy out the webhook URL and secret.
3. **Confirm the Routine's environment**: Claude in Chrome, a WhatsApp Web Business tab already
   signed in, and access to the `Clinic-Automation-Logs` Desktop folder.
4. **Get the APK and sideload it.** Easiest path needs no local Android SDK: push the branch and
   download the debug APK from the GitHub Actions run
   ([details](android-detector/README.md#build-in-ci-no-local-android-sdk-needed)). It is not a
   Play Store app — see [why](android-detector/README.md#install-this-is-a-sideload-not-a-play-store-app).
5. **On the phone**: grant Notification access, grant the battery-optimisation exemption, paste
   the webhook URL and secret, then populate the block-list — **starting with Andy's escalation
   number `+60182888972`**, plus staff, suppliers and family. Then flip the master switch on.
6. **Test end to end** from another phone. Confirm a log row appears with `action: dry_run`.
7. **Read a few days of dry-run drafts.** Then, and only then, decide about `DRY_RUN = false`.

## Coverage windows

The clinic opens **9:00am–9:00pm**, and the team is busiest at opening, over lunch, at evening
changeover, and late evening. Those peaks are exactly when WhatsApp goes unanswered, so they get
covered alongside the hours the clinic is shut:

```
        00:00      09:00   09:30      12:45   14:15   17:00   18:30   20:00   21:00      24:00
          │          │       │          │       │       │       │       │       │          │
 BRIDGE   ████████████████████          █████████       █████████       ██████████████████████
 TEAM                        ████████████       █████████       █████████
          └── closed ────────┴────────── open 09:00–21:00 ──────────────┴──── closed ─────┘
```

- **Bridge active:** 00:00–09:30, 12:45–14:15, 17:00–18:30, 20:00–24:00
- **Team handles it:** 09:30–12:45, 14:15–17:00, 18:30–20:00
- **Every day, including Sunday** — one schedule, no day-of-week setting

Peak windows as configured: `08:00-09:30`, `12:45-14:15`, `17:00-18:30`, `20:00-22:00`. The
08:00 and 22:00 edges fall outside opening hours, where the bridge is active anyway — they're
written that way so the list reads as the clinic's real peaks rather than as a derived artefact.

Both layers apply this: the Android app won't forward a quiet-window message, and the Routine
re-checks before replying so a stale phone config can't put words over a staff member who is
sitting right there. Editable in the app's settings screen, which previews the resulting active
and quiet windows so a typo shows up before it costs a day of coverage.

**The same schedule runs seven days a week, deliberately.** This is a general booking line for
several branches: some branch is open every day, and staff work the line on Sundays as usual, so
there is no day where nobody is watching and no day that needs different treatment. A
day-of-week setting would only ever be a thing to get wrong.

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
- **The schedule is "when nobody's free", not "business hours".** The point of the system is
  covering the times the team can't get to WhatsApp — after hours *and* during the busy stretches
  of the working day. During the calm stretches the bridge deliberately stays quiet, because
  staff can see those messages themselves and a bot replying over them is worse than a slightly
  slower human. See [Coverage windows](#coverage-windows).
- **Groups are never auto-replied to**, at any setting. The Android switch only controls whether
  group messages get forwarded for logging.
- **Dry-run is the default** and stays true until a human turns it off.
- **Timing questions always ask which branch first.** One number serves SS2 Taman Paramount, Ara
  Damansara and Putra Heights, so "are you open?" is unanswerable as asked. Only SS2 gets an
  automatic answer (open every day); Ara Damansara and Putra Heights timing goes to the team. The
  Routine must never answer with the booking line's 09:00–21:00 staffing hours — those are when
  someone answers WhatsApp, not when a branch is open, and confusing the two would be the most
  confidently wrong reply this system could produce.
- **Flagged messages ping a human on WhatsApp**, not just the log. Anything the Routine won't
  answer sends a short message to Andy (+60 18-288 8972) — immediately for suspected emergencies,
  otherwise one ping per chat per 20 minutes. The ping fires even in dry-run mode: dry-run is
  about not messaging *patients*, and a flagged message is time-sensitive either way. Andy's
  number is block-listed in both layers so his replies can't be mistaken for a patient enquiry
  and answered — that entry is load-bearing, not tidiness.

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
- **Volume and pattern raise the risk**, and this system's schedule leans into the riskiest
  pattern. Instant replies at 3am, to a first-time number, are close to a textbook automation
  signature — bursts, identical repeated text, inhuman response latency and messaging numbers
  that never messaged you first are the signals most likely to draw enforcement, and overnight
  coverage plus an open allow-list hits three of the four. That is the direct cost of covering
  off-hours, and it is worth accepting knowingly rather than discovering later. The Routine
  softens the worst of it with a randomised 1–2 minute pause before sending between 1am and 5am
  (see [ROUTINE.md](ROUTINE.md#the-overnight-send-delay)) — a reply that takes a minute reads as
  someone awake rather than as software. It reduces the signal; it doesn't remove it. Dry-run mode
  is the only lever that removes the exposure entirely.
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
