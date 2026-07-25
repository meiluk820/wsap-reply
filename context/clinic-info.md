# Clinic info — the Routine's only source of truth

**Status: PLACEHOLDER. Fill this in before the first live run.**

Everything the Routine is allowed to state to a patient must be written here. If a fact is not
in this file, the Routine treats the message as uncertain and flags it for a human instead of
guessing — that behaviour is intentional, so an incomplete file is safe but useless.

Delete the `TODO` markers as you fill each section. Keep the headings; the Routine reads by
section.

---

## Business hours

This WhatsApp number is a **general booking line for all branches**, staffed **9:00am – 9:00pm,
seven days a week including Sundays**. Some branch is open every day, so replies and staff cover
run as usual at weekends.

| Day | Line staffed |
|---|---|
| Monday – Sunday | 9:00am – 9:00pm |

**Per-branch opening hours may differ from the line's hours** — a patient asking "are you open
today?" means *their* branch, not the phone line. Fill in each branch's own hours below; until
those are filled in, the Routine can't answer that question and will flag it for a human, which
is the correct behaviour rather than a guess.

Public holidays: TODO
Lunch break: TODO — the 12:45–2:15pm peak window suggests the clinic stays open through lunch;
say so explicitly if it does, since "are you open at lunch?" is a common question.

<!-- Per branch if they differ — add a row set per branch and say which is which. -->

### Peak hours (for reference, not for patients)

8:00–9:30am · 12:45–2:15pm · 5:00–6:30pm · 8:00–10:00pm. These are when the team can't get to
WhatsApp, and are why the bridge covers those windows. Never quote these to a patient as "busy
times" unless you decide you want that.

## Branches

<!-- TODO. One block per branch. The Routine may state address, area and phone; nothing else. -->

### TODO Branch name
- Area: TODO
- Address: TODO
- Phone: TODO
- Parking / how to find it: TODO
- Hours if different from above: TODO

## Services offered

<!-- TODO. List only what the clinic actually does. The Routine will not mention a treatment
     that isn't listed here, which is what stops it promising something you don't offer. -->

- TODO

Not offered / referred out: TODO

## Prices

<!-- Decide the POLICY, not just the numbers. Most clinics prefer the Routine never quotes a
     price. If that's you, write exactly that and leave the table out — the Routine will then
     flag price questions for a human. -->

Policy: TODO — e.g. "Never quote prices. Say a team member will follow up with a quote."

## Tone of voice

<!-- TODO. Be concrete; this is what the drafts will sound like. -->

- Language(s) to reply in: TODO (e.g. English, Malay, follow the patient's lead)
- Formality: TODO (e.g. warm and casual, first-name, no honorifics)
- Emoji: TODO (e.g. sparingly, never more than one)
- Words/phrases to avoid: TODO
- Example of a good reply: TODO
- Example of a reply that's too stiff / too salesy: TODO

## Booking

<!-- The Routine cannot see the appointment book and must never confirm a slot. What it CAN do
     is tell the patient what happens next. -->

What the Routine may say about booking: TODO — e.g. "Someone from the front desk will confirm
your slot shortly."
Booking link, if any: TODO

## Escalation contacts

<!-- TODO. Who picks up a flagged message, and how fast. The Routine doesn't message these
     people itself — it writes to the log — but knowing the path matters when you decide what
     to flag. -->

Anything the Routine flags as `needs_human_review` triggers a WhatsApp message to
**Andy, +60 18-288 8972** — immediately for suspected emergencies, rate-limited to one ping per
chat per 20 minutes otherwise. See [ROUTINE.md](../ROUTINE.md#escalating-to-a-human).

Andy's number is on the block-list in both the app and the Routine, so his replies are never
treated as patient messages. Don't remove that entry.

- Emergency / clinical flags go to: Andy (+60 18-288 8972) by WhatsApp
- General human-review queue watched by: Andy, same channel
- Out-of-hours: **same contact, any hour.** Pings fire at 3am as readily as at 3pm; Andy attends
  to them when he's available. This is a deliberate choice — the alternative was queuing overnight
  flags until morning, which would delay exactly the emergency messages the flag exists to catch.
  The trade-off accepted is that a flagged message can ping a sleeping phone with no one acting on
  it for hours.

## Standing exceptions

<!-- TODO. Specific numbers, names or situations the Routine should always skip, beyond the
     block-list. e.g. "Never auto-reply to the number ending 4095 — long-running complaint." -->

- TODO
