---
name: chart-selection
description: Decide whether and how to chart numbers the agent already has — pick the chart type, keep the picture honest, and render it. Use when the user asks to draw, plot, or visualize data, or when a numeric answer is clearly better seen than read. Do not use to fetch or compute the numbers themselves (table-analysis for rows inside one table, cross-doc-aggregation for set-level values across documents), and do not chart a single fact (doc-qa-cited).
version: "1"
---

# Chart selection (the right picture, honestly drawn)

A chart is a claim in visual form. This playbook covers the decision —
whether to chart at all, which type, and how to keep it from misleading.
Getting the numbers is not this playbook's job: fetch or compute them
first with the sibling playbooks, then come here.

## Gate: should this be a chart at all?

Chart only when the data forms a trend, a comparison, or a composition
with at least three data points. One or two numbers are a sentence, not
a chart. A non-numeric answer never becomes a chart. If the user asks
for a chart of something that fails this gate, say so in one line and
answer in text instead.

## Values first, exact

- Every plotted value must equal the tool output it came from. No
  rounding that changes a value's meaning, no invented points to fill a
  gap in a series, no silent interpolation.
- If a category in the series is missing, leave the gap visible or
  state it in the text — never connect over it as if it were measured.

## Pick the type

| Data shape | Chart | Notes |
|---|---|---|
| Change over time, ordered points | line | bar is fine for a handful of points |
| Category comparison (months, products, groups) | bar | sort by value only if order carries no meaning |
| Filled magnitude over time | area | only when a zero baseline is honest |
| Composition of one whole | pie | at most 5 parts, and the parts must sum to the whole; otherwise use bar |

When two types fit, prefer the one the user named; when the user named
a type that fights the data (a pie for a time trend), say so briefly
and use the fitting one.

## Honesty rules

- **Axes start at zero for bar charts.** Never truncate an axis to make
  differences look bigger. If the user asks for a truncated axis,
  decline that part in one line and render the honest axis.
- **Title states the measure, the unit, and the coverage** — e.g.
  「实发工资（日元），2026年1–4月」. A chart whose口径 is unstated is a
  chart that will be misread.
- **Label every series and category** with the names the sources use.
- Percentages in a composition are computed from the same values being
  plotted, and the whole they sum to is named.

## Render and close

Render with `render_chart`. It is registered in the trial (preview)
surface; in a formal conversation where it is unavailable, fall back to
a small markdown table with the same values and say that is what you
did. After rendering, add one or two sentences of takeaway — the trend
or the standout category — and do not re-recite every value in prose.
