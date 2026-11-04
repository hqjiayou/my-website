---
name: extract-to-table
description: Extract structured fields from unstructured documents — invoices, contracts, notices, letters — into a markdown table with one row per document, one column per field, and every value traceable to its source. Use when the user wants fields pulled out of prose documents and laid out as a table or checklist. Do not use when the source is already a structured table with rows and columns (table-analysis), when the main ask is a set-level total or ranking (cross-doc-aggregation), or for a single fact from one document (doc-qa-cited).
version: "1"
---

# Extract to table (prose documents → structured rows)

The source documents are prose; the table does not exist until you build
it, cell by cell, from passages. The failure modes are invented cells
and quietly missing rows. Work in this order:

1. **Fix the field set first.** If the user named the fields, use
   exactly those, in their order. Otherwise propose the natural fields
   for the document kind — an invoice: month, issuer, amount, content;
   a payslip: month, gross, deductions, net — and state the field set
   before extracting. Never add, drop, or rename a column midway.
2. **Enumerate the document set.** Call `list_knowledge_documents` with
   filters matching the document kind, paging with `offset` until
   `truncated` is false. The enumeration is the row list: every
   enumerated document gets a row, including ones whose fields turn out
   to be missing. For a set of more than a couple of documents, plan
   with todos first (at most 3 items: fix fields and enumerate, extract
   per document, assemble and check) — progress by completing items,
   not by spending calls on status updates.
3. **Extract one document at a time — in parallel batches.** Pull each
   document's passages with `search_site_knowledge` aimed at that
   document and fill only that document's row. Issue the calls for
   several documents together in one round instead of one serial
   round-trip per document. A cell's value comes from that row's
   document only: never carry a value across rows, never borrow a field
   from a sibling document that happens to state it.
4. **Missing means 未提及 (not stated).** A field the document does not
   state is written as 未提及 — an explicit finding. Never guess it,
   never infer it from the document kind's usual pattern, and never
   leave the cell blank: a blank reads as zero or as an extraction bug,
   未提及 reads as "the document does not say".
5. **Make every row traceable.** Add a final source column (or cite per
   row) carrying the `[cN]` labels for the passages the row was built
   from. A value without a label does not go in the table.
6. **Totals only over a complete set.** A footer sum or average is
   allowed only when every row's value for that column was actually
   extracted — the same rule as cross-doc-aggregation. If any row is
   未提及 for that column, either omit the total or print it labeled as
   a partial sum over the extracted rows only, with the count stated.
7. **Close with a coverage note.** How many documents were extracted
   out of how many enumerated, which fields were commonly 未提及, and
   any document that could not be read at all. The table plus this note
   is the deliverable — deliver it in the conversation as markdown; do
   not claim a file was created.
