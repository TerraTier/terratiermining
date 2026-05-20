# TerraTier Mining v0.3.0 - Fortune System & Custom Drops

This update introduces a completely overhauled **Customizable Fortune System** and granular **Block Drop Rules**.

### ☘ 1:1 Fortune System
*   **Simple Math:** Multiplier = `1 + Fortune`.
    *   `1.0 Fortune` = 2x drops (1 base + 1 extra).
    *   `2.5 Fortune` = 2.5x drops (rounded logic).
*   **Global Attribute:** The `fortune` attribute now works on **all** plugin-controlled blocks by default.
*   **Scalability:** Supports specific fortune types (e.g., `ore_fortune`) mapped to specific block lists in `config.yml`.

### ⚒ Custom Block Drops
*   **Range Math:** Blocks can now drop a random amount of items (e.g., `1-3`).
*   **Multiple Items:** A single block can be configured to drop multiple different items.
*   **Auto-Pickup Overrides:** Per-block `auto-pickup` settings (e.g., make Wool drop on the ground even if global auto-pickup is ON).

### 📊 Modernized Statistics (`/ttm stats`)
*   **Aesthetic UI:** Uses **ꜰᴀɴᴄʏ ᴛᴇxᴛ** and custom hex colors for a premium feel.
*   **Detailed Breakdown:** New `/ttm stats breakdown` command shows exactly where your stats come from (Tools, Armor, Inventory, etc.).

### ⚙ Developer & Admin
*   **Web App Integration:** New mining attributes are fully supported in the Item Studio advanced settings.
*   **Granular YAML:** All new mechanics are fully configurable in `starter-blocks.yml` and `config.yml`.
