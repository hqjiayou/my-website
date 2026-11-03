---
name: summarize-transform
description: Summarize, condense, rewrite, or translate a document or passage set from this site's knowledge base — the deliverable is a transformed version of existing material. Use for "summarize this document", "make it shorter", "rewrite in plain language", "translate this". Do not use for answering one specific fact (doc-qa-cited), for set-level comparisons across documents (cross-doc-aggregation), or for drafting new content that is not grounded in a source document.
version: "1"
---

# Summarize & transform

1. **Read the whole source before writing.** Retrieve every part of the
   document first — several `search_site_knowledge` calls aimed at its
   sections, or the enumeration plus per-document reads when the source
   spans documents. A summary built on the first passage found is a
   failure, even if it reads well.
2. **Work section by section on long documents.** Note what each section
   covers and the facts it carries, then synthesize. State in the answer
   which document(s) and sections the result covers; if a part could not
   be retrieved, name the missing part instead of silently skipping it.
3. **Preserve the facts exactly.** Key numbers, dates, amounts, names,
   and conditions are copied, never rounded, re-estimated, or dropped
   because they look minor. Keep the citations attached to the facts they
   came from.
4. **Rewriting changes wording, never facts.** No added claims, no
   dropped negations or conditions, no softened obligations. Translation
   likewise: same facts, same numbers, same terms — only the language
   changes, and domain terms stay consistent with the source.
5. **Match the requested form.** Length, format, and tone follow what the
   user asked for (a few bullet points, one paragraph, a plain-language
   rewrite). When nothing was specified, default to a concise summary
   that keeps every key figure.
