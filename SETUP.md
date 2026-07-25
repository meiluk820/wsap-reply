# Setup walkthrough

Written to be followed without knowing anything about Android development. Do the parts in
order — each one proves the previous one worked before you build on it.

Two things nobody can do for you, including Claude: tapping the toggles in Android's settings
(Android forbids apps from granting these to themselves, by design), and creating the Routine in
Claude Code Desktop. Everything else is either automated or copy-paste.

---

# Part 1 — Prove the phone detects messages

**Goal:** see a real WhatsApp message turn into a webhook POST. Nothing else is wired up yet, on
purpose — if something is wrong, this is where it shows up, with nothing else to confuse it.

Time: about 20 minutes. You need the clinic phone and one other phone to message it from.

## 1.1 Get a free test URL

On any device, open **https://webhook.site**.

It immediately shows a page with **"Your unique URL"** near the top — something like
`https://webhook.site/8f2c1a4e-...`. Tap the copy button next to it.

Leave this page open. Every request the app sends will appear here within a second or two.

> This is a temporary third-party inbox, so during this test **only send messages from your own
> second phone.** Don't leave the app pointed here waiting for real patient messages — anyone
> with the URL can read what arrives.

### If the webhook.site page looks broken

If you see `{{ ... }}` placeholders on the page instead of a real URL — e.g. "Your unique URL"
reading `{{ useSubdomain ? getUrlSubdomain() : getUrl() }}` — the page's JavaScript didn't run.
Any warning banners you can see in that state are unrendered template text too, **not** real
limits on your URL. An ad blocker or script blocker is the usual cause.

1. Hard refresh: `Ctrl+Shift+R`.
2. Open it in a private window, where extensions are off: `Ctrl+Shift+N`.
3. Turn your ad blocker off for the site and reload.

Still broken? Use **https://requestcatcher.com** instead — a simpler page with less to go wrong.
It gives you `https://something.requestcatcher.com`, and requests show up as they arrive. Use that
URL everywhere this guide says webhook.site.

### Fallback: prove detection with no third-party site at all

If no inbox service will cooperate, you can still prove the phone is detecting messages, using
only the app:

1. Set **Webhook URL** to `https://example.com/nope` and any secret. Save. Forwarding ON.
2. Send a test message from your second phone.
3. Open the app and look at **Delivery failures** at the bottom.

An entry appearing there — timestamp, a partly-hidden sender like `Ra…ar`, and `HTTP 404` — proves
the notification was **detected, parsed, and a send was attempted**. That is the part most likely
to go wrong, and this confirms it without any external service.

What it can't show you is the JSON itself, so the parser fields stay unverified until you get a
real inbox working. Worth doing anyway: it splits "the phone isn't detecting" from "the endpoint
isn't receiving", which are very different problems.

## 1.2 Install the app on the clinic phone

On the **clinic phone's browser**, open:

**https://github.com/meiluk820/wsap-reply/releases/tag/latest-debug**

1. Tap **`app-debug.apk`** in the Assets list. It downloads.
2. Open the download. Android will say installs from this source aren't allowed — tap
   **Settings**, turn on **Allow from this source**, then go back and tap **Install**.
3. If Play Protect warns about an unknown app, choose **Install anyway**. This is expected: the
   app is deliberately not distributed through the Play Store (see
   [android-detector/README.md](android-detector/README.md#install-this-is-a-sideload-not-a-play-store-app)).
4. Open **WA Notify Bridge**. Allow notifications when asked.

## 1.3 Grant the two permissions

The app's first card shows both, with GRANTED / NOT GRANTED next to each. **You have to do these
by hand — no app can grant them to itself.**

**Notification access** — tap **Open notification access settings** in the app. Find
**WA Notify Bridge** in the list, turn it on, and confirm the warning dialog. Come back to the
app; the card should now read **GRANTED**.

> If the list doesn't show the app, close and reopen the settings screen. Some phones cache it.

**Battery optimisation** — tap **Open battery settings**, choose **Allow** / **Unrestricted** /
**Don't optimise**. The card should read **EXEMPT**.

> **Xiaomi / Redmi / Poco, Oppo / Realme, Vivo, Huawei:** also open your phone's Settings → Apps →
> WA Notify Bridge → turn on **Autostart**, and in the recent-apps screen press-and-hold the app
> and **lock** it. These skins ignore the standard exemption and will kill the app within a day
> or two without this.

## 1.4 Point it at the test URL

In the app:

1. **Webhook URL** — paste the webhook.site URL from step 1.1.
2. **Shared secret** — type anything for now, e.g. `test123`.
3. Tap **Save settings**.
4. Turn **Forwarding** ON at the top.

A quiet **"Auto-reply bridge active"** notification appears and stays. That notification is what
keeps the app alive — don't swipe it away or turn off its channel.

## 1.5 Test it

Check the schedule first: forwarding only runs outside 09:00–21:00, or during
**08:00–09:30, 12:45–14:15, 17:00–18:30, 20:00–22:00**. If you're testing in a quiet window
(say 3pm), temporarily switch **Use the schedule** OFF, and remember to switch it back on after.

From your second phone, send the clinic number:

| Send this | Expect on webhook.site |
|---|---|
| `Hi, are you open tomorrow?` | One request, `"message": "Hi, are you open tomorrow?"` |
| Two messages back to back | **Two** requests, not four — this checks de-duplication |
| A photo **with** a caption | One request containing the caption |
| A photo with **no** caption | **Nothing.** Correct behaviour, not a failure |

Open one request on webhook.site and check the JSON: `sender` should be the contact name or
number, `message` the text, `chat_type` should read `individual`.

### If nothing arrives

Work down this list — it's ordered by how often each one is the cause:

1. **Is the chat open on the clinic phone's screen?** WhatsApp posts no notification for a chat
   you're currently looking at, so the app sees nothing. Close WhatsApp completely and resend.
2. **Is the sender muted** in WhatsApp on the clinic phone? Muted chats post no notification.
3. **Notification access** — recheck the card says GRANTED.
4. **Schedule** — are you inside a quiet window? Switch the schedule off and retry.
5. **Check the app's Delivery failures list** at the bottom of the settings screen. If entries
   appear there, the message *was* detected and the sending failed — that's a URL or network
   problem, not a detection problem, which is useful to know.

**If the JSON arrives but a field looks wrong** — wrong sender, truncated or empty message, a
group marked `individual` — copy one full request from webhook.site and send it to Claude. That's
a parser fix, and WhatsApp's notification format genuinely varies between versions, so this is a
realistic outcome rather than a sign anything is broken.

---

# Part 2 — Fill in the clinic facts

Open [`context/clinic-info.md`](context/clinic-info.md). Everything marked `TODO` is a fact only
you have. The Routine is only allowed to tell patients things written in this file — anything
missing gets flagged for a human instead of guessed, so an unfinished file is safe but not
useful.

Highest value first:

1. **SS2 Taman Paramount's opening and closing times.** It's the only branch the Routine may
   answer for, and right now it can say "open every day" but not "we close at 9".
2. **Services offered** — otherwise every "do you do implants?" gets flagged.
3. **Price policy** — most clinics choose "never quote a price, the front desk follows up".
   Write that sentence in and the Routine will respect it.
4. **Tone** — what language to reply in, how formal, emoji or not.

You can also just tell Claude these in chat and have it write them into the file properly.

---

# Part 3 — Create the Routine

This part is in the Claude Code Desktop app, on the computer that has the clinic's Chrome with
WhatsApp Web already logged in.

1. Open **Claude Code Desktop → Routines → New Routine**.
2. Trigger type: **Webhook**.
3. Open [`ROUTINE.md`](ROUTINE.md) and paste **everything from the "Configuration" heading down to
   the end of section 8** into the Routine's prompt box.
4. Save. Desktop shows you a **webhook URL and a secret** — copy both.
5. On the clinic phone, in WA Notify Bridge: replace the webhook.site URL with the real one,
   replace `test123` with the real secret, **Save settings**.
6. Confirm the Routine's environment has: Chrome signed in to WhatsApp Web Business, and access to
   the `Clinic-Automation-Logs` folder on the Desktop.

**Leave `DRY_RUN = true`.** It is already true in the file. In this mode the Routine reads chats
and writes drafts to the log but sends nothing to patients — which is the whole point of the next
part.

One exception worth knowing: the **escalation ping to Andy still sends** in dry-run. Dry-run is
about not messaging patients; Andy is staff, and a flagged message is time-sensitive either way.

---

# Part 4 — Read the drafts before going live

For a few days, read `Clinic-Automation-Logs/WhatsApp_AutoReply_Log.md`. One row per message.

Ask of each `dry_run` row: **would I have sent that?**

- Drafts read well → consider `DRY_RUN = false`. Read the
  [ToS risk note](README.md#risk-note-whatsapp-terms-of-service) again first; automated sending
  can get the number banned, and the number is the clinic's patient channel.
- Drafts are close but off in tone or facts → that's a `clinic-info.md` problem, not a Routine
  problem. Fix the file and keep watching.
- Lots of `needs_human_review` rows → look at the reasons. Usually it means a fact is missing
  from `clinic-info.md`, which is the system telling you exactly what to write down.

Staying in dry-run permanently is a legitimate choice. A human copying an approved draft into
WhatsApp is quick, and it carries none of the account risk.
