package src.vshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class vShop extends JavaPlugin implements TabExecutor, Listener {
    private Economy econ;
    private final Map<Material, Double> itemPrices = new HashMap<>();
    private final Map<Player, Boolean> shiftSellEnabled = new HashMap<>();
    private final Map<Player, Long> shiftSellCooldown = new HashMap<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe("Vault não encontrado! Desabilitando plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        loadConfig();
        this.getCommand("sell").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean setupEconomy() {
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    private void loadConfig() {
        for (String key : getConfig().getConfigurationSection("items").getKeys(false)) {
            Material material = Material.getMaterial(key);
            if (material != null) {
                itemPrices.put(material, getConfig().getDouble("items." + key));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c§o[vShop] §cSomente jogadores podem usar este comando.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("vshop.sell")) {
            player.sendMessage("§c§o[vShop] §cVocê não tem permissão para usar esse comando.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
            sellAll(player);
        } else if (args.length > 0 && args[0].equalsIgnoreCase("shift")) {
            if (!player.hasPermission("vshop.shiftsell")) {
                player.sendMessage("§c§o[vShop] §cVocê não tem permissão para ativar/desativar a venda pelo shift.");
                return true;
            }
            toggleShiftSell(player);
        } else {
            player.sendMessage("§c§o[vShop] §cUso correto: /sell all ou /sell shift");
        }

        return true;
    }

    private void toggleShiftSell(Player player) {
        boolean currentState = shiftSellEnabled.getOrDefault(player, false);
        shiftSellEnabled.put(player, !currentState);
        String message = currentState ? "§b§o[vShop] §cVenda com shift desativada." : "§b§o[vShop] §aVenda com shift ativada.";
        player.sendMessage(message);
    }

    private void sellShift(Player player) {
        if (!shiftSellEnabled.getOrDefault(player, false)) {
            player.sendMessage("§c§o[vShop] §cVenda com shift está desativada. Use /sell shift para ativá-la.");
            return;
        }
        if (isInCooldown(player)) {
            long timeLeft = getCooldownTimeLeft(player);
            String message = getConfig().getString("messages.cooldown_message");
            message = message.replace("{time_left}", String.valueOf(timeLeft));
            player.sendMessage(message);
            return;
        }

        double totalValue = 0;
        int totalItemsSold = 0;
        Material soldItem = null;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getAmount() > 0 && itemPrices.containsKey(item.getType())) {
                soldItem = item.getType();
                double itemValue = itemPrices.get(item.getType()) * item.getAmount();
                totalItemsSold += item.getAmount();
                totalValue += itemValue;
                player.getInventory().remove(item);
            }
        }

        if (totalValue > 0) {
            econ.depositPlayer(player, totalValue);
            String message = getConfig().getString("messages.sell_shift");
            message = message.replace("{item_quantity}", String.valueOf(totalItemsSold))
                    .replace("{item_name}", soldItem != null ? soldItem.name() : "")
                    .replace("{total_value}", String.valueOf(totalValue));
            player.sendMessage(message);
            setCooldown(player);
        } else {
            player.sendMessage("§c§o[vShop] §cVocê não tem itens pra venda.");
        }
    }

    private void setCooldown(Player player) {
        long cooldownTime = getConfig().getInt("sell.interval") * 1000;
        shiftSellCooldown.put(player, System.currentTimeMillis() + cooldownTime);
    }

    private boolean isInCooldown(Player player) {
        return shiftSellCooldown.containsKey(player) && System.currentTimeMillis() < shiftSellCooldown.get(player);
    }

    private long getCooldownTimeLeft(Player player) {
        return (shiftSellCooldown.get(player) - System.currentTimeMillis()) / 1000;
    }

    private void sellAll(Player player) {
        double totalValue = 0;
        int totalItemsSold = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getAmount() > 0 && itemPrices.containsKey(item.getType())) {
                double itemValue = itemPrices.get(item.getType()) * item.getAmount();
                totalItemsSold += item.getAmount();
                totalValue += itemValue;
                player.getInventory().remove(item);
            }
        }

        if (totalValue > 0) {
            econ.depositPlayer(player, totalValue);
            String message = getConfig().getString("messages.sell_all");
            message = message.replace("{item_quantity}", String.valueOf(totalItemsSold));
            player.sendMessage(message.replace("{total_value}", String.valueOf(totalValue)));
        } else {
            player.sendMessage("§c§o[vShop] §cVocê não tem itens para venda.");
        }
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (shiftSellEnabled.getOrDefault(player, false) && player.isSneaking()) {
            sellShift(player);
        }
    }
}
