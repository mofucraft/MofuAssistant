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
    private static final String DISTRIBUTION_PERMISSION = "osusowaken.enable";
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
     * osusowaken.enableパーミッションを持つグループに所属しているかをチェック
     */
    public List<String> getPlayerCommunities(Player player) {
        if (!isLuckPermsAvailable()) {
            return Collections.emptyList();
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return Collections.emptyList();
        }

        // プレイヤーが所属するグループの中で、osusowaken.enableパーミッションを持つグループを抽出
        List<String> communities = new ArrayList<>();
        for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
            // グループがosusowaken.enableパーミッションを持っているかチェック
            boolean hasPermission = group.getCachedData().getPermissionData().checkPermission(DISTRIBUTION_PERMISSION).asBoolean();
            if (hasPermission) {
                // プレイヤーがこのグループに所属しているかチェック
                if (user.getInheritedGroups(user.getQueryOptions()).contains(group)) {
                    communities.add(group.getName());
                }
            }
        }

        return communities;
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
            // searchAll()はMap<UUID, Collection<Node>>を返すので、keySet()でUUIDのセットを取得
            return luckPerms.getUserManager().searchAll(
                    NodeMatcher.key(InheritanceNode.builder(communityName).build())
            ).join().keySet().size();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get community member count for " + communityName, e);
            return 0;
        }
    }

    /**
     * 全てのコミュニティグループを取得
     * osusowaken.enableパーミッションを持つグループを抽出
     */
    public Set<String> getAllCommunities() {
        if (!isLuckPermsAvailable()) {
            return Collections.emptySet();
        }

        Set<String> communities = new HashSet<>();

        // 全てのグループからosusowaken.enableパーミッションを持つグループを抽出
        for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
            // グループがosusowaken.enableパーミッションを持っているかチェック
            boolean hasPermission = group.getCachedData().getPermissionData().checkPermission(DISTRIBUTION_PERMISSION).asBoolean();
            if (hasPermission) {
                communities.add(group.getName());
            }
        }

        return communities;
    }

    /**
     * メンバー数に基づいて配布すべきアイテム数を計算
     * 1人: 160枚
     * 2人: 256枚 (+96枚)
     * 3人: 320枚 (+64枚)
     * 4～99人: +1人につき+32枚
     * 100人以降: +1人につき+16枚
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
        } else if (memberCount < 100) {
            // 4～99人: 320 + (memberCount - 3) * 32
            return 320 + (memberCount - 3) * 32;
        } else {
            // 100人以降: 320 + (99 - 3) * 32 + (memberCount - 99) * 16
            // = 320 + 96 * 32 + (memberCount - 99) * 16
            // = 320 + 3072 + (memberCount - 99) * 16
            // = 3392 + (memberCount - 99) * 16
            return 3392 + (memberCount - 99) * 16;
        }
    }

    /**
     * プレイヤーが特定のコミュニティに所属しているかチェック
     */
    public boolean isPlayerInCommunity(Player player, String communityName) {
        return getPlayerCommunities(player).contains(communityName);
    }

    /**
     * コミュニティの表示名を取得
     * displayname.[名前]パーミッションがあればそれを優先、なければグループ名を返す
     * @param groupName グループの内部ID
     * @return 表示名
     */
    public String getDisplayName(String groupName) {
        if (!isLuckPermsAvailable()) {
            return groupName;
        }

        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            return groupName;
        }

        // displayname.* パーミッションを検索
        String displayNamePrefix = "displayname.";
        for (var node : group.getNodes()) {
            String permission = node.getKey();
            if (permission.startsWith(displayNamePrefix)) {
                // displayname.[名前] の [名前] 部分を取得
                return permission.substring(displayNamePrefix.length());
            }
        }

        // displayname パーミッションがない場合はグループ名をそのまま返す
        return groupName;
    }
}
