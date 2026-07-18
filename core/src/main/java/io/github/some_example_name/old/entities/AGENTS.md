# entities/ — Higher-level Entities

Inherits all rules from root `AGENTS.md`.
These rules apply when working inside `.../old/entities/`.

## Role

Entities sit above individual cells. They represent organisms or other composite structures built from cells.
They coordinate higher-level behaviour while the heavy per-cell work stays in `cells/` and `systems/`.

## Hard rules

- Entities must not become a hidden second simulation layer that allocates heavily every tick.
- Prefer referencing cells / cell data over owning deep copies.
- Do not introduce synchronization here.
- Keep entity update logic deterministic.
- Avoid creating new entity objects every frame; reuse and pool where lifetime is short.

## Boundaries

- Low-level cell physics, neural firing, energy, division → stay in `cells/` + `systems/`.
- Entity code should orchestrate and hold higher-level state, not re-implement cell logic.
- Rendering of entities belongs in render systems, not here.

## When changing entities

1. Check whether the logic belongs closer to cells/systems instead.
2. Watch for accidental per-tick allocations (temporary lists of cells, new state objects, etc.).
3. Preserve existing identity and lifetime semantics unless explicitly changing them.
4. Prefer the existing command pattern (`commands/`) for external mutations of entity/world state.

## Common pitfalls

- Turning entities into god-objects that know too much about rendering or UI.
- Allocating collections of cells on every update.
- Breaking determinism by unordered iteration or non-seeded randomness.
- Duplicating data that already lives in cells.

## Before you edit

Explain which entity types are affected and whether the change touches a per-tick path.

## After you edit

Compile and confirm no new hot-path allocations were introduced.
