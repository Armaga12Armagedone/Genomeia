@AGENTS.md

# Claude-specific instructions for Genomeia

You are working on a high-performance artificial life simulation written in Kotlin + LibGDX.
Determinism and zero/near-zero allocations in hot loops are more important than abstract cleanliness.

## Working style with Claude Code

- Before any non-trivial change, briefly restate:
  - what you are going to change
  - which files
  - why
  - expected impact on performance / determinism / allocations
- Prefer plan mode for changes that touch more than 1–2 files or any simulation hot path.
- Make the smallest possible diff. Do not “improve” unrelated code.
- After edits: run the relevant Gradle compile/build and fix errors before declaring done.
- When you are unsure whether a change can affect determinism or introduce allocations, say so explicitly and ask.

## Claude preferences for this project

- Treat simulation code as sacred. Be extremely conservative there.
- When reading code, pay special attention to loops that run per-cell or per-tick.
- Prefer showing a short before/after or the exact allocation that was removed/added.
- If a requested change would require architectural changes, stop and ask instead of proceeding.
- Do not invent new systems, frameworks, or abstractions. Extend what already exists.

## Hierarchy reminder

- Root rules live in `AGENTS.md` (imported above).
- Deeper architecture notes live in `ARCHITECTURE.md`.
- If a subdirectory later gains its own `AGENTS.md` or `CLAUDE.md`, the nearer file wins for that subtree.

Keep answers and diffs focused. Performance and determinism first.
