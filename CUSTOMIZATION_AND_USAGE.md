# TerraTier Mining Customization And Usage

TerraTier scans every `.yml` and `.yaml` file inside `plugins/TerraTierMining`, including files in any subfolder. Organize definitions however you want:

```text
plugins/TerraTierMining/
  config.yml
  definitions/
    starter-blocks.yml
    starter-tools.yml
  mining/
    early-game.yml
    deep-caves.yml
  tools/
    pickaxes.yml
    axes.yml
  worlds/
    starter-island.yml
```

By default, TerraTier controls every block. Blocks without a rule are blocked until you define them. If you set `control-configured-blocks-only: true`, unconfigured blocks return to normal Paper/Minecraft mining.

The bundled starter files are active `.yml` files, so basic logs, dirt, sand, stone, coal ore, and vanilla starter tools work immediately after install.

## Build And Install

Build locally with the included Gradle wrapper:

```powershell
.\gradlew.bat build
```

The built jar is here:

```text
build/libs/terratier-custom-mining-0.2.0.jar
```

To build and copy directly into your local server:

```powershell
.\gradlew.bat deployPlugin -PserverPluginsDir="C:\path\to\server\plugins"
```

Put the jar in your server's `plugins` folder and restart. After editing YAML files, run:

```text
/terramining reload
```

Config-only changes need only `/terramining reload`. Java/plugin-code changes need a rebuilt jar plus a server restart or plugin reload tool.

Admin permission:

```text
terratier.mining.admin
```

## Commands

```text
/terramining reload
/terramining debug
/terramining debug full
/terramining speed
```

Aliases are also available: `/ttmining`, `/terratiermining`, and the old compatibility alias `/lowmining`.

Use `/terramining speed` to check your current final mining speed. Use `/terramining debug` while looking at a block and holding a tool for a compact status line. Use `/terramining debug full` to see candidate ids and block-data.

## File Formats

You can use grouped sections:

```yml
blocks:
  minecraft:stone:
    strength: 12.0
    tool: pickaxe
    min-tier: 1

tools:
  minecraft:iron_pickaxe:
    speed: 8.0
    type: pickaxe
    tier: 3
```

Or loose CraftEngine-style top-level ids:

```yml
minecraft:coal_ore:
  strength: 25.0
  tool: pickaxe
  min-tier: 2

terratier:copper_pickaxe:
  speed: 8.0
  type: pickaxe
  tier: 2
```

TerraTier decides what a loose entry is by its fields:

- Has `strength`: block rule.
- Has `speed`: tool rule.
- Has `block-data-contains`, `block-data`, or `host-material`: custom block identity.

## Mining Formula

Block strength is the number of seconds needed when mining speed is `1`.

```text
seconds_to_break = block_strength / mining_speed
```

Examples:

```text
stone strength 12, speed 1  -> 12 seconds
stone strength 12, speed 6  -> 2 seconds
coal ore strength 25, speed 5 -> 5 seconds
```

## Block Rules

```yml
minecraft:stone:
  strength: 12.0
  tool: pickaxe
  min-tier: 1
```

Fields:

- `strength`: seconds to mine at speed `1`.
- `tool`: `any`, `hand`, `pickaxe`, `axe`, `shovel`, `hoe`, `shears`, or `sword`.
- `min-tier`: required tier number.

Suggested tier scale:

```text
0 = hand/no tier
1 = wood/gold
2 = stone
3 = iron
4 = diamond
5 = netherite
```

## Tool Rules

```yml
terratier:copper_pickaxe:
  speed: 8.0
  type: pickaxe
  tier: 2
```

Fields:

- `speed`: total mining speed while held.
- `type`: the tool category used for block requirements.
- `tier`: numeric tier used for block requirements.

If a held item has no matching tool rule, TerraTier falls back to the player's `BLOCK_BREAK_SPEED` attribute or `settings.base-mining-speed`, whichever is higher.

## Mining Speed Buffs

Final mining speed is:

```text
final_speed = (held_tool_speed + additive_buffs) * multiplier_buffs
```

For CraftEngine/custom item attributes, put one compact TerraTier payload under CraftEngine `data.pdc`:

```yml
items:
  terratier:mining_chestplate:
    material: leather_chestplate
    data:
      pdc:
        terratier:item_id: terratier:mining_chestplate
        terratier:attributes: '[{"attribute":"mining_speed","value":10,"slot":"chest","source":"equipped"}]'
```

- `mining_speed` and `mining_speed_add` are flat additions.
- `mining_speed_multiplier` is a final multiplier. Use `1.15` for +15%.
- `source: equipped` applies only while held, offhanded, or worn; combine it with `slot: mainhand`, `offhand`, `head`, `chest`, `legs`, or `feet` for exact placement.
- `source: inventory` applies when the item is anywhere in the player's carried inventory.
- TerraTier scans the held item, offhand, armor, and inventory. The held item is not counted twice as an inventory item.

You can also define buff rules by item id in any YAML file:

```yml
buffs:
  terratier:mining_helmet:
    applies: equipped
    slots: [head]
    mining-speed-add: 2

  terratier:mining_charm:
    applies: inventory
    mining-speed-multiplier: 1.15
```

`applies` can be `held`, `offhand`, `armor`, `equipped`, `inventory`, or `all`.

## CraftEngine Items

Tool ids are checked in this order:

1. Persistent-data keys listed in `settings.custom-item-pdc-keys`, including the generator's `terratier:item_id`.
2. The item's `item_model`.
3. The item's `material#custom_model_data`, such as `minecraft:paper#1001`.
4. Vanilla material id, such as `minecraft:iron_pickaxe`.
5. `hand` for empty hands.

Hold the item and run `/terramining debug` to see the candidate tool ids. Put the id you want in any YAML file.

## CraftEngine Blocks

Custom blocks often use a vanilla host block plus special block-data, such as `note_block` states. To map those to TerraTier ids:

1. Look at the custom block in-game.
2. Run `/terramining debug full`.
3. Copy the shown `Block data` string.
4. Add an identity plus a block rule.

Example:

```yml
terratier:copper_ore:
  material: minecraft:note_block
  block-data-contains:
    - "instrument=basedrum"
    - "note=3"
  strength: 35.0
  tool: pickaxe
  min-tier: 2
```

The first matching custom block identity wins, so put more specific entries in files that sort earlier if two identities could match the same hosted block.

## Settings

`config.yml` is for global settings. Block and tool definitions can live anywhere.

```yml
settings:
  enabled: true
  control-configured-blocks-only: false
  bypass-creative: true
  suppress-vanilla-mining: true
  base-mining-speed: 1.0
  max-mining-distance: 7.0
```

- `control-configured-blocks-only`: when false, every block is controlled and blocks without rules are blocked. When true, only configured blocks use TerraTier mining.
- `bypass-creative`: lets creative and spectator players use normal behavior.
- `suppress-vanilla-mining`: zeroes vanilla client break speed to prevent vanilla crack flicker. In all-blocks-controlled mode this stays active for players continuously; in configured-only mode it applies while looking at configured blocks.
- `base-mining-speed`: default speed for hands and unconfigured tools.
- `max-mining-distance`: cancels mining if the player moves too far away or looks away.

## Suggested Balancing Workflow

1. Split definitions by progression tier or island/region.
2. Start with logs, dirt, sand, stone, and one ore.
3. Set base speed to `1.0`.
4. Choose block strengths as the intended hand-mining seconds.
5. Give starter tools speed `3` to `6`.
6. Use `/terramining debug full` whenever a block or item is not matching the id you expected.
