---
name: notice-extraction-quality
description: Use for improving action extraction quality and trust.
---

# Extraction quality rules

## Trust
- Every extracted field should have a reason.
- Separate confirmed facts from inferred hints.
- If a date is ambiguous, show the ambiguity rather than pretending certainty.

## Noise control
Do not extract from:
- title-only lines
- table-of-contents style lines
- duplicate text
- footer/header noise
- malformed OCR fragments

## Evidence
- Evidence snippets should be short and specific.
- Avoid dumping giant paragraphs as evidence.
- Confidence is useful only if it is interpretable.
