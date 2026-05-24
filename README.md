# TerraTier Mining (v0.4.0)

A highly configurable and scalable Paper plugin for custom mining mechanics, including block strength, mining speed, tool tiers, and a robust fortune system.

## Key Features

*   TerraTierStats Bridge: Reads custom item stats through the central TerraTierStats API.
*   Custom Mining Speed: Fine-grained control over mining speeds for any tool or block.
*   1:1 Fortune System: Simplified multiplier math (`1 + Fortune Attribute`) for predictable loot scaling.
*   Global & Specific Fortunes: Reserve the `fortune` attribute for all blocks, or map specific fortunes (e.g., `ore_fortune`) to block groups in `fortune.yml`.
*   Block Regeneration: Automatically restore blocks after break with customizable delays and placeholders (e.g., crops stay as baby seeds).
*   Seamless Mining: Proactive session migration and arm-swing detection for a smooth, lag-free experience.
*   Aesthetic UI: Beautifully formatted stats and breakdowns with blue-to-turquoise hex gradients and fancy text.
*   Auto-Pickup: Configurable global and per-block inventory collection.

## Commands

*   `/terramining stats` - View your current mining speed and active fortune.
*   `/terramining stats breakdown` - Detailed breakdown of all active buffs and their sources.
*   `/terramining reload` - Refresh configuration and definitions (Requires permission `terratier.mining.admin`).
*   `/terramining debug [full]` - Technical insights for block/tool IDs and custom block data.

## Build & Deployment

This project is configured with GitHub Actions for automated deployment to Server.pro.

1.  Local Build: 
    ```powershell
    .\gradlew.bat -p ..\terratierstats shadowJar
    .\gradlew.bat shadowJar
    ```
    Outputs: `..\terratierstats\build\libs\TerraTierStats.jar` and `build/libs/TerraTierMining.jar`

2.  Auto-Deploy: Pushing to `main` triggers a build and SFTP upload to your configured server. (Requires Repository Secrets).

## Configuration

*   `config.yml`: Main settings, auto-pickup, and default delays.
*   `fortune.yml`: Map custom stat IDs to Minecraft blocks for specific fortune types.
*   `definitions/`: Folder for YAML files defining block and tool rules.

## Integration

TerraTier Mining now depends on TerraTierStats. Use the custom `terratier:attributes` PDC key on items to apply `mining_speed`, `fortune`, `luck`, and held-tool `tier` stats via Item Studio. For example, `terratier:attributes: '[{"attribute":"tier","value":3}]'` makes that tool harvest blocks with `min-tier: 3` or lower.

Server-side item stat overrides that used to live in `item-attributes.yml` now belong in `plugins/TerraTierStats/item-stats.yml`.
