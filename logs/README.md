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
| `Reason` | Why this action — always filled in for anything other than `sent` |

### `Action` values

| Value | Meaning |
|---|---|
| `sent` | Reply sent and confirmed in the thread |
| `dry_run` | Draft produced, deliberately not sent (`DRY_RUN = true`) |
| `no_reply_needed` | Nothing to answer — "ok", "thanks", an emoji |
| `skipped_group` | Group chat; log-only by policy |
| `skipped_staff_replied` | A human answered in the thread within the staff-reply window |
| `skipped_blocked` | Sender on the block-list, or off a non-empty allow-list |
| `needs_human_review` | Flagged for a person. `Reason` says which gate fired |
| `rejected` | Bad shared secret, malformed payload, or stale timestamp |

### `Reason` values for `needs_human_review`

`emergency signal` · `chat not found` · `ambiguous chat match` ·
`message not found in thread` · `whatsapp web not authenticated` · `unclear intent` ·
`fact not in clinic-info` · `send unconfirmed` · `tool error`

## Example

```markdown
| Timestamp | Sender | App | Chat | Action | Incoming message | Reply | Reason |
|-----------|--------|-----|------|--------|------------------|-------|--------|
| 2026-07-25T09:14:02+08:00 | Ravi Kumar | whatsapp_business | individual | dry_run | Hi, are you open this Saturday? | Yes, we're open Saturday 9am–5pm at both branches. Want me to have the front desk hold a slot for you? | DRY_RUN enabled |
| 2026-07-25T09:31:47+08:00 | +60 12-345 6789 | whatsapp_business | individual | needs_human_review | My tooth has been aching since last night | | emergency signal |
| 2026-07-25T10:02:11+08:00 | Clinic Staff Group | whatsapp_business | group: PJ Front Desk | skipped_group | Anyone free to cover 3pm? | | group policy |
| 2026-07-25T10:20:05+08:00 | Siti | whatsapp_business | individual | no_reply_needed | ok thanks! | | nothing to answer |
```

## Retention

Not automated. Rows hold patient message text, so decide a retention period with the clinic and
archive or trim the file on that schedule. The Android app keeps nothing — this log is the only
durable record of message content in the whole system, which makes it the file that matters for
privacy.
