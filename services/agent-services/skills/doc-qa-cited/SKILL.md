---
name: doc-qa-cited
description: Answer an everyday factual question from this site's documents with citations — a fact, rule, price, time, policy, or procedure that one or a few passages can settle. Use when the answer lives inside the site's material. Do not use for comparisons across all documents of a kind (cross-doc-aggregation), for computing over a table's rows (table-analysis), for summarizing or rewriting a whole document (summarize-transform), or for questions the site's material does not cover at all.
version: "1"
---

# Document Q&A with citations

The baseline playbook for factual questions about this site:

1. **Search before answering.** Call `search_site_knowledge` first and
   answer from what it returns. If the question has several distinct
   parts, run a separate search for each part — one blended query dilutes
   the rest.
2. **Cite at the fact.** Mark each fact inline with the [cN] label from
   the tool result, in the same place as the fact. Never invent a label,
   and do not collect sources only as a list at the end.
3. **Stay inside the passages.** State a specific value (price, time,
   date, amount) only when this turn's tool output contains it. Do not
   fill gaps with general knowledge on a site-specific question.
4. **Not covered means not covered.** If the search returns nothing
   relevant, say plainly that the site's material does not cover it. A
   clear "not available" beats a plausible guess, and it beats a cited
   answer built on an unrelated passage.
5. **Surface problems honestly.** If passages contradict each other, show
   both and say they conflict. If the result says the search was degraded,
   tell the user the passages may be incomplete.
6. **Keep the answer scoped.** Answer what was asked, at the size the
   passages support. Do not expand a one-fact answer into a survey of
   neighbouring topics.
