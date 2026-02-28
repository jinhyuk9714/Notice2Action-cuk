---
name: api-contract-guidelines
description: Use for frontend/backend type alignment.
---

# API contract rules

- Request and response names should be stable and obvious.
- If backend response changes, update frontend types in the same change.
- Prefer explicit field names over overloaded generic names.
- Keep nullable fields intentional.
- Make enums human-reviewable.
