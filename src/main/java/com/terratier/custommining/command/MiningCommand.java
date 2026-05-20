package com.terratier.custommining.command;

import com.terratier.custommining.TerraTierMiningPlugin;
import com.terratier.custommining.model.MiningBuffTotals;
import com.terratier.custommining.model.ToolStats;
import com.terratier.custommining.service.MiningService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class MiningCommand implements CommandExecutor, TabCompleter {
    // Standard Attribute Colors
    private static final String COLOR_SPEED = "#4facfe";   // Sky Blue
    private static final String COLOR_FORTUNE = "#f6d365"; // Gold
    private static final String COLOR_LUCK = "#84fab0";    // Emerald Green
    
    private final TerraTierMiningPlugin plugin;
    private final Map<UUID, Long> lastStatsTime = new HashMap<>();

    public MiningCommand(TerraTierMiningPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("terratier.mining.admin")) {
            plugin.reloadMiningConfig();
            sender.sendMessage("§a[TerraTier] Configuration reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("stats") && sender instanceof Player player) {
            if (args.length > 1 && args[1].equalsIgnoreCase("breakdown")) {
                showBreakdown(player);
            } else {
                showStats(player);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("debug") && sender instanceof Player player && sender.hasPermission("terratier.mining.admin")) {
            showDebug(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("drops") && sender instanceof Player player && sender.hasPermission("terratier.mining.admin")) {
            plugin.miningService().showDrops(player);
            return true;
        }

        sendInvalidMessage(sender, args[0]);
        return true;
    }

    private void showHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage(fade("TERRATIER MINING HELP", "#667eea", "#764ba2", true));
        sender.sendMessage("");
        
        displayHelpLine(sender, "help", "Show this menu", "#a1c4fd");
        displayHelpLine(sender, "stats", "View your mining stats", "#abc9fd");
        displayHelpLine(sender, "stats breakdown", "View detailed stat sources", "#b5cefd");
        
        if (sender.hasPermission("terratier.mining.admin")) {
            sender.sendMessage("");
            sender.sendMessage(hex("#778899") + "§o" + fancy("Admin Commands:"));
            displayHelpLine(sender, "reload", "Reload configuration files", "#c2e9fb");
            displayHelpLine(sender, "debug", "Debug block/tool under cursor", "#cdeffb");
            displayHelpLine(sender, "drops", "View drop rates for block under cursor", "#c8f6fb");
        }
        
        sender.sendMessage("");
        if (sender instanceof Player p) lastStatsTime.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private void displayHelpLine(CommandSender sender, String cmd, String desc, String hexColor) {
        sender.sendMessage("  " + hex(hexColor) + "/ttm " + fancy(cmd) + " §8» §7" + desc);
    }

    private void showStats(Player player) {
        MiningService service = plugin.miningService();
        ToolStats tool = service.resolveTool(player);
        MiningBuffTotals buffs = service.resolveBuffs(player);

        player.sendMessage("");
        player.sendMessage(fade("MINING STATISTICS", "#a1c4fd", "#c2e9fb", true));
        player.sendMessage("");
        
        displayStatLine(player, "Speed", tool.speed(), "⚒", COLOR_SPEED);
        
        double fortune = buffs.get("fortune", 0.0);
        if (fortune != 0) displayStatLine(player, "Fortune", fortune, "☘", COLOR_FORTUNE);

        double luck = buffs.get("luck", 0.0) + buffs.get("mining_luck", 0.0);
        if (luck != 0) displayStatLine(player, "Luck", luck, "✦", COLOR_LUCK);
        
        player.sendMessage("");
        player.sendMessage("  §8§o" + fancy("Type /ttm stats breakdown for details"));
        player.sendMessage("");
        
        lastStatsTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void displayStatLine(Player player, String label, double val, String icon, String color) {
        player.sendMessage("  " + hex(color) + icon + " §7" + fancy(label) + " §8» §f" + String.format("%.1f", val));
    }

    private void showBreakdown(Player player) {
        MiningService service = plugin.miningService();
        ToolStats tool = service.resolveTool(player);
        MiningBuffTotals buffs = service.resolveBuffs(player);

        player.sendMessage("");
        player.sendMessage(fade("STATS BREAKDOWN", "#a1c4fd", "#c2e9fb", true));
        
        displaySpeedBreakdown(player, tool, buffs);

        double fortune = buffs.get("fortune", 0.0);
        if (fortune != 0) displayAttributeBreakdown(player, buffs, "fortune", "Fortune", "☘", COLOR_FORTUNE);

        double luck = buffs.get("luck", 0.0) + buffs.get("mining_luck", 0.0);
        if (luck != 0) displayAttributeBreakdown(player, buffs, "luck", "Luck", "✦", COLOR_LUCK);

        player.sendMessage("");
        lastStatsTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void displaySpeedBreakdown(Player player, ToolStats tool, MiningBuffTotals buffs) {
        double finalSpeed = tool.speed();
        player.sendMessage("");
        player.sendMessage("  " + fade("Mining Speed", COLOR_SPEED, "#ffffff", false) + " §8» " + hex(COLOR_SPEED) + "⚒ §f" + String.format("%.1f", finalSpeed));
        
        double speedAdd = buffs.getTotalAdd("mining_speed") + buffs.getTotalAdd("speed");
        double speedMult = buffs.getTotalMultiplier("mining_speed") * buffs.getTotalMultiplier("speed");
        double pureToolSpeed = (finalSpeed / Math.max(0.001, speedMult)) - speedAdd;

        player.sendMessage("    §8▪ §7+" + String.format("%.1f", pureToolSpeed) + " §8(" + fancy(tool.type().displayName()) + "§8)");

        buffs.sourceBreakdown().forEach((type, totals) -> {
            double add = totals.get("mining_speed", 0.0) + totals.get("speed", 0.0);
            double mult = totals.get("mining_speed_multiplier", 1.0) * totals.get("speed_multiplier", 1.0);
            String name = type.name().toLowerCase();
            if (add != 0) player.sendMessage("    §8▪ §7+" + String.format("%.1f", add) + " §8(" + fancy(name) + "§8)");
            if (mult != 1.0) player.sendMessage("    §8▪ §7x" + String.format("%.2f", mult) + " §8(" + fancy(name) + "§8)");
        });
    }

    private void displayAttributeBreakdown(Player player, MiningBuffTotals buffs, String attr, String label, String symbol, String color) {
        double total = buffs.get(attr, 0.0);
        player.sendMessage("");
        player.sendMessage("  " + fade(label, color, "#ffffff", false) + " §8» " + hex(color) + symbol + " §f" + String.format("%.1f", total));
        
        buffs.sourceBreakdown().forEach((type, totals) -> {
            double val = totals.get(attr, 0.0);
            if (val != 0) {
                player.sendMessage("    §8▪ §7+" + String.format("%.1f", val) + " §8(" + fancy(type.name().toLowerCase()) + "§8)");
            }
        });
    }

    private void showDebug(Player player) {
        player.sendMessage("");
        player.sendMessage(fade("MINING DEBUGGER", "#ff9a9e", "#fecfef", true));
        player.sendMessage("");
        plugin.miningService().debug(player);
        player.sendMessage("");
        lastStatsTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void sendInvalidMessage(CommandSender sender, String input) {
        sender.sendMessage("");
        sender.sendMessage("  §c§lERROR §8» §7" + fancy("Unknown command: ") + "§f" + input);
        sender.sendMessage("  §8§o" + fancy("Type /ttm help for a list of commands"));
        sender.sendMessage("");
    }

    private String fade(String text, String from, String to, boolean bold) {
        int r1 = Integer.valueOf(from.substring(1, 3), 16);
        int g1 = Integer.valueOf(from.substring(3, 5), 16);
        int b1 = Integer.valueOf(from.substring(5, 7), 16);
        
        int r2 = Integer.valueOf(to.substring(1, 3), 16);
        int g2 = Integer.valueOf(to.substring(3, 5), 16);
        int b2 = Integer.valueOf(to.substring(5, 7), 16);

        StringBuilder sb = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            int r = r1 + (r2 - r1) * i / Math.max(1, length - 1);
            int g = g1 + (g2 - g1) * i / Math.max(1, length - 1);
            int b = b1 + (b2 - b1) * i / Math.max(1, length - 1);
            String hex = String.format("#%02x%02x%02x", r, g, b);
            sb.append(hex(hex));
            if (bold) sb.append("§l");
            sb.append(fancy(String.valueOf(text.charAt(i))));
        }
        return sb.toString();
    }

    private String hex(String code) {
        return "§x" + code.substring(1).chars().mapToObj(c -> "§" + (char)c).reduce("", (a, b) -> a + b);
    }

    private String fancy(String input) {
        if (input == null) return "";
        return input.toLowerCase()
            .replace("a", "ᴀ").replace("b", "ʙ").replace("c", "ᴄ").replace("d", "ᴅ")
            .replace("e", "ᴇ").replace("f", "ꜰ").replace("g", "ɢ").replace("h", "ʜ")
            .replace("i", "ɪ").replace("j", "ᴊ").replace("k", "ᴋ").replace("l", "ʟ")
            .replace("m", "ᴍ").replace("n", "ɴ").replace("o", "ᴏ").replace("p", "ᴘ")
            .replace("q", "ǫ").replace("r", "ʀ").replace("s", "ꜱ").replace("t", "ᴛ")
            .replace("u", "ᴜ").replace("v", "ᴠ").replace("w", "ᴡ").replace("x", "x")
            .replace("y", "ʏ").replace("z", "ᴢ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("reload", "stats", "help"));
            if (sender.hasPermission("terratier.mining.admin")) {
                options.add("debug");
                options.add("drops");
            }
            return options.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) return List.of("breakdown");
        return List.of();
    }
}
