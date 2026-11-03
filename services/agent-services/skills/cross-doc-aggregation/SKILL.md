---
name: cross-doc-aggregation
description: Aggregate across ALL knowledge documents of one kind — highest, lowest, total, average, count, or ranking (e.g. the lowest-pay month across every payslip, the largest invoice). Use when the answer is only correct if the whole set was covered. Do not use for a fact inside one document (doc-qa-cited), for row math inside one structured table (table-analysis), or for summarizing one document (summarize-transform).
version: "1"
---

# Cross-document aggregation (set-level questions)

A set-level question asks about a whole class of documents: the highest,
lowest, total, average, count, or ranking across all of them. Passage search
alone can never prove coverage of the set — a top-k result silently omits
documents. Work the full set, in this order:

1. **Plan with todos first — cheaply.** Before any other tool call, add
   the phase todos in a single round of calls: at most 3 items (enumerate
   the set, extract the value from each document, compare and answer).
   Progress is marked by completing each item as its phase finishes —
   do not spend extra calls marking items in progress. Status tracking
   must never crowd out the work itself: on a large set, extraction
   comes first and optional status updates are skipped.
2. **Enumerate the full set.** Call `list_knowledge_documents` with the
   keyword / format / date filters that match the document kind, paging
   with `offset` until `truncated` is false. The reported `total` is the
   size of the set you must cover.
3. **Fix one measure before extracting.** Choose a single field with a
   single definition and use it for every document — never mix measures
   midway. Defaults when the user did not specify: pay means net pay
   (take-home, 实发); an amount means the document's own stated total.
   If the user named a measure (e.g. gross, 税前), use that one instead.
4. **Extract one value per document — in parallel batches.** For each
   enumerated document, get its value for the chosen measure —
   `search_site_knowledge` aimed at that document, or `query_site_table`
   when the document is a structured table (its `sourceId` from the
   enumeration is the table tool's `source_id`). Issue the extraction
   calls for several documents together in one round of parallel calls
   instead of one serial round-trip per document — every round-trip
   costs a model request against the run limits. Record each value with
   its citation. If a document's value cannot be found, mark that
   document as missing — never guess it.
5. **Compare from the written list.** Write out every document with its
   value first, then take the highest / lowest / total / average / ranking
   from that list only. This is not adding up search fragments: the total
   is computed over a fully enumerated set, one extracted value per
   document.
6. **Answer with coverage and measure stated.** Lead with the result,
   then the per-document list with citations, then the coverage ("all 4
   payslips, Jan–Apr 2026") and the measure used ("by net pay"). Where a
   different measure would change the result, add one line noting it
   exists (e.g. "by gross pay the lowest month differs") — do not end by
   asking the user which measure to use when a default above applies.

Degraded path — if `list_knowledge_documents` is unavailable, errors, or
cannot return the whole set, or some documents' values stay missing: say
plainly that the full set could not be covered and why, name what is
missing, and present any partial values as partial only. Never present a
partial result as the set-level answer, and never invent the missing
values.
