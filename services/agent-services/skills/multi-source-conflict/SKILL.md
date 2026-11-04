---
name: multi-source-conflict
description: Present conflicting versions side by side when site documents disagree about the same fact — different numbers, dates, terms, or editions in different places. Use when two or more retrieved passages state the same fact differently, or a version the user reports clashes with what the documents say. Do not use for set-level totals or rankings across documents (cross-doc-aggregation), for a fact stated in only one document (doc-qa-cited), or for row math inside one structured table (table-analysis).
version: "1"
---

# Multi-source conflict (same fact, several versions)

A conflict means: same subject, same attribute, different values in
different sources. The failure mode this playbook exists to prevent is
the quiet one — the model retrieves two versions, picks the one it saw
first (or the one that "sounds right"), and answers as if the other did
not exist. Never do that. Work it in this order:

1. **Gather every version.** Search by the fact's own terms with
   `search_site_knowledge`, aiming at each document that plausibly
   states it. When the fact belongs to a document kind, enumerate the
   kind with `list_knowledge_documents` so a second version is not lost
   to top-k retrieval. Stop when the sources that state the fact are
   covered — not when the first answer is found.
2. **Check identity before declaring a conflict.** Line the versions up
   and confirm they really are the same fact:
   - Different subjects are not a conflict. Two products with different
     warranty periods, two shops with different hours — attribute each
     value to its own subject and say so; there is nothing to resolve.
   - Different measures are not a conflict. Net pay vs gross pay, weekday
     vs weekend hours — present them as two labeled measures, one line
     each.
   - Different time scopes are not a conflict. A value for last year and
     a value for this year can both be right; label the scope.
   Only what survives this check — same subject, same measure, same
   scope, different value — is a true conflict.
3. **Present the true conflict side by side.** One row or one bullet per
   version: the value, its source citation `[cN]`, and any dating or
   edition signal the source itself carries (document date, version
   marker, a formal policy vs an informal notice, an announcement that
   says it supersedes an earlier one). Never silently pick one version.
   Never average, blend, or split the difference between versions —
   a compromise value exists in no source and is a fabrication.
4. **Weigh the signals — out loud.** If the signals let a reader judge
   (a newer dated document, a formal document over a casual notice, an
   explicit "this replaces…" statement), say which version they favor
   and why, and still show the other version. If no signal decides, say
   plainly: the site sources conflict on this point and it cannot be
   settled from the site materials. "Cannot be decided" is a complete
   answer; a coin flip dressed as certainty is not.
5. **Handle user-reported versions the same way.** If the user cites a
   value from memory, a colleague, or another channel, attribute it as
   user-reported (it has no citation), set it next to the documented
   version with its `[cN]`, and do not adopt the unsourced version over
   the documented one.

Hard constraints:

- Every version carries its own `[cN]` label from the tool result.
  Never invent a label, and never attach one source's label to another
  source's value.
- A single-source fact is not a conflict: if only one document states
  the fact, answer from it and do not manufacture a second version.
- If the user asked for a set-level number (total, ranking) over values
  that are themselves in conflict, state the conflict first and do not
  compute over a disputed value without flagging which version the
  computation used — set-level work itself belongs to
  cross-doc-aggregation.
