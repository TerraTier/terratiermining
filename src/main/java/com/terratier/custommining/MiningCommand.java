package com.terratier.custommining;

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

final class MiningCommand implements CommandExecutor, TabCompleter {
    private static final String PRIMARY = "#34eb9b"; // Aqua-ish Mint
    private static final String SECONDARY = "#778899"; // Light Slate Gray
    private static final String ACCENT = "#e0e0e0"; // Light Gray
    private static final String HIGHLIGHT = "#5eead4"; // Turquoise
    private static final String LABEL = "#4facfe"; // Sky Blue
    
    private final TerraTierMiningPlugin plugin;
    private final Map<UUID, Long> lastStatsTime = new HashMap<>();

    MiningCommand(TerraTierMiningPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;

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

        return false;
    }

    private void showStats(Player player) {
        MiningService service = plugin.miningService();
        ToolStats tool = service.resolveTool(player);
        MiningBuffTotals buffs = service.buffResolver().resolve(player, plugin.miningConfig());

        if (shouldSendLeadingNewline(player)) player.sendMessage("");
        player.sendMessage(fade("MINING STATISTICS", "#4facfe", "#00f2fe", true));
        player.sendMessage("");
        
        displayStatLine(player, "Speed", tool.speed(), "⚒");
        
        double fortune = buffs.get("fortune", 0.0);
        displayStatLine(player, "Fortune", fortune, "☘");
        
        player.sendMessage("");
        player.sendMessage("§8§o" + fancy("Type /ttm stats breakdown for details"));
        player.sendMessage("");
        
        lastStatsTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private void showBreakdown(Player player) {
        MiningService service = plugin.miningService();
        ToolStats tool = service.resolveTool(player);
        MiningBuffTotals buffs = service.buffResolver().resolve(player, plugin.miningConfig());

        if (shouldSendLeadingNewline(player)) player.sendMessage("");
        player.sendMessage(fade("MINING STATS BREAKDOWN", "#4facfe", "#00f2fe", true));
        
        displayAttributeBreakdown(player, buffs, "mining_speed", "Mining Speed", "⚒", plugin.miningConfig().baseMiningSpeed());
        displayAttributeBreakdown(player, buffs, "fortune", "Fortune", "☘", 0.0);

        player.sendMessage("");
        
        lastStatsTime.put(player.getUniqueId(), System.currentTimeMillis());
    }

    private boolean shouldSendLeadingNewline(Player player) {
        long last = lastStatsTime.getOrDefault(player.getUniqueId(), 0L);
        return (System.currentTimeMillis() - last) > 100;
    }

    private void displayStatLine(Player player, String label, double value, String symbol) {
        player.sendMessage("  " + hex(LABEL) + fancy(label) + " §8» §7" + String.format("%.1f", value) + " " + symbol);
    }

    private void displayAttributeBreakdown(Player player, MiningBuffTotals buffs, String attr, String label, String symbol, double base) {
        double total = buffs.get(attr, 0.0);
        if (attr.equals("mining_speed")) {
            total += base;
        }

        player.sendMessage("");
        player.sendMessage("  " + hex(HIGHLIGHT) + fancy(label) + " §8» §7" + String.format("%.1f", total) + " " + symbol);
        
        if (base > 0) {
            player.sendMessage("    §8+§7" + String.format("%.1f", base) + " §8(" + hex(SECONDARY) + fancy("base") + "§8)");
        }

        buffs.sourceBreakdown().forEach((type, totals) -> {
            double val = totals.get(attr, 0.0);
            if (val != 0) {
                String sourceName = switch (type) {
                    case HELD -> "tools";
                    case OFFHAND -> "off-hand";
                    case ARMOR -> "armors";
                    case INVENTORY -> "inventory";
                };
                player.sendMessage("    §8+§7" + String.format("%.1f", val) + " §8(" + hex(SECONDARY) + fancy("from " + sourceName) + "§8)");
            }
        });
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
            
            String character = String.valueOf(text.charAt(i));
            sb.append(hex(hex));
            if (bold) sb.append("§l");
            sb.append(fancy(character));
        }
        return sb.toString();
    }

    private String hex(String code) {
        return "§x" + code.substring(1).chars()
            .mapToObj(c -> "§" + (char)c)
            .reduce("", (a, b) -> a + b);
    }

    private String fancy(String input) {
        return input.toLowerCase()
            .replace("a", "ᴀ")
            .replace("b", "ʙ")
            .replace("c", "ᴄ")
            .replace("d", "ᴅ")
            .replace("e", "ᴇ")
            .replace("f", "ꜰ")
            .replace("g", "ɢ")
            .replace("h", "ʜ")
            .replace("i", "ɪ")
            .replace("j", "ᴊ")
            .replace("k", "ᴋ")
            .replace("l", "ʟ")
            .replace("m", "ᴍ")
            .replace("n", "ɴ")
            .replace("o", "ᴏ")
            .replace("p", "ᴘ")
            .replace("q", "ǫ")
            .replace("r", "ʀ")
            .replace("s", "ꜱ")
            .replace("t", "ᴛ")
            .replace("u", "ᴜ")
            .replace("v", "ᴠ")
            .replace("w", "ᴡ")
            .replace("x", "x")
            .replace("y", "ʏ")
            .replace("z", "ᴢ");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("reload", "stats"));
            if (sender.hasPermission("terratier.mining.admin")) {
                options.add("speed");
                options.add("debug");
            }
            return options.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("stats")) {
            return List.of("breakdown");
        }
        return List.of();
    }
}
