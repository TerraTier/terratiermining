package com.terratier.custommining;

import com.terratier.custommining.command.MiningCommand;
import com.terratier.custommining.config.ItemAttributesConfig;
import com.terratier.custommining.config.MiningConfig;
import com.terratier.custommining.service.MiningService;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TerraTierMiningPlugin extends JavaPlugin {
    private MiningConfig miningConfig;
    private ItemAttributesConfig itemAttributesConfig;
    private MiningService miningService;

    @Override
    public void onEnable() {
        // Prepare files
        saveDefaultConfig();
        saveBundledFile("definitions/starter-blocks.yml");
        saveBundledFile("definitions/starter-tools.yml");
        saveBundledFile("item-attributes.yml");
        saveBundledFile("fortune.yml");

        // Load configs
        this.miningConfig = MiningConfig.load(this);
        this.itemAttributesConfig = ItemAttributesConfig.load(this);

        // Initialize Service
        this.miningService = new MiningService(this, miningConfig, itemAttributesConfig);

        // Register Command
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

    public void reloadMiningConfig() {
        reloadConfig();
        this.miningConfig = MiningConfig.load(this);
        this.itemAttributesConfig = ItemAttributesConfig.load(this);
        if (miningService != null) {
            miningService.updateConfig(miningConfig, itemAttributesConfig);
        }
    }

    public MiningConfig miningConfig() {
        return miningConfig;
    }

    public MiningService miningService() {
        return miningService;
    }

    private void saveBundledFile(String path) {
        if (!getDataFolder().toPath().resolve(path).toFile().exists()) {
            saveResource(path, false);
        }
    }
}
