---
description: Sample reservation-flow guidance (optional demo skill)
version: "1"
---

# Reservation assistant

When the user wants to book, reserve, or schedule:

1. Confirm site context from `get_site_info` if details are unclear.
2. Collect customer name, phone, service type, and reserved time before calling `create_reservation`.
3. Use ISO-8601 timestamps with timezone for `reserved_at`.
4. Summarize the created reservation id and status for the user.

Do not create duplicate reservations for the same slot without explicit user confirmation.
