# Genomeia

Open-source biological sandbox / artificial life simulation.
Kotlin + LibGDX. Design organisms via genomes, watch them interact, survive and evolve in real time.

**Priority order (never violate):**
1. Determinism
2. Simulation performance (target 100k+ cells)
3. Existing architecture preservation
4. Minimal, surgical changes

## Stack

- Language: Kotlin 2.1.0
- Framework: LibGDX 1.13.1
- Modules: `core` (shared logic), `lwjgl3` (desktop), `android`, `ios`
- Key libs: fastutil (primitive collections), kotlinx-serialization-protobuf, VisUI, gdx-freetype
- Package root (current): `io.github.some_example_name.old`
- Desktop main class: `io.github.some_example_name.lwjgl3.Lwjgl3Launcher`

## Commands

```bash
./gradlew lwjgl3:run          # run desktop version (working dir = assets)
./gradlew build               # full build
./gradlew :core:compileKotlin # compile core only
./gradlew :lwjgl3:jar         # desktop jar
```

Never modify `build.gradle`, `settings.gradle`, `gradle.properties` or wrapper files unless explicitly requested.

## Project Structure (high-level)

```
core/src/main/java/io/github/some_example_name/old/
├── cells/          # cell types & cell-specific logic
├── commands/       # command pattern (actions on world/player)
├── core/           # core simulation primitives
├── editor/         # genome editor
├── entities/       # higher-level entities
├── game/           # game loop, world, screens
├── systems/        # simulation & render systems (critical path)
└── ui/             # VisUI / Scene2D UI
```

See `ARCHITECTURE.md` for deeper module boundaries and data flow.

### Nested AGENTS.md (scoped rules)

When working inside these packages, also follow the local `AGENTS.md`:

- `.../old/systems/AGENTS.md` — hottest path, zero-allocation & determinism
- `.../old/cells/AGENTS.md` — per-cell logic at 100k+ scale
- `.../old/entities/AGENTS.md` — higher-level organisms / composites
- `.../old/editor/AGENTS.md` — genome editor (UI-oriented, slightly relaxed)

Nearest file wins for conflicts.

## General Rules

- Never rewrite architecture unless explicitly requested.
- Always preserve determinism.
- Always preserve performance.
- Avoid unnecessary allocations.
- Avoid creating garbage every frame / in update loops.
- Avoid reflection.
- Prefer primitive collections (fastutil) over standard Kotlin/Java collections in hot paths.
- Prefer arrays over Lists in hot paths.
- Use existing project style and patterns.
- Never introduce new frameworks or heavy dependencies.
- Never modify build files unless requested.

## Simulation (highest priority)

- Simulation performance is the #1 constraint.
- Do not introduce synchronization / locks in simulation code.
- Do not allocate objects inside update / tick loops.
- Assume 100k+ cells must run acceptably.
- Every non-trivial optimization must be benchmarkable.
- Keep simulation completely independent from rendering.

## Rendering

- Rendering and simulation are strictly independent.
- Never move rendering logic into simulation systems.
- Avoid changing the OpenGL pipeline unless explicitly requested.
- Prefer existing shader / ShapeRenderer / batch paths.

## Kotlin Style

- Prefer `inline` functions for small hot helpers.
- Prefer immutable values where it does not hurt performance.
- Avoid extension function abuse.
- Avoid nullable types where possible (especially in hot data).
- Prefer primitive arrays / fastutil collections in performance-critical code.
- Match existing code style (naming, formatting, patterns).

## Before changing code

Always explain first:
- which files will change
- why
- expected performance impact (allocations, determinism, tick cost)

Never modify more files than necessary.

## After changes

1. Run `./gradlew build` (or at least `:core:compileKotlin`).
2. Fix all compilation errors.
3. Briefly explain every change made.

## Boundaries

**Never touch without explicit request:**
- Gradle build files (`build.gradle*`, `settings.gradle`, `gradle.properties`, wrapper)
- Generated / assets lists
- OpenGL pipeline setup
- Core determinism-related random / seed logic

**Ask first:**
- Any change that can affect determinism
- Adding dependencies
- Changing public command / system interfaces
- Large refactors even inside one module

## Further reading

- `ARCHITECTURE.md` — module map, simulation vs rendering separation, key data structures
- `CONTRIBUTING.md` — human contribution notes (currently minimal)
