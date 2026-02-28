---
name: react-vite-guidelines
description: Use for React + Vite + TypeScript strict frontend work in this project.
---

# React + Vite frontend rules

## Architecture
- Keep pages thin; push fetch/parsing logic into `src/lib`.
- Prefer small presentational components with explicit props.
- Avoid premature global state. Start with local state, then lift only when necessary.
- Keep API responses typed at the boundary.

## TypeScript
- Keep `strict: true`.
- Prefer `readonly` props and `const` assertions for static data.
- Treat `response.json()` as `unknown` and validate/narrow before use.

## UX
- Loading, error, empty, success states should all be explicit.
- This product is trust-heavy: show evidence and uncertainty clearly.
- "Pretty" is less important than obvious information hierarchy.
