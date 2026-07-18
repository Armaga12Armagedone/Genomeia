# systems/ — Simulation & Render Systems

Inherits all rules from root `AGENTS.md`.
These rules apply when working inside `.../old/systems/`.

## Role

This package contains the critical update loops of the simulation and the corresponding render systems.
Almost every performance and determinism constraint of the project lives here.

## Hard rules

- **Zero / near-zero allocations** inside any per-tick or per-cell loop.
- Prefer primitive arrays and fastutil collections.
- Do not introduce locks, synchronized blocks, or concurrent collections in simulation systems.
- Simulation systems must never call any graphics / OpenGL / ShapeRenderer / SpriteBatch API.
- Render systems may only **read** simulation state. Never write back into it.
- Keep existing system registration and update order unless explicitly asked to change it.

## When changing a system

1. Identify whether it is a **simulation** system or a **render** system.
2. Measure or reason about allocation impact before and after.
3. Prefer in-place updates and object reuse / pooling.
4. Do not add new abstraction layers (no new ECS, no new event bus, etc.).

## Common pitfalls

- Creating temporary `List`, `ArrayList`, `HashMap` or Kotlin sequences inside the tick.
- Using `forEach` / lambdas that capture and allocate.
- Touching UI or input state from a simulation system.
- Changing iteration order over entities/cells in a way that breaks determinism.

## Before you edit

Explain:
- which system(s)
- simulation or render
- expected effect on allocations and determinism

## After you edit

Compile at least `:core:compileKotlin` and report any new allocations or ordering changes.
