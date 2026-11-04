---
name: deep-research
description: Research an open-ended topic across this site's knowledge base and synthesize a cited, structured answer in one pass — decompose the topic into sub-questions, search each one, check the gaps, then write it up. Use for broad explain / full-picture / compare-and-conclude questions that no single document answers. Do not use for one fact (doc-qa-cited), for set-level numbers or rankings across documents (cross-doc-aggregation), for drafting a long document interactively with the user (doc-coauthoring), or for conflicts between sources as the main problem (multi-source-conflict).
version: "1"
---

# Deep research (one topic, several documents, one synthesis)

A research question is open-ended: the answer is assembled from several
documents and does not exist anywhere as a single passage. The failure
modes are a shallow first-hit answer dressed as research, and a long
answer padded with general knowledge where the corpus ran out. Work in
this order:

1. **Decompose before searching.** Write the sub-question list first —
   the three to six factual questions the topic actually turns on
   ("what are the opening hours", "what does the warranty cover", "how
   does registration work"). Sub-questions must be answerable from
   documents; opinion or advice questions are reframed or dropped. For
   a topic needing several searches, plan with todos (at most 3 items:
   decompose and search, gap check, synthesize) and mark progress by
   completing items.
2. **Search per sub-question, separately.** One `search_site_knowledge`
   call per sub-question — a single blended query blurs which source
   backs which claim. When a sub-question is about a document kind,
   enumerate it with `list_knowledge_documents`; when the evidence sits
   in a structured table, use `query_site_table` with the enumerated
   document's `source_id`.
3. **Stop rule.** After the planned sub-questions are searched, allow at
   most one extra round for gaps the first round exposed. Do not keep
   searching until the run budget is gone — a run that spends
   everything on retrieval has nothing left to write with, and ten
   searches rarely beat four aimed ones.
4. **Gap check before writing.** Mark each sub-question covered,
   partial, or not found. A not-found sub-question appears in the
   write-up as an explicit line (「未查到」/ not covered by the site
   materials) — never silently dropped, and never filled in from
   general knowledge. Partial coverage is stated as partial.
5. **Synthesize by sub-question.** Structure the answer by the
   sub-questions (headings or labeled parts), connect the facts into a
   readable whole instead of pasting passages end to end, and put the
   `[cN]` label at each site-specific claim. Where two sources state
   the same fact differently, present both versions with their labels
   per the multi-source-conflict playbook — do not blend them into one
   number.
6. **Close with coverage.** One short line: which sub-questions were
   answered from how many documents, and which remain open. Length
   follows the topic — a research answer is longer than a fact answer,
   but every paragraph must earn its place with a citation or a
   connection between cited facts.

Hard constraints:

- Every number, name, date, and rule in the synthesis comes from this
  turn's tool output with its `[cN]` label. General knowledge may frame
  a sentence, never supply a site fact.
- A set-level number inside the research (a total, a ranking) is only
  as good as its coverage: if it matters, work it with the
  cross-doc-aggregation discipline (enumerate the full set first) or
  report it as uncomputed — never sum the fragments a search happened
  to return.
- If the topic turns out to hinge on one document after all, stop and
  answer directly; research ceremony around a single fact helps no one.
