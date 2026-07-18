# Genomeia Architecture (alpha-0.2.4)

High-level map for AI agents and contributors.
This document is intentionally concise. Prefer reading the actual code over expanding this file.

## Goals that shape the architecture

1. **Determinism** — same seed + same inputs → identical simulation state.
2. **Performance** — target tens to hundreds of thousands of cells at interactive frame rates.
3. **Separation** — simulation logic must never depend on rendering or UI.
4. **Extensibility via commands** — many world/player actions go through a command pattern (foundation for future mods).

## Module layout (Gradle)

| Module   | Role                                      |
|----------|-------------------------------------------|
| `core`   | All shared game & simulation logic        |
| `lwjgl3` | Desktop launcher + LWJGL3 backend         |
| `android`| Android launcher                          |
| `ios`    | iOS launcher (RoboVM)                     |

Almost all interesting code lives in `core`.

## Package map (current, under `io.github.some_example_name.old`)

```
old/
├── cells/       # Concrete cell types and cell-local behaviour
├── commands/    # Command objects that mutate world / player state
├── core/        # Low-level simulation primitives, shared data
├── editor/      # Genome editor UI & logic
├── entities/    # Higher-level entities built from cells
├── game/        # Game screens, world container, main loop glue
├── systems/     # Update systems (simulation) and render systems
└── ui/          # VisUI / Scene2D screens and widgets
```

Note: the package still contains `.old` because a larger architectural cleanup is in progress.
Do not move packages or rename the root unless explicitly asked.

## Critical separation: Simulation ↔ Rendering

```
┌─────────────────────┐       ┌─────────────────────┐
│   Simulation        │       │   Rendering         │
│   (deterministic)   │──────▶│   (visual only)     │
│                     │  read │                     │
│  systems/*          │       │  systems/*Render*   │
│  cells/             │       │  editor render      │
│  core/              │       │  UI                 │
└─────────────────────┘       └─────────────────────┘
```

- Simulation systems must never call OpenGL, ShapeRenderer, SpriteBatch, or any LibGDX graphics API.
- Rendering systems may read simulation state but must never write to it.
- Never move rendering code into simulation update paths.

## Performance & allocation rules (hard constraints)

- Hot paths (cell update, physics, neural, pheromone, division, etc.) must allocate as close to zero objects as possible.
- Prefer:
  - primitive arrays
  - fastutil collections (`IntArray`, `ObjectArrayList`, etc.)
  - object pooling where objects are unavoidable
- Avoid:
  - `List`, `Map`, `Set` from Kotlin/Java stdlib in hot loops
  - boxing
  - lambdas / sequence operations that allocate
  - string operations inside ticks
- Any new allocation inside an update loop is a red flag and must be justified + measured.

## Determinism rules

- All randomness must go through a controlled, seedable source.
- Do not use `Math.random()`, `kotlin.random.Random.Default`, or system time for simulation decisions.
- Floating-point operations should be kept consistent (avoid platform-dependent paths).
- Order of iteration over collections that affect simulation state must be stable.

## Command pattern

Many mutations of the world and player go through the `commands/` package.
This is intentional (multi-threading safety + future modding surface).
When adding new player/world actions, prefer a new command over direct mutation of core state from UI or input handlers.

## Genome & cells

- Genomes define cell types, connections, neural logic, division rules, etc.
- Cells are the primary simulation entities (tens/hundreds of thousands).
- Heavy data (genomes, large arrays) should be shared or pooled; avoid per-cell heavy objects.

## Where to look first

| Task                        | Start here                          | Local rules |
|-----------------------------|-------------------------------------|-------------|
| Cell behaviour / update     | `cells/`, `systems/`                | `cells/AGENTS.md`, `systems/AGENTS.md` |
| Adding a new system         | `systems/` + registration pattern   | `systems/AGENTS.md` |
| Higher-level organisms      | `entities/`                         | `entities/AGENTS.md` |
| Genome editor               | `editor/`                           | `editor/AGENTS.md` |
| Input → world change        | `commands/` + handlers in `game/`/`ui/` | — |
| Rendering a new element     | corresponding `*Render*` system     | `systems/AGENTS.md` |
| Performance investigation   | hot loops in `systems/` + `cells/`  | those local AGENTS.md |

## What not to do

- Do not introduce ECS frameworks, dependency injection, or other heavy abstractions.
- Do not add synchronization primitives inside the simulation tick.
- Do not break the simulation/rendering split.
- Do not change Gradle files or add dependencies without explicit request.
- Do not perform large architectural rewrites “for cleanliness”.

---

When in doubt, make the smallest change that preserves determinism and performance, and explain the performance impact.
