---
name: table-analysis
description: Analyse one structured table in the knowledge base (a spreadsheet-like document) with query_site_table — filter rows, count, sum, average, min/max over a column. Use when the question computes over the rows of a single table. Do not use for facts written in prose documents (doc-qa-cited), for comparisons across many separate documents (cross-doc-aggregation), or as a database query — the tool takes no SQL and evaluates no formulas.
version: "1"
---

# Table analysis (one structured table)

1. **Locate before computing.** Find the document first with
   `search_site_knowledge`, then use `query_site_table` with `op=list` to
   see the sheets and exact column headers. The `source_id` is the
   document's id from search or enumeration (e.g. `kb:14`). Column names
   must match a header exactly — never guess a header.
2. **Pick the tool by the shape of the question.** A fact stated in prose
   belongs to passage search. Anything that filters rows or computes over
   a column — count, sum, avg, min, max — belongs to `query_site_table`.
   Never eyeball a sum or a count from passages when the table tool can
   compute it exactly.
3. **Confirm the measure before running.** Decide which column, which
   unit, and which rows are in scope (the filter), and use the user's
   wording to choose between similar columns (e.g. gross vs net). State
   the column and filter you used in the answer, so the number can be
   checked. The tool does not evaluate formulas: values are the stored
   cell values, and if a column holds unevaluated formula results, say so
   rather than treating them as exact.
4. **Plan multi-step analyses.** If the analysis needs three or more
   operations (several filters, a comparison across row groups), add one
   todo per step first and work through them in order.
5. **Chart only real numbers.** When the user asks to *see* a trend, a
   comparison, or a proportion, and you already hold the exact values
   from tool output, call `render_chart` with those values copied from
   the tool result (bar for comparisons, line/area for trends, pie for
   proportions). Never invent or adjust values for a chart, and skip the
   chart when one or two values are clearer as a sentence. Note
   `render_chart` exists only in preview chat; elsewhere give the values
   as text or a small table.
6. **Answer with the working shown.** Give the result together with the
   column, the filter or row scope, and the source document citation, so
   the user can re-run the same query and land on the same number.
