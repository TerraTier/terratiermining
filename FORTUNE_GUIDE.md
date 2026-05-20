# TerraTier Custom Fortune Guide

The Fortune Manager allows you to create highly customizable loot multipliers based on item attributes. You can define specific groups of blocks and map them to custom fortune attributes.

## 1. Configuration (`config.yml`)

Add your fortune mappings under the `fortune-mappings` section. Each key is an **Attribute ID**, and the value is a list of **Minecraft Block IDs** it affects.

```yaml
fortune-mappings:
  crop_fortune:
    - "minecraft:wheat"
    - "minecraft:carrot"
    - "minecraft:potatoes"
  ore_fortune:
    - "minecraft:coal_ore"
    - "minecraft:iron_ore"
    - "minecraft:diamond_ore"
  sugar_cane_fortune:
    - "minecraft:sugar_cane"
```

## 2. Applying to Items

To use these fortunes, add the corresponding attribute to your item's `terratier:attributes` Persistent Data Container (PDC). The value of the attribute determines the fortune level.

**Example JSON for an item's attributes:**
```json
[
  {
    "id": "crop_fortune",
    "value": 3.0
  },
  {
    "id": "mining_speed",
    "value": 10.0
  }
]
```

## 3. How the Math Works

The system generalizes the vanilla Minecraft Fortune enchantment logic:

*   **Level 0:** 1x drops (no multiplier).
*   **Level 1:** 33% chance of 2x drops, 66% chance of 1x. (Avg 1.33x)
*   **Level 2:** 25% chance of 2x, 25% chance of 3x, 50% chance of 1x. (Avg 1.75x)
*   **Level 3:** 20% chance of 2x, 20% chance of 3x, 20% chance of 4x, 40% chance of 1x. (Avg 2.2x)
*   **Level N:** `1 / (N + 2)` chance for each multiplier from 2 to `N + 1`.

## 4. Key Benefits

*   **Scalability:** Create as many different fortune types as you want (e.g., `wood_fortune`, `gem_fortune`).
*   **Precision:** Control exactly which blocks are affected by which tools.
*   **Integration:** Works seamlessly with the existing TerraTier attribute system and custom items.
