---
name: doc-coauthoring
description: Co-author a long document or how-to from this site's knowledge base — gather corpus context, draft section by section in chat, cite facts with [cN], then reader-check the draft. Use when the user wants to write, draft, or collaborate on a guide, handbook, FAQ, policy write-up, or similar long-form text grounded in site documents. Do not use for answering one factual question (doc-qa-cited), for summarizing or translating an existing document without drafting new structure (summarize-transform), for set-level comparisons across documents (cross-doc-aggregation), or for row math inside one table (table-analysis).
version: "1"
license: Apache-2.0
metadata:
  source: "Adapted from anthropics/skills doc-coauthoring (Apache-2.0); rewritten for this platform's tools"
---

# Knowledge-grounded document co-authoring

Guide the user through a structured workflow for drafting a long document
that is grounded in this site's knowledge base. Act as an active guide
through three stages: Context Gathering, Refinement & Structure, and
Reader Testing. Deliver the draft in the conversation — this platform has
no file-write or download channel for generated documents.

Offer the workflow when the user wants to write or co-author a substantial
doc (guide, handbook, FAQ, policy, onboarding note, and similar). Explain
the three stages briefly and ask whether they want the structured path or
prefer freeform. If they decline, work freeform; if they accept, start
Stage 1.

## Hard constraints (always)

1. **Ground every site-specific fact.** Numbers, names, dates, prices,
   procedures, and policy text that claim to come from this site must
   appear in this turn's tool output. Mark each such fact inline with its
   `[cN]` label from the tool result. Never invent a label.
2. **Search before inventing.** Use `search_site_knowledge` (and
   `list_knowledge_documents` when you need coverage of a document set)
   to gather material. Prefer site passages over general knowledge for
   anything the corpus might cover.
3. **Honest gaps.** If the knowledge base does not cover a point the draft
   needs, say so plainly and leave a visible placeholder (e.g.
   `[TODO: not in knowledge base]`) instead of filling with guesses.
4. **No fabricated totals.** Passage search cannot prove a set-wide total,
   count, or ranking. For set-level numbers, either stay qualitative or
   hand off to the aggregation / table playbooks — do not sum fragments.
5. **Draft in chat only.** Produce and revise the document as markdown in
   the conversation. Do not claim you wrote a file, created an artifact,
   or uploaded anything.

## Stage 1: Context Gathering

**Goal:** Close the gap between what the user knows and what the draft
needs, and pull relevant site material early.

### Opening questions

Ask briefly (they may answer in shorthand):

1. What type of document is this? (guide, FAQ, policy, handbook, …)
2. Who is the primary audience?
3. What should a reader be able to do after reading it?
4. Any required section list or length constraint?
5. Which topics or document kinds in the knowledge base should we draw on?

### Pull corpus context

Once the user names topics or keywords:

1. Call `list_knowledge_documents` with filters that match those topics
   when you need to know what exists; page with `offset` until
   `truncated` is false when coverage matters.
2. Call `search_site_knowledge` for each distinct topic or section you
   expect to draft — separate searches beat one blended query.
3. When a source is a structured table and you need exact field values,
   use `query_site_table` after confirming sheet and headers (list →
   filter/aggregate). State values only when they appear in tool output.

Track what you learned and what is still missing. Ask clarifying
questions when basics are unclear; stop when you can discuss trade-offs
without needing the domain explained again.

**Exit:** Ask whether to add more context or move to drafting. On go,
proceed to Stage 2.

## Stage 2: Refinement & Structure

**Goal:** Agree a section outline, then draft and refine one section at a
time in chat.

### Plan with todos

Before drafting, add phase todos in one round of calls — at most 3 items
(e.g. outline, draft sections, reader check). Complete each item as its
phase finishes; skip optional in-progress status updates when the budget
is better spent on retrieval and drafting.

### Outline

- If the user already has a structure, confirm it.
- Otherwise propose 3–6 sections suited to the doc type and audience.
- Ask which section to start with; prefer the section with the most
  unknowns (often the core procedure or decision). Leave summary-style
  sections for last.

Post the agreed outline as a markdown scaffold with short placeholders
like `[To be written]` for each section.

### Per section

For each section:

1. **Clarify.** Ask a short list of what must appear in this section.
2. **Ground.** Re-search or re-read the relevant passages before writing
   claims. Cite with `[cN]` at the fact.
3. **Draft.** Replace that section's placeholder with the drafted text in
   the conversation (show the updated full draft or a clear section
   update — whichever is easier to review).
4. **Curate.** Ask what to keep, cut, or combine; apply surgical edits
   from feedback without inventing new site facts.
5. **Gap check.** Ask if anything important is missing; if the corpus
   lacks it, mark the gap explicitly.

When most sections are done, re-read the whole draft for flow,
contradictions, redundancy, and unsupported claims. Fix those before
Stage 3.

## Stage 3: Reader Testing

**Goal:** Catch blind spots — content that authors understand but readers
will misread — before treating the draft as done.

1. Predict 5–10 questions a real reader would ask of this document.
2. For each question, check whether the draft alone answers it. Prefer
   answering from the draft text; if a check needs a fresh pair of eyes
   and `delegate_task` is available, delegate a self-contained prompt that
   includes only the draft and one question (the sub-agent does not see
   this conversation). If `delegate_task` is not available, ask the user
   to open a new chat, paste the draft, and ask those questions there.
3. Also check ambiguity, hidden assumptions, and internal contradictions.
4. Fix gaps in Stage 2 style; re-test until answers stay consistent.

## Final review

When reader testing passes:

1. Ask the user to do a final read — they own the document.
2. Remind them to verify every number, name, and procedure against the
   cited passages.
3. Offer one more pass if they want it; otherwise stop with the final
   markdown draft in chat.

## Tips

- Be direct and procedural; give the user agency to skip or speed up a
  stage.
- Do not sell the workflow — execute it.
- Keep site identity and business claims inside tool-backed passages.
- If the request collapses to a single fact lookup, stop this playbook
  and answer with the document Q&A pattern instead.
