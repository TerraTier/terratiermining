package com.terratier.custommining;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TerraTierMiningPlugin extends JavaPlugin {
    private MiningConfig miningConfig;
    private ItemAttributesConfig itemAttributesConfig;
    private MiningService miningService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledFile("definitions/starter-blocks.yml");
        saveBundledFile("definitions/starter-tools.yml");
        saveBundledFile("item-attributes.yml");
        saveBundledFile("fortune.yml");
        miningConfig = MiningConfig.load(this);
        itemAttributesConfig = ItemAttributesConfig.load(this);
        this.miningService = new MiningService(this, miningConfig, itemAttributesConfig);

        MiningCommand miningCommand = new MiningCommand(this);
        PluginCommand command = getCommand("terramining");
        if (command != null) {
            command.setExecutor(miningCommand);
            command.setTabCompleter(miningCommand);
        }

        getLogger().info("TerraTier custom mining enabled.");
    }

    @Override
    public void onDisable() {
        if (miningService != null) {
            miningService.shutdown();
        }
    }

    void reloadMiningConfig() {
        reloadConfig();
        miningConfig = MiningConfig.load(this);
        itemAttributesConfig = ItemAttributesConfig.load(this);
        if (miningService != null) {
            miningService.updateConfig(miningConfig, itemAttributesConfig);
        }
    }

    MiningConfig miningConfig() {
        return miningConfig;
    }

    MiningService miningService() {
        return miningService;
    }

    private void saveBundledFile(String path) {
        if (!getDataFolder().toPath().resolve(path).toFile().exists()) {
            saveResource(path, false);
        }
    }
}
