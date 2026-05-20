# TerraTier Custom Mining

Standalone Paper plugin for custom block strength, mining speed, and tool tier requirements.

## Build

Paper `26.1.2` uses Java 25 class files. The included Gradle wrapper uses Gradle `9.5.1`, so you do not need Maven or a system Gradle install:

```powershell
.\gradlew.bat build
```

The jar is created at:

```text
build/libs/terratier-custom-mining-0.2.0.jar
```

To build and copy the jar straight into a local server's `plugins` folder:

```powershell
.\gradlew.bat deployPlugin -PserverPluginsDir="C:\path\to\server\plugins"
```

## Install

Drop the jar into your Paper server's `plugins` folder and restart. The plugin writes its default config to:

```text
plugins/TerraTierMining/config.yml
```

It also writes starter definition files under:

```text
plugins/TerraTierMining/definitions/
```

These are real `.yml` files and are active out of the box. TerraTier scans every `.yml` and `.yaml` file in the plugin folder and all subfolders, so you can split blocks and tools into whatever layout you prefer.

Config-only changes do not need a jar rebuild. Edit the YAML, then run `/terramining reload`. Java/plugin-code changes still need a new jar and a server restart or plugin reload tool.

By default TerraTier controls every block. Blocks without a rule are blocked until you define them. In this mode TerraTier keeps vanilla `BLOCK_BREAK_SPEED` suppressed for survival players, which prevents the Minecraft client from drawing its own vanilla break cracks over the custom mining progress.

## Commands

```text
/terramining reload
/terramining debug
/terramining debug full
/terramining speed
```

`/terramining debug` shows a compact block/tool/result summary. Use `/terramining debug full` when lining up CraftEngine-backed blocks and tools because it includes candidate ids and block-data.

For config examples and balancing notes, see [CUSTOMIZATION_AND_USAGE.md](CUSTOMIZATION_AND_USAGE.md).

## CraftEngine Notes

TerraTier does not hard-depend on CraftEngine. It can still match custom items through:

- Persistent data keys such as `craftengine:id`.
- The item's `item_model`.
- `material#custom_model_data`, such as `minecraft:paper#1001`.
- Vanilla material id.

For custom mining-speed attributes on CraftEngine items, put TerraTier data under CraftEngine `data.pdc`:

```yml
items:
  terratier:mining_chestplate:
    material: leather_chestplate
    data:
      pdc:
        terratier:item_id: terratier:mining_chestplate
        terratier:attributes: '[{"attribute":"mining_speed","value":10,"slot":"chest","source":"equipped"}]'
```

For custom blocks backed by vanilla block states, add entries in any TerraTier YAML file using the block-data string from `/terramining debug full`.
