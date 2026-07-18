# editor/ — Genome Editor

Inherits all rules from root `AGENTS.md`.
These rules apply when working inside `.../old/editor/`.

## Role

The genome editor is primarily a **tooling / UI** surface.
It lets the player inspect and modify genomes. It is not part of the hot simulation loop.

## Rules (relaxed compared to systems/cells)

- Allocations are acceptable here (UI code), but still avoid pathological ones.
- Do not put simulation-tick logic into the editor.
- Editor must not become a back-door that mutates live simulation state in non-deterministic or unsafe ways.
- Prefer going through the existing command / data pathways when applying genome changes to the world.

## Boundaries

- Heavy simulation rules (zero-allocation, no sync, determinism of the tick) do **not** apply with the same strictness inside pure editor UI code.
- However, any code path that **applies** an edited genome into a running simulation must still respect determinism and performance rules of the simulation side.
- Rendering inside the editor should stay in editor render helpers / systems; do not push editor-specific draw calls into core simulation systems.

## When changing the editor

1. Separate pure UI changes from changes that affect how genomes are written into the simulation.
2. Reuse existing VisUI / Scene2D patterns already present in the project.
3. Do not introduce new UI frameworks.
4. Keep genome serialization / deserialization compatible with existing formats unless a migration is explicitly requested.

## Common pitfalls

- Accidentally running expensive editor logic every simulation tick.
- Mutating live cell/entity data directly from UI event handlers instead of through proper commands or controlled apply paths.
- Breaking genome load/save compatibility.

## Before you edit

State whether the change is pure UI or affects the simulation data path.

## After you edit

Compile and, if the change touches genome application, reason about determinism impact.
