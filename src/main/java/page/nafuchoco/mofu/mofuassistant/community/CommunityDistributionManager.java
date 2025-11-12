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
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * LuckPermsと統合してコミュニティグループを管理するクラス
 */
public class CommunityDistributionManager {
    private static final String COMMUNITY_GROUP_PREFIX = "community.";
    private final MofuAssistant plugin;
    private LuckPerms luckPerms;

    public CommunityDistributionManager(MofuAssistant plugin) {
        this.plugin = plugin;
        initializeLuckPerms();
    }

    private void initializeLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            plugin.getLogger().log(Level.INFO, "LuckPerms統合が有効になりました。");
        } else {
            plugin.getLogger().log(Level.WARNING, "LuckPermsが見つかりません。コミュニティ配布機能は動作しません。");
        }
    }

    public boolean isLuckPermsAvailable() {
        return luckPerms != null;
    }

    /**
     * プレイヤーが所属する全てのコミュニティグループを取得
     */
    public List<String> getPlayerCommunities(Player player) {
        if (!isLuckPermsAvailable()) {
            return Collections.emptyList();
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return Collections.emptyList();
        }

        // getCachedDataを使用して、グループから継承されたパーミッションも含めて取得
        return user.getCachedData().getPermissionData().getPermissionMap().keySet().stream()
                .filter(permission -> permission.startsWith(COMMUNITY_GROUP_PREFIX))
                .map(permission -> permission.substring(COMMUNITY_GROUP_PREFIX.length()))
                .collect(Collectors.toList());
    }

    /**
     * 特定のコミュニティに所属するプレイヤー数を取得（オフラインも含む）
     */
    public int getCommunityMemberCount(String communityName) {
        if (!isLuckPermsAvailable()) {
            return 0;
        }

        try {
            // グループを取得
            Group group = luckPerms.getGroupManager().getGroup(communityName);
            if (group == null) {
                plugin.getLogger().log(Level.WARNING, "Community group not found: " + communityName);
                return 0;
            }

            // このグループを継承しているユーザーをLuckPermsのストレージから検索
            // オフラインプレイヤーも含めて取得
            Set<UUID> members = luckPerms.getUserManager().searchAll(
                    NodeMatcher.key(InheritanceNode.builder(communityName).build())
            ).join();

            return members.size();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get community member count for " + communityName, e);
            return 0;
        }
    }

    /**
     * 全てのコミュニティグループを取得
     */
    public Set<String> getAllCommunities() {
        if (!isLuckPermsAvailable()) {
            return Collections.emptySet();
        }

        Set<String> communities = new HashSet<>();

        // 全てのグループからcommunity.*パーミッションを持つグループを抽出
        for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
            // グループのノードからcommunity.*パーミッションを探す
            group.getNodes().stream()
                    .filter(node -> node.getKey().startsWith(COMMUNITY_GROUP_PREFIX))
                    .forEach(node -> {
                        String communityName = node.getKey().substring(COMMUNITY_GROUP_PREFIX.length());
                        communities.add(communityName);
                    });
        }

        return communities;
    }

    /**
     * メンバー数に基づいて配布すべきアイテム数を計算
     * 1人: 160枚
     * 2人: 256枚 (+96枚)
     * 3人: 320枚 (+64枚)
     * 4人以降: +1人につき+32枚
     */
    public int calculateDistributionAmount(int memberCount) {
        if (memberCount <= 0) {
            return 0;
        } else if (memberCount == 1) {
            return 160;
        } else if (memberCount == 2) {
            return 256;
        } else if (memberCount == 3) {
            return 320;
        } else {
            // 4人以降: 320 + (memberCount - 3) * 32
            return 320 + (memberCount - 3) * 32;
        }
    }

    /**
     * プレイヤーが特定のコミュニティに所属しているかチェック
     */
    public boolean isPlayerInCommunity(Player player, String communityName) {
        return getPlayerCommunities(player).contains(communityName);
    }
}
