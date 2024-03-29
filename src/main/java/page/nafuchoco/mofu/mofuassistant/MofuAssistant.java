/*
 * Copyright 2021 NAFU_at
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package page.nafuchoco.mofu.mofuassistant;

import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import page.nafuchoco.mofu.mofuassistant.database.DatabaseConnector;
import page.nafuchoco.mofu.mofuassistant.database.MofuAssistantTable;
import page.nafuchoco.mofu.mofuassistant.event.PlayerPeacefulModeChangeEvent;
import page.nafuchoco.mofu.mofuassistant.listener.PeacefulModeEventListener;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class MofuAssistant extends JavaPlugin implements Listener {
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    //private static final Pattern NO_DELIMITER_UUID_PATTERN = Pattern.compile("^[0-9a-f]{32}$");
    private static MofuAssistant instance;

    public static MofuAssistant getInstance() {
        if (instance == null)
            instance = (MofuAssistant) Bukkit.getServer().getPluginManager().getPlugin("MofuAssistant");
        return instance;
    }


    private MofuAssistantConfig config;
    private DatabaseConnector connector;
    private MofuAssistantTable mofuAssistantTable;

    @Override
    public void onEnable() {
        // Plugin startup logic
        saveDefaultConfig();
        config = new MofuAssistantConfig();
        getPluginConfig().reloadConfig();
        if (getPluginConfig().isDebug())
            getInstance().getLogger().log(Level.INFO, getPluginConfig().toString());

        connector = new DatabaseConnector(getPluginConfig().getInitConfig().getDatabaseType(),
                getPluginConfig().getInitConfig().getAddress() + ":" + getPluginConfig().getInitConfig().getPort(),
                getPluginConfig().getInitConfig().getDatabase(),
                getPluginConfig().getInitConfig().getUsername(),
                getPluginConfig().getInitConfig().getPassword(),
                getPluginConfig().getInitConfig().getTablePrefix());
        mofuAssistantTable = new MofuAssistantTable("playerdata", connector);

        try {
            mofuAssistantTable.createTable();
        } catch (SQLException e) {
            getInstance().getLogger().log(Level.WARNING, "An error occurred while initializing the database table.", e);
        }

        getServer().getPluginManager().registerEvents(new PeacefulModeEventListener(), this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        if (connector != null)
            connector.close();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mofuassistant." + command.getName())) {
            sender.sendMessage(ChatColor.RED + "You can't run this command because you don't have permission.");
        } else switch (command.getName()) {

            case "assistant":
                if (args.length == 0)
                    return false;
                else if ("reload".equals(args[0])) {
                    getPluginConfig().reloadConfig();
                    sender.sendMessage(ChatColor.GREEN + "[MofuAssistant] Successfully reloaded the configuration.");
                } else {
                    return false;
                }

            case "peaceful":
                if (getPluginConfig().getPeacefulModeConfig().enable()) {
                    if (sender instanceof Player player) {
                        if (getPluginConfig().getPeacefulModeConfig().worldWhitelist()
                                ^ getPluginConfig().getPeacefulModeConfig().targetWorld().contains(player.getWorld().getName()))
                            break;

                        val playerData = MofuAssistantApi.getInstance().getPlayerData(player);
                        playerData.getSettings().setPeacefulMode(player.getWorld(), !playerData.getSettings().isPeacefulMode(player.getWorld()));
                        val peacefulModeChangeEvent = new PlayerPeacefulModeChangeEvent(player);
                        getServer().getPluginManager().callEvent(peacefulModeChangeEvent);
                        playerData.updatePlayerData();
                        player.sendMessage(ChatColor.GREEN + "[MofuAssistant] ピースフルモードを切り替えました。: " + playerData.getSettings().isPeacefulMode(player.getWorld()));
                    }
                }
                break;

            case "migrate":
                if (args.length != 2) {
                    sender.sendMessage(ChatColor.RED + "Usage: /migrate <original player> <new player>");
                    return true;
                }

                OfflinePlayer originalPlayer;
                OfflinePlayer newPlayer;

                if (UUID_PATTERN.matcher(args[0]).matches())
                    originalPlayer = Bukkit.getOfflinePlayer(UUID.fromString(args[0]));
                else originalPlayer = Bukkit.getOfflinePlayer(args[0]);

                if (UUID_PATTERN.matcher(args[1]).matches())
                    newPlayer = Bukkit.getOfflinePlayer(UUID.fromString(args[1]));
                else newPlayer = Bukkit.getOfflinePlayer(args[1]);

                sender.sendMessage(ChatColor.GREEN + "[MofuAssistant] データの移行を開始します...");
                var migrationManager = new MigrationManager();
                migrationManager.migrate(originalPlayer, newPlayer);
                sender.sendMessage(ChatColor.GREEN + "[MofuAssistant] データの移行が完了しました。");
                break;

            default:
                return false;
        }
        return true;
    }

    @EventHandler
    public void onPlayerQuitEvent(PlayerQuitEvent event) {
        MofuAssistantApi.getInstance().dropStoreData(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerPeacefulModeChangeEvent(PlayerPeacefulModeChangeEvent event) {
        if (MofuAssistantApi.getInstance().getPlayerData(event.getPlayer()).getSettings().isPeacefulMode(event.getPlayer().getWorld())) {
            MobHelper.getOffensive(event.getPlayer().getNearbyEntities(40, 40, 40)).forEach(
                    entity -> {
                        // 既にターゲット中のMobのターゲットを解除
                        if (entity.getTarget() instanceof Player target
                                && target.equals(event.getPlayer()))
                            entity.setTarget(null);
                    }
            );
        }
    }

    public MofuAssistantConfig getPluginConfig() {
        return config;
    }

    public MofuAssistantTable getMofuAssistantTable() {
        return mofuAssistantTable;
    }
}
