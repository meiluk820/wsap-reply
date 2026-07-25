# Clinic info — the Routine's only source of truth

**Status: PLACEHOLDER. Fill this in before the first live run.**

Everything the Routine is allowed to state to a patient must be written here. If a fact is not
in this file, the Routine treats the message as uncertain and flags it for a human instead of
guessing — that behaviour is intentional, so an incomplete file is safe but useless.

Delete the `TODO` markers as you fill each section. Keep the headings; the Routine reads by
section.

---

## Business hours

Opening hours are **9:00am – 9:00pm**, the same on every day the clinic is open. Confirm the days
below — Mon–Sat is an assumption, and it also drives the bridge's schedule (on a day marked
closed, the bridge forwards for the full 24 hours).

| Day | Open | Close |
|---|---|---|
| Monday | 9:00am | 9:00pm |
| Tuesday | 9:00am | 9:00pm |
| Wednesday | 9:00am | 9:00pm |
| Thursday | 9:00am | 9:00pm |
| Friday | 9:00am | 9:00pm |
| Saturday | 9:00am | 9:00pm |
| Sunday | TODO — closed? | TODO |

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

- Emergency / clinical flags go to: TODO
- General human-review queue watched by: TODO
- Out-of-hours: TODO

## Standing exceptions

<!-- TODO. Specific numbers, names or situations the Routine should always skip, beyond the
     block-list. e.g. "Never auto-reply to the number ending 4095 — long-running complaint." -->

- TODO
