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

package page.nafuchoco.mofu.mofuassistant.community;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;
import page.nafuchoco.mofu.mofuassistant.database.DistributionCycleTable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * /osusowakenコマンドの処理クラス
 */
public class OsusowakenCommand implements CommandExecutor, TabCompleter {
    private final MofuAssistant plugin;
    private final CommunityDistributionManager manager;
    private final CommunityItemStorage storage;
    private final DistributionGUI gui;
    private final DistributionScheduler scheduler;
    private final DistributionCycleTable cycleTable;

    public OsusowakenCommand(MofuAssistant plugin, CommunityDistributionManager manager,
                            CommunityItemStorage storage, DistributionGUI gui,
                            DistributionScheduler scheduler, DistributionCycleTable cycleTable) {
        this.plugin = plugin;
        this.manager = manager;
        this.storage = storage;
        this.gui = gui;
        this.scheduler = scheduler;
        this.cycleTable = cycleTable;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (!manager.isLuckPermsAvailable()) {
            sender.sendMessage(ChatColor.RED + "LuckPermsが見つかりません。この機能は利用できません。");
            return true;
        }

        // 引数なし: GUIを開く
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
                return true;
            }

            if (!player.hasPermission("mofuassistant.osusowaken")) {
                player.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
                return true;
            }

            gui.openCommunitySelectionGUI(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "setitem":
                return handleSetItem(sender);

            case "info":
                return handleInfo(sender, args);

            case "start":
                return handleStart(sender);

            case "end":
                return handleEnd(sender);

            case "status":
                return handleStatus(sender);

            case "help":
                return handleHelp(sender);

            default:
                sender.sendMessage(ChatColor.RED + "不明なサブコマンドです。/osusowaken help でヘルプを表示します。");
                return true;
        }
    }

    /**
     * /osusowaken setitem - 手に持っているアイテムを配布アイテムとして設定
     */
    private boolean handleSetItem(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (!player.hasPermission("mofuassistant.osusowaken.admin")) {
            player.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "手にアイテムを持ってください。");
            return true;
        }

        // アイテムを保存（数量は1として保存）
        ItemStack saveItem = item.clone();
        saveItem.setAmount(1);
        storage.saveItem(saveItem);

        player.sendMessage(ChatColor.GREEN + "配布アイテムを設定しました。");
        player.sendMessage(ChatColor.GRAY + "アイテム: " + ChatColor.WHITE +
                          (item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                                  ? item.getItemMeta().getDisplayName()
                                  : item.getType().name()));
        return true;
    }

    /**
     * /osusowaken info [community] - コミュニティ情報の表示
     */
    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (!player.hasPermission("mofuassistant.osusowaken")) {
            player.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }

        List<String> communities = manager.getPlayerCommunities(player);

        if (communities.isEmpty()) {
            player.sendMessage(ChatColor.RED + "あなたはどのコミュニティにも所属していません。");
            return true;
        }

        // 特定のコミュニティが指定されている場合
        if (args.length >= 2) {
            String communityName = args[1];
            if (!communities.contains(communityName)) {
                player.sendMessage(ChatColor.RED + "あなたはこのコミュニティに所属していません。");
                return true;
            }

            showCommunityInfo(player, communityName);
            return true;
        }

        // 全てのコミュニティ情報を表示
        player.sendMessage(ChatColor.GREEN + "=== あなたのコミュニティ情報 ===");
        for (String communityName : communities) {
            String displayName = manager.getDisplayName(communityName);
            int memberCount = manager.getCommunityMemberCount(communityName);
            int distributionAmount = manager.calculateDistributionAmount(memberCount);
            player.sendMessage(ChatColor.YELLOW + displayName + ChatColor.GRAY + ": " +
                              memberCount + "人 → " + distributionAmount + "個");
        }

        return true;
    }

    /**
     * 特定コミュニティの詳細情報を表示
     */
    private void showCommunityInfo(Player player, String communityName) {
        String displayName = manager.getDisplayName(communityName);
        int memberCount = manager.getCommunityMemberCount(communityName);
        int distributionAmount = manager.calculateDistributionAmount(memberCount);

        player.sendMessage(ChatColor.GREEN + "=== " + displayName + " ===");
        player.sendMessage(ChatColor.GRAY + "メンバー数: " + ChatColor.WHITE + memberCount + "人");
        player.sendMessage(ChatColor.GRAY + "配布数: " + ChatColor.WHITE + distributionAmount + "個");

        ItemStack item = storage.loadItem();
        if (item != null) {
            String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                    ? item.getItemMeta().getDisplayName()
                    : item.getType().name();
            player.sendMessage(ChatColor.GRAY + "配布アイテム: " + ChatColor.WHITE + itemName);
        } else {
            player.sendMessage(ChatColor.RED + "配布アイテムが設定されていません。");
        }
    }

    /**
     * /osusowaken start - 手動で配布を開始
     */
    private boolean handleStart(CommandSender sender) {
        if (!sender.hasPermission("mofuassistant.osusowaken.admin")) {
            sender.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }

        try {
            scheduler.startManualCycle();
            sender.sendMessage(ChatColor.GREEN + "配布サイクルを手動で開始しました。");
        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "配布サイクルの開始中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "配布サイクルの開始に失敗しました。", e);
        }

        return true;
    }

    /**
     * /osusowaken end - 手動で配布を終了
     */
    private boolean handleEnd(CommandSender sender) {
        if (!sender.hasPermission("mofuassistant.osusowaken.admin")) {
            sender.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }

        try {
            scheduler.endManualCycle();
            sender.sendMessage(ChatColor.GREEN + "配布サイクルを終了しました。未回収のアイテムは破棄されました。");
        } catch (IllegalStateException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "配布サイクルの終了中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "配布サイクルの終了に失敗しました。", e);
        }

        return true;
    }

    /**
     * /osusowaken status - 配布状態を表示
     */
    private boolean handleStatus(CommandSender sender) {
        if (!sender.hasPermission("mofuassistant.osusowaken")) {
            sender.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }

        try {
            DistributionCycle activeCycle = cycleTable.getActiveCycle();

            if (activeCycle == null) {
                sender.sendMessage(ChatColor.RED + "現在、アクティブな配布サイクルはありません。");
                return true;
            }

            sender.sendMessage(ChatColor.GREEN + "=== 配布サイクル状態 ===");
            sender.sendMessage(ChatColor.GRAY + "サイクルID: " + ChatColor.WHITE + activeCycle.getCycleId());
            sender.sendMessage(ChatColor.GRAY + "開始時刻: " + ChatColor.WHITE + activeCycle.getStartTime());
            sender.sendMessage(ChatColor.GRAY + "終了時刻: " + ChatColor.WHITE + activeCycle.getEndTime());
            sender.sendMessage(ChatColor.GRAY + "ステータス: " +
                              (activeCycle.isCurrentlyValid() ? ChatColor.GREEN + "有効" : ChatColor.RED + "期限切れ"));

            // アイテム設定状況
            if (storage.hasItem()) {
                sender.sendMessage(ChatColor.GRAY + "配布アイテム: " + ChatColor.GREEN + "設定済み");
            } else {
                sender.sendMessage(ChatColor.GRAY + "配布アイテム: " + ChatColor.RED + "未設定");
            }

        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "配布状態の取得中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "配布状態の取得に失敗しました。", e);
        }

        return true;
    }

    /**
     * /osusowaken help - ヘルプメッセージ
     */
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== Osusowaken コマンドヘルプ ===");
        sender.sendMessage(ChatColor.YELLOW + "/osusowaken " + ChatColor.GRAY + "- アイテム配布GUIを開く");
        sender.sendMessage(ChatColor.YELLOW + "/osusowaken info [community] " + ChatColor.GRAY + "- コミュニティ情報を表示");
        sender.sendMessage(ChatColor.YELLOW + "/osusowaken status " + ChatColor.GRAY + "- 配布状態を表示");
        sender.sendMessage(ChatColor.YELLOW + "/osusowaken help " + ChatColor.GRAY + "- このヘルプを表示");

        if (sender.hasPermission("mofuassistant.osusowaken.admin")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "管理者コマンド:");
            sender.sendMessage(ChatColor.YELLOW + "/osusowaken setitem " + ChatColor.GRAY + "- 手に持つアイテムを配布アイテムに設定");
            sender.sendMessage(ChatColor.YELLOW + "/osusowaken start " + ChatColor.GRAY + "- 配布を手動で開始");
            sender.sendMessage(ChatColor.YELLOW + "/osusowaken end " + ChatColor.GRAY + "- 配布を手動で終了（未回収破棄）");
        }

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                     @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("info", "status", "help"));
            if (sender.hasPermission("mofuassistant.osusowaken.admin")) {
                subCommands.add("setitem");
                subCommands.add("start");
                subCommands.add("end");
            }

            String input = args[0].toLowerCase();
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(input)) {
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            if (sender instanceof Player player) {
                List<String> communities = manager.getPlayerCommunities(player);
                String input = args[1].toLowerCase();
                for (String community : communities) {
                    if (community.toLowerCase().startsWith(input)) {
                        completions.add(community);
                    }
                }
            }
        }

        return completions;
    }
}
