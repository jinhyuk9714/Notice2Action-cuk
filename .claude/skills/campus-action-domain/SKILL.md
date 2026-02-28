---
name: campus-action-domain
description: Use for campus-specific action extraction and product decisions.
---

# Domain rules for Notice2Action CUK

## Product lens
This project does NOT optimize for generic summarization.
It optimizes for:
- what the student must do
- when they must do it
- what they must prepare
- whether they are eligible
- where they must go (TRINITY, 사이버캠퍼스, 외부 링크)

## Extraction priorities
Prioritize:
1. action verb
2. due date / schedule
3. required documents
4. system hint
5. evidence snippet

## Avoid
- motivational intro text
- notice titles by themselves
- irrelevant logistics
- decorative examples
- unsupported inference
