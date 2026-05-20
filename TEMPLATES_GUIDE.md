# TerraTier Template & CraftEngine Guide

This guide explains how the new template-based system works and how to use it on your server.

## 1. How Templates Work in CraftEngine
CraftEngine allows you to define "blueprints" (templates) that can be reused across many items.
- **Global Registry**: Templates defined in any `.yml` file under the `templates:` root key are available to all other files.
- **Centralization**: We've put the base templates in `plugins/TerraTierMining/definitions/templates.yml`. CraftEngine will load these automatically.

## 2. Using Templates in the Web App
When you select a template (e.g., `terratier:tool`) in the editor:
1. The exported YAML becomes much smaller.
2. It uses the `template: <id>` and `arguments: <values>` format.
3. **Magic UI**: If this is ON, the editor formats the lore to look like vanilla Minecraft (hiding the real enchants/attributes and rebuilding them in the lore). This formatted lore is passed to the template as the `${lore}` argument.

## 3. Customizing Templates
You can create your own templates on your own terms:
1. Open `plugins/TerraTierMining/definitions/templates.yml`.
2. Add your own template under the `templates:` key.
3. Example:
   ```yaml
   templates:
     my_custom:base:
       material: stone
       data:
         item_name: "<red>${name}"
   ```
4. You can then use `my_custom:base` in your items.

## 3. Customizing Tooltip Layouts
You can now use raw attribute values to decide where they appear in the tooltip:
1. The web app passes raw values (e.g., `${mining_speed}`, `${fortune}`) to the template.
2. In your template, you can use `condition` to place them.
3. Example (Attribute at the top):
   ```yaml
   templates:
     my_custom:top_speed:
       template: terratier:item
       overrides:
         data:
           lore:
             - condition: "${mining_speed:-0} > 0"
               true: "<!i><#5555ff>+${mining_speed} Mining Speed"
             - ""
             - "${lore}"
   ```

## 4. Why the Buttons?
- **Include 'items:' key**: Turn this ON if you are creating a *new* file for CraftEngine. Turn it OFF if you are pasting into an existing `items:` block.
- **Magic UI Compiler**: Turn this ON if you want the editor to handle complex "vanilla look" formatting (like automatic attribute colors and spacing within the lore lines). If you want the *template* to handle the layout (like putting an attribute at the top), the template can use the raw `${attribute_name}` arguments.
- **Export Key**: This is the unique ID of your item (`namespace:id`). You need this if you want to define specific mining speeds or tiers in `plugins/TerraTierMining/definitions/tools.yml`.

## 5. Applying to Server
1. Copy the generated YAML from the web app.
2. Paste it into a `.yml` file in your CraftEngine items folder (e.g., `plugins/CraftEngine/items/my_items.yml`).
3. Ensure `plugins/TerraTierMining/definitions/templates.yml` is present on your server.
4. Reload CraftEngine (`/ce reload`).
