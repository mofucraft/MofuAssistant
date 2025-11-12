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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * /osusowakenコマンドの処理クラス
 */
public class OsusowakenCommand implements CommandExecutor, TabCompleter {
    private final MofuAssistant plugin;
    private final CommunityDistributionManager manager;
    private final CommunityItemStorage storage;
    private final DistributionGUI gui;

    public OsusowakenCommand(MofuAssistant plugin, CommunityDistributionManager manager,
                            CommunityItemStorage storage, DistributionGUI gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.storage = storage;
        this.gui = gui;
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
            int memberCount = manager.getCommunityMemberCount(communityName);
            int distributionAmount = manager.calculateDistributionAmount(memberCount);
            player.sendMessage(ChatColor.YELLOW + communityName + ChatColor.GRAY + ": " +
                              memberCount + "人 → " + distributionAmount + "個");
        }

        return true;
    }

    /**
     * 特定コミュニティの詳細情報を表示
     */
    private void showCommunityInfo(Player player, String communityName) {
        int memberCount = manager.getCommunityMemberCount(communityName);
        int distributionAmount = manager.calculateDistributionAmount(memberCount);

        player.sendMessage(ChatColor.GREEN + "=== " + communityName + " ===");
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
     * /osusowaken help - ヘルプメッセージ
     */
    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== Osusowaken コマンドヘルプ ===");
        sender.sendMessage(ChatColor.YELLOW + "/osusowaken " + ChatColor.GRAY + "- アイテム配布GUIを開く");
        sender.sendMessage(ChatColor.YELLOW + "/osusowaken info [community] " + ChatColor.GRAY + "- コミュニティ情報を表示");
        sender.sendMessage(ChatColor.YELLOW + "/osusowaken help " + ChatColor.GRAY + "- このヘルプを表示");

        if (sender.hasPermission("mofuassistant.osusowaken.admin")) {
            sender.sendMessage("");
            sender.sendMessage(ChatColor.RED + "管理者コマンド:");
            sender.sendMessage(ChatColor.YELLOW + "/osusowaken setitem " + ChatColor.GRAY + "- 手に持つアイテムを配布アイテムに設定");
        }

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                     @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(Arrays.asList("info", "help"));
            if (sender.hasPermission("mofuassistant.osusowaken.admin")) {
                subCommands.add("setitem");
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
