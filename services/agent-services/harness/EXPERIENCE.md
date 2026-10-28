# Harness experience log

Distilled lessons from eval runs and production failures, following the
Harness² idea of harnessing-level recursion: a lesson counts as learned
only once it has been composed into a component (a platform file here,
the prompt builder, run limits, or retrieval settings) and verified.
This file is NOT injected into prompts — `harness.py` reads only
`background.md`, `guardrails.md`, and `tool_guidance.md`.

## The loop

1. **Propose** — change one harness component (or one knob) per change.
2. **Probe** — run the answer eval (golden set; add `--distractors` for
   the adversarial layer) and the targeted retrieval probes.
3. **Compose** — keep the change only if scores do not regress; revert
   otherwise. Record the lesson below either way, with its evidence.

## Lessons

### L1 — Site-specific values beat general knowledge (2026-10-27)

Symptom: "VPN 的远程网关是什么" was answered from general knowledge even
though the site manual held the exact gateway (vpn-gt.qualica.co.jp) and
retrieval had found it. The model had judged the question "common
knowledge" and skipped the knowledge tool.
Composed into: prompt_builder knowledge-available guidance — anything the
knowledge base might cover (procedures, configuration values, terminology)
must be searched first; the site value wins; say so when not found.
Evidence: user screenshot 2026-10-27; eval corpus covers the pattern.

### L2 — One blended query dilutes composite questions (2026-10-27)

Symptom: a question asking about warranty AND maintenance retrieved only
one side; the other side's chunks fell below the score floor even with
relative trimming disabled (true-embedding probe).
Composed into: prompt_builder guidance — separate search per distinct
part. Mirrored in `tool_guidance.md`.

### L3 — One source can crowd out the top-k (2026-10-27)

Symptom: for a single-topic question, all top-4 chunks came from one
long guide, starving other documents.
Composed into: `RETRIEVAL_MAX_CHUNKS_PER_SOURCE` (default 2), applied
after fusion and relative trimming, before rerank.
Verified: crowding probe re-run — the same question now returns exactly
2 chunks from that guide.

### L4 — Near-miss embeddings need a relevance gate (2026-10-27/28)

Symptom: character/semantic near-misses score above the vector floor —
an unanswerable question about snow-making machines retrieved an
industrial sewing-machine manual (both names end in the character 机).
Composed into: rerank relevance cutoff (`RETRIEVAL_RERANK_CUTOFF`,
default on) as the designated gate after vector retrieval.
Status: vector layer intentionally lets it through; answer-level
verification with `--distractors` is pending a full eval run.

### L5 — Verify the verifier before tuning the model (2026-10-27)

Symptom: unanswerable questions were all answered with correct refusals,
yet the eval scored refusal_correct 0.00 — the scorer's refusal cue list
did not contain the model's actual refusal phrasings.
Composed into: scoring.py cue expansion with regression cases (PR #75).
Lesson: when a metric says the model is wholly wrong, suspect the
measurement first.
