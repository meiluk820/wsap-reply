# Log format

## Where the log actually lives

**Not in this folder.** The canonical file is:

```
<Desktop>/Clinic-Automation-Logs/WhatsApp_AutoReply_Log.md
```

On Windows the Desktop is often `C:\Users\<user>\OneDrive\Desktop`. That folder is the same one
the `gbp-posts`, `gbp-qa-sweep` and `gbp-competitor-audit` skills use: a scheduled task syncs it
to Google Drive, so the Routine writes to the **Desktop folder** and never to Drive directly.
Request folder access if it isn't connected; create the log file if it's missing.

This `/logs` directory exists to hold the format spec and a template — not patient data.
`.gitignore` excludes `logs/*.md` other than this file and any `*.template.md`, so a stray
real log can't be committed by accident.

## Row format

One row per webhook trigger, appended. Matches the pipe-table convention of the existing
`GBP_Posts_Log.md` and `GBP_QA_Log.md`.

```
| Timestamp | Sender | App | Chat | Action | Incoming message | Reply | Reason |
|-----------|--------|-----|------|--------|------------------|-------|--------|
```

| Column | Contents |
|---|---|
| `Timestamp` | ISO 8601 local time, from the payload — the message's time, not the log-write time |
| `Sender` | Name if the chat is saved, otherwise the number |
| `App` | `whatsapp` or `whatsapp_business` |
| `Chat` | `individual`, or `group: <name>` |
| `Action` | One of the values below |
| `Incoming message` | Verbatim, truncated to 200 chars, newlines as `⏎` so the row stays a row |
| `Reply` | The reply sent, or the draft in dry-run mode; empty when nothing was drafted |
| `Reason` | Why this action. Always filled in for anything other than a plain `sent`; on an overnight send, records the pause used, e.g. `delayed 94s (overnight)` |

### `Action` values

| Value | Meaning |
|---|---|
| `sent` | Reply sent and confirmed in the thread |
| `dry_run` | Draft produced, deliberately not sent (`DRY_RUN = true`) |
| `no_reply_needed` | Nothing to answer — "ok", "thanks", an emoji |
| `asked_which_branch` | A timing question arrived without a branch; sent the clarifying question. May legitimately have no follow-up row — see ROUTINE.md |
| `skipped_group` | Group chat; log-only by policy |
| `skipped_staff_replied` | A human answered in the thread within the staff-reply window — including during the overnight send delay |
| `skipped_blocked` | Sender on the block-list, or off a non-empty allow-list |
| `skipped_quiet_window` | Arrived during a calm stretch of the working day; staff handle it |
| `needs_human_review` | Flagged for a person, and pinged to the escalation contact. `Reason` says which gate fired, and notes whether the ping went out (`escalated`) or was suppressed as a repeat (`escalation suppressed — repeat within 20min`) |
| `rejected` | Bad shared secret, malformed payload, or stale timestamp |
| `escalation_failed` | The WhatsApp ping to the escalation contact could not be sent. The underlying row is still logged separately |

### `Reason` values for `needs_human_review`

`emergency signal` · `chat not found` · `ambiguous chat match` ·
`message not found in thread` · `whatsapp web not authenticated` · `unclear intent` ·
`fact not in clinic-info` · `branch timing — team replies` · `send unconfirmed` · `tool error`

## Example

```markdown
| Timestamp | Sender | App | Chat | Action | Incoming message | Reply | Reason |
|-----------|--------|-----|------|--------|------------------|-------|--------|
| 2026-07-25T09:14:02+08:00 | Ravi Kumar | whatsapp_business | individual | dry_run | Hi, are you open this Saturday? | Yes, we're open Saturday 9am–5pm at both branches. Want me to have the front desk hold a slot for you? | DRY_RUN enabled |
| 2026-07-25T09:31:47+08:00 | +60 12-345 6789 | whatsapp_business | individual | needs_human_review | My tooth has been aching since last night | | emergency signal — escalated to Andy |
| 2026-07-25T10:02:11+08:00 | Clinic Staff Group | whatsapp_business | group: PJ Front Desk | skipped_group | Anyone free to cover 3pm? | | group policy |
| 2026-07-25T10:20:05+08:00 | Siti | whatsapp_business | individual | no_reply_needed | ok thanks! | | nothing to answer |
| 2026-07-25T20:14:20+08:00 | Wei Ling | whatsapp_business | individual | asked_which_branch | are you open tomorrow? | Sure — which branch are you asking about? SS2 Taman Paramount, Ara Damansara, or Putra Heights? | branch unknown |
| 2026-07-25T20:16:03+08:00 | Wei Ling | whatsapp_business | individual | needs_human_review | ara damansara | | branch timing — team replies — escalated to Andy |
| 2026-07-26T02:41:33+08:00 | +60 11-222 3344 | whatsapp_business | individual | sent | Hi, do you take walk-ins? | We do, but Saturdays fill up fast — the front desk will message you in the morning to lock in a time. | delayed 94s (overnight) |
```

## Retention

Not automated. Rows hold patient message text, so decide a retention period with the clinic and
archive or trim the file on that schedule. The Android app keeps nothing — this log is the only
durable record of message content in the whole system, which makes it the file that matters for
privacy.
