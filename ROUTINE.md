# Routine spec — WhatsApp instant auto-reply

This is the instruction set for the cloud half of the system: a webhook-triggered Claude Code
Routine that receives a message forwarded by the Android detector, decides whether replying is
safe, drafts a reply from the clinic's own information, and sends it through the already
authenticated WhatsApp Web Business session in Chrome.

It is a **spec, not a live Routine.** Routines are created in the Claude Code Desktop UI; see
"How to install this as a Routine" at the bottom. Everything between the trigger and the
output section is what you paste into the Routine's prompt.

---

## Configuration

```
DRY_RUN                    = true      # ← ships true. Drafts and logs, sends nothing.
REPLY_TO_GROUPS            = false     # never true. Groups are log-only, always.
STAFF_REPLY_WINDOW_MINUTES = 30
CONTEXT_MESSAGES_TO_READ   = 10        # read at least 5
ALLOW_LIST                 = []        # empty = reply to everyone, including unknown numbers
BLOCK_LIST                 = []        # always wins over ALLOW_LIST

# Schedule — see "Coverage windows" below. Mirrors the Android app's settings.
CLINIC_OPEN                = 09:00
CLINIC_CLOSE               = 21:00
CLINIC_OPEN_DAYS           = Mon,Tue,Wed,Thu,Fri,Sat
PEAK_WINDOWS               = 08:00-09:30, 12:45-14:15, 17:00-18:30, 20:00-22:00
```

`DRY_RUN = true` is the default and must stay that way until a batch of drafts has been read
and approved by a human. Flipping it to `false` is a deliberate act by the operator, never
something the Routine does to itself, and never something to suggest doing mid-run.

**### Coverage windows

The Routine replies **when nobody at the clinic is free to answer** — which is the opposite of a
business-hours filter:

| When | Active? | Why |
|---|---|---|
| Outside `CLINIC_OPEN`–`CLINIC_CLOSE` | Yes | Nobody is there |
| Days not in `CLINIC_OPEN_DAYS` | Yes, all day | Same |
| Inside a `PEAK_WINDOWS` entry | Yes | Team is with patients and can't get to WhatsApp |
| Any other time the clinic is open | **No** | Staff can see and answer it themselves |

With the values above that works out to **active 00:00–09:30, 12:45–14:15, 17:00–18:30,
20:00–24:00** on an open day, and quiet during 09:30–12:45, 14:15–17:00, 18:30–20:00.

The Android app applies this too, so in normal operation a quiet-window message never reaches
the webhook. Re-check it here anyway: a phone with a stale config, a manually fired Routine, or
a replayed request would otherwise reply over a staff member who is sitting right there. If the
message's `timestamp` falls in a quiet window, log `skipped_quiet_window` and stop.

Note the interaction with the staff-reply gate — during peak windows the team is *busy*, not
absent, so someone may still answer between the notification firing and this Routine reaching
step 4. Gate #3 is what catches that, and it matters more here than it would in an
off-hours-only design.

`ALLOW_LIST` is empty by design.** The operator's instruction is that unknown numbers should
receive a reply — a first-time patient messaging the clinic is the main case this system exists
for. An empty allow-list therefore means *proceed for any sender*; adding entries narrows it.
`BLOCK_LIST` is the real gate: put staff, suppliers, labs, family and the clinic's own numbers
there. Note the trade-off plainly: with an open allow-list, every safety decision rests on the
emergency gate and the staff-reply gate below, so treat those as load-bearing.

---

## 1. Trigger

API webhook. Payload:

```json
{
  "sender": "string",
  "chat_type": "individual|group",
  "group_name": "string|null",
  "message": "string",
  "timestamp": "ISO 8601",
  "app": "whatsapp|whatsapp_business"
}
```

Header: `X-Webhook-Secret: <shared secret>`.

## 2. Validate

- `X-Webhook-Secret` present and exactly equal to the configured secret → otherwise **reject
  with 401 and stop.** Do not log the payload of a rejected request beyond the fact that a
  rejection happened; an unauthenticated caller does not get to write to your log.
- Payload parses, and `sender` and `message` are both non-empty → otherwise log
  `malformed payload` and stop.
- `timestamp` is within the last 15 minutes → otherwise log `stale` and stop. A replayed or
  delayed message should not produce a reply that reads as if it just arrived.

## 3. Safety gate — do not reply if any of these hold

Evaluate all of them. Any single hit means **log and stop** — no reply, no partial reply, no
"checking with the team" holding message.

1. **`chat_type == "group"`.** Log only. There is no configuration that makes the Routine
   speak in a group chat.
2. **Dental or medical emergency signals** in the message. Match case-insensitively on, at
   minimum: `pain`, `painful`, `sakit`, `bleeding`, `blood`, `swelling`, `swollen`, `bengkak`,
   `trauma`, `broke`, `broken`, `knocked out`, `fell out`, `abscess`, `pus`, `fever`,
   `emergency`, `urgent`, `asap`, `can't sleep`, `cannot sleep`, `numb`. Log as
   **`needs human review — emergency signal`** and stop. A false positive here costs a slower
   reply; a false negative costs a patient in pain being answered by a bot. Bias to flagging,
   and do not try to reason your way past a match ("they only said the pain was mild") —
   the match itself is the decision.
3. **A staff member already replied in that thread within `STAFF_REPLY_WINDOW_MINUTES`.**
   This is checked on WhatsApp Web in step 4, not from the payload — the payload cannot know.
   If the last outbound message in the thread is newer than the window, a human is already
   handling this chat; stop.
4. **Sender is block-listed** (`BLOCK_LIST`), or `ALLOW_LIST` is non-empty and the sender is
   not on it. Match on name and on the last 8 digits of the number so
   `+60 12-345 6789` / `0123456789` / `60123456789` are treated as the same person.
5. **The message arrived in a quiet window** — see "Coverage windows" above. Log
   `skipped_quiet_window` and stop. Judge this on the payload's `timestamp`, not on the time the
   Routine happens to run.

## 4. Read the thread

1. Open Chrome and go to the WhatsApp Web Business tab (`web.whatsapp.com`). It is expected to
   be already logged in. **If it shows a QR code, is logged out, or the tab does not exist:**
   log `needs human review — WhatsApp Web not authenticated` and stop. Do not attempt to log
   in, scan anything, or ask for credentials.
2. Search for the chat using `sender`, then the number if the name does not resolve. Clear the
   search box before typing — WhatsApp appends to whatever is already in it, which silently
   produces a garbage query.
3. If zero chats match, or more than one plausibly matches and you cannot tell which:
   log `needs human review — chat not found` / `— ambiguous chat match` and stop.
4. Confirm the chat you opened actually contains the message from the payload. If the newest
   incoming message does not match, the notification and the thread have diverged — log
   `needs human review — message not found in thread` and stop.
5. Read the last `CONTEXT_MESSAGES_TO_READ` messages with `get_page_text` rather than reading a
   screenshot. Short replies render ambiguously in screenshots; the text extraction does not.
6. Now apply safety gate #3: find the newest **outbound** message and compare its timestamp
   against the window.
7. Note the chat's labels. If the thread carries a label the clinic uses to mean "handled by a
   human" (e.g. `DONE REVIEW`, or whatever the clinic adopts for this), treat it the way the
   `whatsapp-kpi-review-funnel` skill treats `DONE REVIEW`: the label is a more reliable signal
   than this one message, so stop.

## 5. Draft the reply

Source of truth is [`context/clinic-info.md`](context/clinic-info.md) — hours, branches,
services, prices policy, tone, escalation contacts. Read it every run; do not answer from
memory of a previous run.

Rules for the draft:

- **Two or three sentences.** WhatsApp, not email. No greeting block, no signature.
- **Match the clinic's tone** as described in `clinic-info.md`, and match the patient's
  language — if they wrote in Malay or Manglish, reply in kind rather than in formal English.
- **Never invent a fact.** No price, no appointment slot, no doctor's availability, no branch
  address, no treatment claim that is not written in `clinic-info.md`. If answering the actual
  question requires a fact you do not have, that is not a drafting problem to write around —
  it is uncertainty, so go to step 7.
- **Never confirm a booking.** The Routine has no access to the appointment book. It can say a
  human will confirm; it cannot say "you're booked for Friday 3pm."
- **No clinical advice.** Not even reassurance about symptoms. Anything symptom-shaped should
  already have been caught by the emergency gate; if it reaches here and still reads clinical,
  stop and flag.
- If the message is not a question and needs nothing — "ok", "thanks", a sticker's caption,
  a thumbs-up — send nothing. Log `no reply needed`. A bot that answers "thanks" with a
  paragraph is worse than one that stays quiet.

## 6. Send — or don't

**If `DRY_RUN = true`:** log the draft with `action: dry_run` and stop. Do not open the message
input, do not type into the chat. The point of dry-run mode is a batch of drafts the operator
can read without any of them having gone out.

**If `DRY_RUN = false`:**

1. Click the message input in the correct chat. Verify the chat header still shows the intended
   sender before typing — a background chat-list update can shift the open chat.
2. Type the reply and send.
3. Screenshot and confirm the message appears in the thread with a timestamp and a checkmark.
4. If the send cannot be confirmed, log `needs human review — send unconfirmed` with the draft
   text. Do **not** retry the send; a duplicate message to a patient is worse than a missing
   one, and the log tells a human exactly what to check.

## 7. Uncertainty and errors — the default branch

Anything not explicitly covered above resolves to: **send nothing, log
`needs human review`, stop.** Including: WhatsApp Web logged out, chat not found, ambiguous
match, unclear intent, a question needing a fact not in `clinic-info.md`, a Chrome or MCP tool
error, a message in a language you cannot reliably write back in, or simply not being confident
the reply is right.

This is not a failure mode, it is the intended behaviour. A message that ends up in the
human-review log has cost the clinic a few minutes. A confidently wrong auto-reply to a patient
costs more than that.

## 8. Log every run

Every trigger produces exactly one log row — including rejections, dry runs, no-reply-needed
and human-review flags. A run that leaves no trace is indistinguishable from a Routine that
silently stopped working.

Format and location: see [`logs/README.md`](logs/README.md). In short — append a row to
`WhatsApp_AutoReply_Log.md` in the `Clinic-Automation-Logs` folder on the user's Desktop, the
same folder and sync arrangement the `gbp-posts` and `gbp-qa-sweep` skills use (a scheduled
task syncs it to Google Drive; write to the Desktop folder, not to Drive directly).

## Output after each run

One line to the session, so a human skimming sees state without opening the log:

```
[HH:MM] <sender> — <action> — <reason or first 60 chars of the reply>
```

---

## How to install this as a Routine

1. Claude Code Desktop → Routines → New Routine → trigger type **Webhook**.
2. Paste sections 1–8 of this file as the Routine prompt, with the configuration block at the
   top.
3. Copy the generated webhook URL and its secret into the Android app's settings screen.
4. Make sure the Routine's environment has: Claude in Chrome with a signed-in WhatsApp Web
   Business tab, and access to the `Clinic-Automation-Logs` Desktop folder.
5. Fill in [`context/clinic-info.md`](context/clinic-info.md) before the first run. With it
   blank, every run correctly flags for human review and the system does nothing useful.
6. Leave `DRY_RUN = true`. Let it collect drafts for a few days, read them, then decide.

## Before you flip `DRY_RUN` to false

Read the risk note in the [top-level README](README.md#risk-note-whatsapp-terms-of-service).
Automating sends on a real WhatsApp Business number can get that number banned, and the number
is the clinic's asset.
