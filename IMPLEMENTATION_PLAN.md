# TerraTier Custom Mining Plan

## Goal

Create a Paper plugin that replaces vanilla mining for configured blocks with a Hypixel-style stat check:

- Blocks have a configurable `strength`.
- Players/tools provide a mining `speed`.
- Mining progress is advanced by `speed / strength` every second.
- Blocks can require a tool type and minimum tier before progress starts.

The first implementation is intentionally config-first. It does not require a hard CraftEngine dependency, but it can use CraftEngine-style item ids when those ids are exposed through item metadata, item models, custom model data, or mirrored in this plugin's config.

## Core Model

### Block Rules

Each block id maps to:

- `strength`: how many seconds the block takes at mining speed `1`.
- `tool`: `any`, `hand`, `pickaxe`, `axe`, `shovel`, `hoe`, `shears`, or `sword`.
- `min-tier`: numeric tier gate. Suggested scale:
  - `0`: hand/no tier
  - `1`: wood/gold
  - `2`: stone
  - `3`: iron
  - `4`: diamond
  - `5`: netherite

Example:

```yml
blocks:
  minecraft:oak_log:
    strength: 1.5
    tool: axe
    min-tier: 0
  minecraft:coal_ore:
    strength: 25
    tool: pickaxe
    min-tier: 2
```

### Tool Rules

Each tool id maps to:

- `speed`: total mining speed while held.
- `type`: mining category.
- `tier`: numeric tier.

If a tool has no configured `speed`, the plugin falls back to the player's `BLOCK_BREAK_SPEED` attribute, then the configured base speed.

Example:

```yml
tools:
  terratier:copper_pickaxe:
    speed: 8
    type: pickaxe
    tier: 2
```

## Identity Resolution

### Blocks

Resolution order:

1. Configured `custom-block-identities` matches, using host material and optional block-data substrings.
2. Vanilla material id, such as `minecraft:stone`.

This gives us a practical CraftEngine bridge without binding to CraftEngine internals yet. If a CraftEngine custom block is backed by a `note_block`, `mushroom_stem`, or another vanilla host block, you can map its block-data signature to the same id you use for the item.

### Tools

Resolution order:

1. Configured persistent-data keys, such as `craftengine:id`.
2. Item model key, such as `terratier:copper_pickaxe`.
3. Material plus custom model data, such as `minecraft:paper#1001`.
4. Vanilla material id, such as `minecraft:iron_pickaxe`.
5. `hand` / `minecraft:air` for empty hands.

## Mining Loop

1. Listen for `BlockDamageEvent`.
2. If all-blocks-controlled mode is enabled and the block has no rule, block mining with no visual feedback.
3. If the block has a rule, suppress vanilla mining and create a mining session.
4. Every tick:
   - Re-resolve the block and held tool.
   - Verify distance, block identity, tool type, and tier.
   - Add progress using `speed / strength / 20`.
   - Send client crack animation.
5. On completion, call `Player#breakBlock(block)` so Paper still runs normal break behavior and protection plugins can cancel it.
6. Stop the session on `BlockDamageAbortEvent`, disconnect, changed block, changed world, or failed requirement.

## Commands

- `/terramining reload`: reloads config.
- `/terramining debug`: shows the block ids, tool ids, selected rule, selected tool, and current speed for the player's targeted block.

## Implementation Phases

1. Build the standalone Paper plugin scaffold.
2. Implement config parsing and id resolution.
3. Implement tick-based mining sessions with crack animation.
4. Add reload/debug commands.
5. Compile against Paper `26.1.2.build.64-stable`.

## Future Extensions

- Direct CraftEngine API resolver if you want exact runtime item/block ids from CraftEngine rather than metadata/config inference.
- Party/shared block damage where nearby players see the same crack animation.
- Custom drops, fortune/silk-touch overrides, XP tables, and region-specific strength modifiers.
- Player stat storage for mining speed bonuses from armor, pets, skills, areas, or temporary buffs.
- Tooltip generation that reads the same tool and block config so UI and behavior cannot drift.
