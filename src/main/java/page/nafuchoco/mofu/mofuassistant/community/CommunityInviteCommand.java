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

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;
import page.nafuchoco.mofu.mofuassistant.database.CommunityInviteTable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * コミュニティ招待コマンドハンドラ
 */
public class CommunityInviteCommand implements CommandExecutor, TabCompleter {
    private static final String PREFIX = ChatColor.AQUA + "[コミュニティ] " + ChatColor.WHITE;

    private final MofuAssistant plugin;
    private final CommunityInviteTable inviteTable;
    private final CommunityDistributionManager manager;
    private final LuckPerms luckPerms;

    public CommunityInviteCommand(MofuAssistant plugin, CommunityInviteTable inviteTable,
                                 CommunityDistributionManager manager, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.inviteTable = inviteTable;
        this.manager = manager;
        this.luckPerms = luckPerms;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                            @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return showHelp(sender);
        }

        String subCommand = args[0].toLowerCase();
        // サブコマンド用に引数配列を調整
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);

        switch (subCommand) {
            case "invite":
                return handleInvite(sender, subArgs);
            case "cancel":
                return handleCancel(sender, subArgs);
            case "check":
                if (subArgs.length > 0) {
                    // /community check <player>
                    return handleCheckOther(sender, subArgs);
                } else {
                    // /community check
                    return handleCheck(sender, subArgs);
                }
            case "accept":
                return handleAccept(sender, subArgs);
            case "deny":
                return handleDeny(sender, subArgs);
            case "leave":
                return handleLeave(sender, subArgs);
            case "who":
                return handleWho(sender, subArgs);
            case "help":
                return showHelp(sender);
            default:
                sender.sendMessage(ChatColor.RED + "不明なサブコマンドです。/community help でヘルプを表示します。");
                return true;
        }
    }

    /**
     * ヘルプメッセージを表示
     */
    private boolean showHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GREEN + "=== Community コマンドヘルプ ===");
        sender.sendMessage(ChatColor.YELLOW + "/community invite <player> <communityId> " + ChatColor.GRAY + "- プレイヤーを招待");
        sender.sendMessage(ChatColor.YELLOW + "/community cancel <player> <communityId> " + ChatColor.GRAY + "- 招待をキャンセル");
        sender.sendMessage(ChatColor.YELLOW + "/community check " + ChatColor.GRAY + "- 自分の招待を確認");
        sender.sendMessage(ChatColor.YELLOW + "/community check <player> " + ChatColor.GRAY + "- 他プレイヤーの招待を確認");
        sender.sendMessage(ChatColor.YELLOW + "/community accept <communityId> " + ChatColor.GRAY + "- 招待を受け入れ");
        sender.sendMessage(ChatColor.YELLOW + "/community deny <communityId> " + ChatColor.GRAY + "- 招待を拒否");
        sender.sendMessage(ChatColor.YELLOW + "/community leave <communityId> " + ChatColor.GRAY + "- コミュニティから脱退");
        sender.sendMessage(ChatColor.YELLOW + "/community who <communityId> [page] " + ChatColor.GRAY + "- メンバー一覧");
        return true;
    }

    /**
     * /community invite <player> <communityId> - プレイヤーを招待
     */
    private boolean handleInvite(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "使用方法: /community invite <プレイヤー名> <コミュニティID>");
            return true;
        }

        String targetName = args[0];
        String communityName = args[1];

        // 権限チェック
        if (!player.hasPermission("community." + communityName)) {
            player.sendMessage(PREFIX + "権限がありません");
            return true;
        }

        // 対象プレイヤーを取得
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        try {
            // 招待を作成
            inviteTable.createInvite(target.getUniqueId(), communityName, player.getUniqueId());
            player.sendMessage(PREFIX + targetName + " を招待しました");

            // オンラインなら通知
            if (target.isOnline() && target.getPlayer() != null) {
                target.getPlayer().sendMessage(PREFIX + player.getName() + " から" + communityName + "への招待が来ています！");
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "招待の作成中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "Failed to create invite", e);
        }

        return true;
    }

    /**
     * /community cancel <player> <communityId> - 招待をキャンセル
     */
    private boolean handleCancel(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "使用方法: /community cancel <プレイヤー名> <コミュニティID>");
            return true;
        }

        String targetName = args[0];
        String communityName = args[1];

        // 権限チェック
        if (!player.hasPermission("community." + communityName)) {
            player.sendMessage(PREFIX + "権限がありません");
            return true;
        }

        // 対象プレイヤーを取得
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        try {
            // 招待をチェック
            if (!inviteTable.hasInvite(target.getUniqueId(), communityName)) {
                player.sendMessage(PREFIX + targetName + " は招待されていません");
                return true;
            }

            // 招待を削除
            inviteTable.deleteInvite(target.getUniqueId(), communityName);
            player.sendMessage(PREFIX + targetName + " の招待を取り消しました");
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "招待のキャンセル中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "Failed to cancel invite", e);
        }

        return true;
    }

    /**
     * /community check - 自分の招待を確認
     */
    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        try {
            CommunityInvite invite = inviteTable.getInvite(player.getUniqueId());
            if (invite != null) {
                String displayName = manager.getDisplayName(invite.getCommunityName());
                player.sendMessage(PREFIX + displayName + " への招待があります");
                player.sendMessage(ChatColor.GRAY + "受け入れる: " + ChatColor.GREEN + "/community accept " + invite.getCommunityName());
                player.sendMessage(ChatColor.GRAY + "拒否する: " + ChatColor.RED + "/community deny " + invite.getCommunityName());
            } else {
                player.sendMessage(PREFIX + "招待がありません");
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "招待の確認中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "Failed to check invite", e);
        }

        return true;
    }

    /**
     * /community check <player> - 他プレイヤーの招待を確認
     */
    private boolean handleCheckOther(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "使用方法: /community check <プレイヤー名>");
            return true;
        }

        String targetName = args[0];

        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

        try {
            CommunityInvite invite = inviteTable.getInvite(target.getUniqueId());
            if (invite != null) {
                String displayName = manager.getDisplayName(invite.getCommunityName());
                sender.sendMessage(PREFIX + targetName + " は " + displayName + " に招待されています");
            } else {
                sender.sendMessage(PREFIX + targetName + " は招待されていません");
            }
        } catch (SQLException e) {
            sender.sendMessage(ChatColor.RED + "招待の確認中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "Failed to check invite", e);
        }

        return true;
    }

    /**
     * /community accept <communityId> - 招待を受け入れる
     */
    private boolean handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "使用方法: /community accept <コミュニティID>");
            return true;
        }

        String communityName = args[0];

        // すでに参加しているかチェック
        if (player.hasPermission("community." + communityName)) {
            player.sendMessage(PREFIX + "すでにコミュニティに参加しています");
            return true;
        }

        try {
            CommunityInvite invite = inviteTable.getInvite(player.getUniqueId());

            if (invite == null || !invite.getCommunityName().equals(communityName)) {
                player.sendMessage(PREFIX + communityName + " へは招待されていません");
                return true;
            }

            // LuckPermsでグループに追加
            User user = luckPerms.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                user.data().add(Node.builder("group." + communityName).build());
                luckPerms.getUserManager().saveUser(user);

                // 招待を削除
                inviteTable.deleteInvite(player.getUniqueId(), communityName);

                String displayName = manager.getDisplayName(communityName);
                player.sendMessage(PREFIX + displayName + " に参加しました！");
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "招待の受け入れ中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "Failed to accept invite", e);
        }

        return true;
    }

    /**
     * /community deny <communityId> - 招待を拒否
     */
    private boolean handleDeny(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "使用方法: /community deny <コミュニティID>");
            return true;
        }

        String communityName = args[0];

        try {
            CommunityInvite invite = inviteTable.getInvite(player.getUniqueId());

            if (invite == null || !invite.getCommunityName().equals(communityName)) {
                player.sendMessage(PREFIX + "招待されていません");
                return true;
            }

            // 招待を削除
            inviteTable.deleteInvite(player.getUniqueId(), communityName);
            String displayName = manager.getDisplayName(communityName);
            player.sendMessage(PREFIX + displayName + " への招待を拒否しました");
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "招待の拒否中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "Failed to deny invite", e);
        }

        return true;
    }

    /**
     * /community leave <communityId> - コミュニティから脱退
     */
    private boolean handleLeave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "このコマンドはプレイヤーのみ実行できます。");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "使用方法: /community leave <コミュニティID>");
            return true;
        }

        String communityName = args[0];

        // 参加しているかチェック
        if (!player.hasPermission("community." + communityName)) {
            player.sendMessage(PREFIX + "そのコミュニティには参加していません");
            return true;
        }

        // LuckPermsでグループから削除
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) {
            user.data().remove(Node.builder("group." + communityName).build());
            luckPerms.getUserManager().saveUser(user);

            player.sendMessage(PREFIX + "コミュニティを抜けました");
        }

        return true;
    }

    /**
     * /co-who <communityId> [page] - メンバー一覧
     */
    private boolean handleWho(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ChatColor.RED + "使用方法: /co-who <コミュニティID> [ページ番号]");
            return true;
        }

        String communityName = args[0];
        int page = args.length > 1 ? parseIntOrDefault(args[1], 1) : 1;

        // LuckPermsのlistmembersコマンドを実行
        String command = "lp group " + communityName + " listmembers " + page;
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

        return true;
    }

    private int parseIntOrDefault(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                     @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        String cmdName = command.getName().toLowerCase();

        if (args.length == 1) {
            // 第1引数: プレイヤー名
            if (cmdName.equals("co-invite") || cmdName.equals("co-cancel") || cmdName.equals("co-check-o")) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(player.getName());
                    }
                }
            }
            // 第1引数: コミュニティID
            else if (cmdName.equals("co-accept") || cmdName.equals("co-deny") ||
                     cmdName.equals("co-leave") || cmdName.equals("co-who")) {
                if (sender instanceof Player player) {
                    List<String> communities = manager.getPlayerCommunities(player);
                    for (String community : communities) {
                        if (community.toLowerCase().startsWith(args[0].toLowerCase())) {
                            completions.add(community);
                        }
                    }
                }
            }
        } else if (args.length == 2) {
            // 第2引数: コミュニティID
            if (cmdName.equals("co-invite") || cmdName.equals("co-cancel")) {
                if (sender instanceof Player player) {
                    List<String> communities = manager.getPlayerCommunities(player);
                    for (String community : communities) {
                        if (community.toLowerCase().startsWith(args[1].toLowerCase())) {
                            completions.add(community);
                        }
                    }
                }
            }
        }

        return completions;
    }
}
