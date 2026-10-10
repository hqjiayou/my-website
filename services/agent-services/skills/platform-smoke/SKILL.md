---
description: Platform smoke skill — acknowledge active skills only
version: "1"
---

# Platform smoke

This skill exists to verify skill loading on the agent platform.

- If asked to confirm skills, briefly list the active skill ids you were given.
- Do not invent business workflows.
- Prefer calling MCP `echo` / `add` only when the user explicitly requests a smoke check and those tools are available.
