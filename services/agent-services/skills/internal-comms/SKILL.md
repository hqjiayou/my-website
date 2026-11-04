---
name: internal-comms
description: Write short internal communications grounded in this site's knowledge base — announcements, notices, status updates, progress/plans/problems updates, FAQ short answers, and change or incident notices for staff, members, or customers. Use when the user wants a short announcement-style text drafted from site material. Do not use for long documents drafted section by section together with the user (doc-coauthoring), for summarizing or translating an existing document (summarize-transform), or for answering a factual question directly instead of drafting a text (doc-qa-cited).
version: "1"
license: Apache-2.0
metadata:
  source: "Adapted from anthropics/skills internal-comms (Apache-2.0); rewritten for this platform's tools and short-form site announcements"
---

# Internal communications (short-form, knowledge-grounded)

Write the short texts an organization actually sends: an announcement, a
notice, a status update, a round of FAQ answers. Short is the point —
one screen, the key fact first, drafted in the conversation. This
platform has no send or publish channel: the deliverable is the draft
text in chat, never a claim that anything was posted or emailed.

## Hard constraints (always)

1. **Ground every fact.** Dates, times, prices, rules, and procedures in
   the draft must appear in this turn's tool output and carry their
   `[cN]` label. Never invent a label.
2. **Never invent the news.** A notice announces a change — the new
   time, the new rule, the effective date. If that detail is not in the
   retrieved material and the user did not supply it, it does not go in
   as fact: either ask for it, or draft around it with a visible
   placeholder (e.g. `[TODO: effective date not in knowledge base]`)
   and name the gap after the draft. A fluent announcement of an
   unconfirmed change is a fabrication with good formatting.
3. **Search before drafting.** Use `search_site_knowledge` for every
   fact the text carries; use `list_knowledge_documents` when the text
   summarizes a document set. Prefer site passages over general
   knowledge for anything the corpus might cover.
4. **Short means short.** Cut background, history, and justification
   unless the user asked for them. If the text is growing sections and
   an outline, it has left this playbook — that is doc-coauthoring.

## Workflow

1. **Identify the type and the reader.** Announcement / notice, status
   update, progress-plans-problems update, FAQ answers, or incident /
   change notice. Fix who reads it and the one thing they must know or
   do. If the type or audience is genuinely unclear, ask one clarifying
   question (audience, purpose, tone) — one, not a questionnaire.
2. **Gather the material.** Retrieve the passages behind every fact the
   draft will state (step 2 of the constraints). Note what is missing
   before drafting, not after.
3. **Draft in the type's shape:**
   - **Announcement / notice:** a headline line; what is happening or
     changing; from when / for whom; what the reader must do, if
     anything; where the full detail lives. Facts first, courtesy last.
   - **Status / progress update:** what is done, what is next, what is
     blocked — labeled parts (Progress / Plans / Problems when the user
     wants that shape), one to three sentences each, matter-of-fact,
     numbers where the material has them.
   - **FAQ answers:** one question per entry in a single sentence, the
     answer in one or two sentences grounded in official material. A
     question the material cannot answer is listed as unanswered, not
     drafted around.
   - **Incident / change notice:** what happened, who is affected, the
     current state, the next update point — only states the material
     supports; do not promise a resolution time that no source states.
4. **Deliver and disclose.** Post the draft, then one short line naming
   any placeholder or gap left in it. Offer wording tweaks; do not
   offer to send it anywhere.

## Tips

- Active voice, plain words; the reader is busy.
- Keep the site's own names, times, and terms exactly as the sources
  state them — a notice is the worst place for a paraphrase drift.
- If the user hands you an existing text and asks to condense or
  translate it, that is summarize-transform, not this playbook.
