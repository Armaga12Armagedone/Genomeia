# cells/ — Cell Types & Cell Logic

Inherits all rules from root `AGENTS.md`.
These rules apply when working inside `.../old/cells/`.

## Role

Concrete cell types and the logic that runs on individual cells (or small groups of cells).
This is one of the hottest paths in the entire simulation — code here can easily run tens or hundreds of thousands of times per tick.

## Hard rules

- **Assume this code runs for 100k+ cells.**
- No object allocation inside hot cell-update paths unless absolutely unavoidable and pooled.
- Prefer primitive fields and arrays over object references where possible.
- Avoid nullable types in data that is accessed every tick.
- Do not call into rendering, UI, or input from cell logic.
- Keep cell behaviour deterministic (no hidden randomness, no reliance on iteration order of unordered collections).

## Design constraints

- Cell types should stay relatively data-oriented.
- Heavy shared data (genome definitions, large lookup tables) must be shared / referenced, not copied per cell.
- Division, death, energy, neural, pheromone and similar operations must be allocation-conscious.
- Prefer extending existing cell patterns over inventing completely new architectures.

## When adding or changing a cell type

1. Look at how existing similar cells are implemented.
2. Reuse existing pools, arrays and helper methods.
3. Explicitly consider what happens at scale (10k, 50k, 100k cells).
4. Do not add per-cell collections (List/Map) that grow without bound.

## Common pitfalls

- Storing temporary results in newly created lists/maps every tick.
- Using Kotlin standard library higher-order functions that allocate.
- Making cell objects heavier (more fields, more references) without measuring impact.
- Breaking determinism by depending on HashMap/HashSet iteration order.

## Before you edit

State:
- which cell type(s)
- whether the change is in a hot path
- allocation / determinism impact

## After you edit

Compile and reason about the cost at high cell counts.
