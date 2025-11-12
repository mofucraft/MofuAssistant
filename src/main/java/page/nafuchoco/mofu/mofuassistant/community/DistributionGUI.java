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

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;
import page.nafuchoco.mofu.mofuassistant.community.CommunityDistributionData;
import page.nafuchoco.mofu.mofuassistant.database.CommunityDistributionTable;
import page.nafuchoco.mofu.mofuassistant.database.CommunityPoolTable;
import page.nafuchoco.mofu.mofuassistant.database.DistributionCycleTable;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

/**
 * コミュニティアイテム配布用のチェストGUI
 */
public class DistributionGUI implements Listener {
    private static final String GUI_TITLE = ChatColor.DARK_GREEN + "コミュニティアイテム配布";
    private final MofuAssistant plugin;
    private final CommunityDistributionManager manager;
    private final CommunityItemStorage storage;
    private final CommunityDistributionTable distributionTable;
    private final CommunityPoolTable poolTable;
    private final DistributionCycleTable cycleTable;
    private final Map<UUID, String> playerViewingCommunity;
    private final Map<UUID, Boolean> playerAwaitingAmountInput;
    private final Map<UUID, Integer> playerLogPage;

    public DistributionGUI(MofuAssistant plugin, CommunityDistributionManager manager,
                          CommunityItemStorage storage, CommunityDistributionTable distributionTable,
                          CommunityPoolTable poolTable, DistributionCycleTable cycleTable) {
        this.plugin = plugin;
        this.manager = manager;
        this.storage = storage;
        this.distributionTable = distributionTable;
        this.poolTable = poolTable;
        this.cycleTable = cycleTable;
        this.playerViewingCommunity = new HashMap<>();
        this.playerAwaitingAmountInput = new HashMap<>();
        this.playerLogPage = new HashMap<>();
    }

    /**
     * プレイヤーにコミュニティ選択GUIを表示
     */
    public void openCommunitySelectionGUI(Player player) {
        // 配布サイクルのチェック
        try {
            DistributionCycle activeCycle = cycleTable.getActiveCycle();
            if (activeCycle == null || !activeCycle.isCurrentlyValid()) {
                player.sendMessage(ChatColor.RED + "現在は配布期間外です。");
                player.sendMessage(ChatColor.YELLOW + "次の配布期間をお待ちください。");
                return;
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "配布状態の確認中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "配布サイクルの確認に失敗しました。", e);
            return;
        }

        List<String> communities = manager.getPlayerCommunities(player);

        if (communities.isEmpty()) {
            player.sendMessage(ChatColor.RED + "あなたはどのコミュニティにも所属していません。");
            return;
        }

        if (!storage.hasItem()) {
            player.sendMessage(ChatColor.RED + "配布するアイテムが設定されていません。");
            return;
        }

        // コミュニティが1つだけの場合は直接配布GUIを開く
        if (communities.size() == 1) {
            openDistributionGUI(player, communities.get(0));
            return;
        }

        // 複数コミュニティの場合は選択GUI
        int size = Math.min(54, ((communities.size() + 8) / 9) * 9);
        Inventory inv = Bukkit.createInventory(null, size, GUI_TITLE + " - 選択");

        for (int i = 0; i < communities.size() && i < 54; i++) {
            String communityName = communities.get(i);
            String displayName = manager.getDisplayName(communityName);
            int memberCount = manager.getCommunityMemberCount(communityName);
            int distributionAmount = manager.calculateDistributionAmount(memberCount);

            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + displayName);

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "メンバー数: " + ChatColor.WHITE + memberCount + "人");
            lore.add(ChatColor.GRAY + "配布数: " + ChatColor.WHITE + distributionAmount + "個");
            lore.add("");
            lore.add(ChatColor.YELLOW + "クリックして受け取る");
            meta.setLore(lore);

            item.setItemMeta(meta);
            inv.setItem(i, item);
        }

        player.openInventory(inv);
    }

    /**
     * 特定コミュニティの配布GUIを開く
     */
    public void openDistributionGUI(Player player, String communityName) {
        if (!manager.isPlayerInCommunity(player, communityName)) {
            player.sendMessage(ChatColor.RED + "あなたはこのコミュニティに所属していません。");
            return;
        }

        ItemStack distributionItem = storage.loadItem();
        if (distributionItem == null) {
            player.sendMessage(ChatColor.RED + "配布するアイテムが設定されていません。");
            return;
        }

        // 現在のサイクル情報を取得
        DistributionCycle activeCycle;
        try {
            activeCycle = cycleTable.getActiveCycle();
            if (activeCycle == null || !activeCycle.isCurrentlyValid()) {
                player.sendMessage(ChatColor.RED + "現在は配布期間外です。");
                return;
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "配布状態の確認中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "配布サイクルの確認に失敗しました。", e);
            return;
        }

        // プール情報を取得
        int remainingAmount = 0;
        int totalAmount = 0;
        try {
            CommunityPool pool = poolTable.getPool(activeCycle.getCycleId(), communityName);
            if (pool != null) {
                remainingAmount = pool.getRemainingAmount();
                totalAmount = pool.getTotalAmount();
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "プール情報の取得中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "プール情報の取得に失敗しました。", e);
            return;
        }

        int memberCount = manager.getCommunityMemberCount(communityName);
        String displayName = manager.getDisplayName(communityName);

        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE + " - " + displayName);

        // 配布アイテムを表示
        ItemStack displayItem = distributionItem.clone();
        displayItem.setAmount(1);
        ItemMeta meta = displayItem.getItemMeta();

        List<String> lore = meta != null && meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "コミュニティ: " + ChatColor.WHITE + displayName);
        lore.add(ChatColor.GRAY + "メンバー数: " + ChatColor.WHITE + memberCount + "人");
        lore.add(ChatColor.GRAY + "総配布数: " + ChatColor.WHITE + totalAmount + "個");
        lore.add(ChatColor.GRAY + "残り: " + ChatColor.AQUA + remainingAmount + "個");

        if (meta != null) {
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        inv.setItem(4, displayItem);

        // 全て受け取るボタン
        ItemStack claimAllButton = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta claimAllMeta = claimAllButton.getItemMeta();
        if (claimAllMeta != null) {
            claimAllMeta.setDisplayName(ChatColor.GREEN + "全て受け取る");
            List<String> claimAllLore = new ArrayList<>();
            claimAllLore.add(ChatColor.GRAY + "残りの全てのアイテムを");
            claimAllLore.add(ChatColor.GRAY + "受け取ります");
            claimAllLore.add("");
            claimAllLore.add(ChatColor.YELLOW + "クリックして受け取る");
            claimAllMeta.setLore(claimAllLore);
            claimAllButton.setItemMeta(claimAllMeta);
        }
        inv.setItem(11, claimAllButton);

        // 個数を指定ボタン
        ItemStack customAmountButton = new ItemStack(Material.PAPER);
        ItemMeta customMeta = customAmountButton.getItemMeta();
        if (customMeta != null) {
            customMeta.setDisplayName(ChatColor.YELLOW + "個数を指定");
            List<String> customLore = new ArrayList<>();
            customLore.add(ChatColor.GRAY + "受け取る個数を");
            customLore.add(ChatColor.GRAY + "指定します");
            customLore.add("");
            customLore.add(ChatColor.YELLOW + "クリックして個数を入力");
            customMeta.setLore(customLore);
            customAmountButton.setItemMeta(customMeta);
        }
        inv.setItem(15, customAmountButton);

        // ログ確認ボタン
        ItemStack logButton = new ItemStack(Material.BOOK);
        ItemMeta logMeta = logButton.getItemMeta();
        if (logMeta != null) {
            logMeta.setDisplayName(ChatColor.AQUA + "受け取りログ");
            List<String> logLore = new ArrayList<>();
            logLore.add(ChatColor.GRAY + "このコミュニティの");
            logLore.add(ChatColor.GRAY + "受け取りログを確認します");
            logLore.add("");
            logLore.add(ChatColor.YELLOW + "クリックして確認");
            logMeta.setLore(logLore);
            logButton.setItemMeta(logMeta);
        }
        inv.setItem(22, logButton);

        playerViewingCommunity.put(player.getUniqueId(), communityName);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String title = event.getView().getTitle();
        if (!title.startsWith(GUI_TITLE)) {
            return;
        }

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        // コミュニティ選択GUI
        if (title.equals(GUI_TITLE + " - 選択")) {
            // クリックされたスロット番号から内部IDを取得
            int slot = event.getSlot();
            List<String> communities = manager.getPlayerCommunities(player);
            if (slot >= 0 && slot < communities.size()) {
                String communityName = communities.get(slot);
                player.closeInventory();
                openDistributionGUI(player, communityName);
            }
            return;
        }

        // ログGUI
        if (title.contains(" - ログ - ")) {
            String communityName = playerViewingCommunity.get(player.getUniqueId());
            if (communityName == null) {
                player.closeInventory();
                return;
            }

            int slot = event.getSlot();
            Integer currentPage = playerLogPage.get(player.getUniqueId());
            if (currentPage == null) {
                currentPage = 0;
            }

            if (slot == 48) {
                // 前のページ
                openDistributionLogGUI(player, communityName, currentPage - 1);
            } else if (slot == 50) {
                // 次のページ
                openDistributionLogGUI(player, communityName, currentPage + 1);
            } else if (slot == 49) {
                // 戻る
                player.closeInventory();
                openDistributionGUI(player, communityName);
            }
            return;
        }

        // 配布GUI
        if (title.startsWith(GUI_TITLE + " - ") && !title.contains(" - ログ - ")) {
            String communityName = playerViewingCommunity.get(player.getUniqueId());
            if (communityName == null) {
                player.closeInventory();
                return;
            }

            if (event.getSlot() == 11) {
                // 全て受け取る
                giveItemToPlayer(player, communityName, -1);
            } else if (event.getSlot() == 15) {
                // 個数を指定
                player.closeInventory();
                playerAwaitingAmountInput.put(player.getUniqueId(), true);
                player.sendMessage(ChatColor.GREEN + "チャットで受け取りたい個数を入力してください。");
                player.sendMessage(ChatColor.GRAY + "キャンセルする場合は「cancel」と入力してください。");
            } else if (event.getSlot() == 22) {
                // ログ確認
                player.closeInventory();
                openDistributionLogGUI(player, communityName, 0);
            }
        }
    }

    /**
     * プレイヤーにアイテムを配布
     * @param amount 受け取る個数（-1の場合は残り全て）
     */
    private void giveItemToPlayer(Player player, String communityName, int amount) {
        // 配布サイクルの再確認
        DistributionCycle activeCycle;
        try {
            activeCycle = cycleTable.getActiveCycle();
            if (activeCycle == null || !activeCycle.isCurrentlyValid()) {
                player.sendMessage(ChatColor.RED + "配布期間が終了しました。");
                player.closeInventory();
                return;
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "配布状態の確認中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "配布状態の確認に失敗しました。", e);
            player.closeInventory();
            return;
        }

        ItemStack distributionItem = storage.loadItem();
        if (distributionItem == null) {
            player.sendMessage(ChatColor.RED + "配布するアイテムが設定されていません。");
            player.closeInventory();
            return;
        }

        // プール情報を取得
        CommunityPool pool;
        try {
            pool = poolTable.getPool(activeCycle.getCycleId(), communityName);
            if (pool == null || pool.getRemainingAmount() <= 0) {
                player.sendMessage(ChatColor.RED + "配布可能なアイテムが残っていません。");
                player.closeInventory();
                return;
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "プール情報の取得中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "プール情報の取得に失敗しました。", e);
            player.closeInventory();
            return;
        }

        // 受け取る個数を決定（-1の場合は残り全て）
        int claimAmount = amount == -1 ? pool.getRemainingAmount() : amount;

        if (claimAmount <= 0) {
            player.sendMessage(ChatColor.RED + "受け取る個数は1以上を指定してください。");
            return;
        }

        if (claimAmount > pool.getRemainingAmount()) {
            player.sendMessage(ChatColor.RED + "指定された個数がプールの残量を超えています。");
            player.sendMessage(ChatColor.YELLOW + "残り: " + pool.getRemainingAmount() + "個");
            return;
        }

        // インベントリの空き容量を計算
        ItemStack giveItem = distributionItem.clone();
        int maxStackSize = giveItem.getMaxStackSize();
        int requiredSlots = (claimAmount + maxStackSize - 1) / maxStackSize; // 切り上げ

        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }

        // インベントリに入る分だけを計算
        int actualClaimAmount = claimAmount;
        if (emptySlots < requiredSlots) {
            actualClaimAmount = emptySlots * maxStackSize;
            if (actualClaimAmount <= 0) {
                player.sendMessage(ChatColor.RED + "インベントリに空きがありません。");
                return;
            }
            player.sendMessage(ChatColor.YELLOW + "インベントリの空きが不足しているため、" + actualClaimAmount + "個のみ受け取ります。");
        }

        // プールから取得を試みる
        boolean claimed;
        try {
            claimed = poolTable.claimFromPool(activeCycle.getCycleId(), communityName, actualClaimAmount);
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "アイテムの取得中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "プールからの取得に失敗しました。", e);
            player.closeInventory();
            return;
        }

        if (!claimed) {
            player.sendMessage(ChatColor.RED + "プールの残量が不足しています。");
            player.sendMessage(ChatColor.YELLOW + "他のプレイヤーが先に受け取った可能性があります。");
            player.closeInventory();
            return;
        }

        // アイテムをプレイヤーのインベントリに追加
        List<ItemStack> itemsToGive = new ArrayList<>();
        int remaining = actualClaimAmount;
        while (remaining > 0) {
            ItemStack stack = giveItem.clone();
            int stackAmount = Math.min(remaining, maxStackSize);
            stack.setAmount(stackAmount);
            itemsToGive.add(stack);
            remaining -= stackAmount;
        }

        // アイテムを付与
        for (ItemStack item : itemsToGive) {
            player.getInventory().addItem(item);
        }

        // 配布履歴を記録
        try {
            distributionTable.addClaim(activeCycle.getCycleId(), player.getUniqueId(), communityName, actualClaimAmount);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "配布履歴の記録に失敗しました。", e);
        }

        // 残量を取得
        int newRemaining = 0;
        try {
            CommunityPool updatedPool = poolTable.getPool(activeCycle.getCycleId(), communityName);
            if (updatedPool != null) {
                newRemaining = updatedPool.getRemainingAmount();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "残量の取得に失敗しました。", e);
        }

        String displayName = manager.getDisplayName(communityName);
        player.sendMessage(ChatColor.GREEN + "コミュニティ「" + displayName + "」から " +
                          actualClaimAmount + "個のアイテムを受け取りました。");
        player.sendMessage(ChatColor.GRAY + "プールの残り: " + newRemaining + "個");
        player.closeInventory();
        playerViewingCommunity.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        if (!playerAwaitingAmountInput.containsKey(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        playerAwaitingAmountInput.remove(player.getUniqueId());

        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            player.sendMessage(ChatColor.YELLOW + "個数指定をキャンセルしました。");
            return;
        }

        String communityName = playerViewingCommunity.get(player.getUniqueId());
        if (communityName == null) {
            player.sendMessage(ChatColor.RED + "コミュニティ情報が見つかりません。");
            return;
        }

        int amount;
        try {
            amount = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "数値を入力してください。");
            return;
        }

        if (amount <= 0) {
            player.sendMessage(ChatColor.RED + "1以上の数値を入力してください。");
            return;
        }

        // 配布処理を同期タスクで実行（チャットイベントは非同期なため）
        Bukkit.getScheduler().runTask(plugin, () -> giveItemToPlayer(player, communityName, amount));
    }

    /**
     * コミュニティの受け取りログGUIを開く
     */
    public void openDistributionLogGUI(Player player, String communityName, int page) {
        if (!manager.isPlayerInCommunity(player, communityName)) {
            player.sendMessage(ChatColor.RED + "あなたはこのコミュニティに所属していません。");
            return;
        }

        // 現在のサイクル情報を取得
        DistributionCycle activeCycle;
        try {
            activeCycle = cycleTable.getActiveCycle();
            if (activeCycle == null) {
                player.sendMessage(ChatColor.RED + "現在、アクティブな配布サイクルはありません。");
                return;
            }
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "配布状態の確認中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "配布サイクルの確認に失敗しました。", e);
            return;
        }

        // コミュニティのログを取得
        List<CommunityDistributionData> logs;
        try {
            logs = distributionTable.getCommunityDistributions(activeCycle.getCycleId(), communityName);
            // 新しい順にソート
            logs.sort((a, b) -> b.getLastClaimTime().compareTo(a.getLastClaimTime()));
        } catch (SQLException e) {
            player.sendMessage(ChatColor.RED + "ログの取得中にエラーが発生しました。");
            plugin.getLogger().log(Level.SEVERE, "ログの取得に失敗しました。", e);
            return;
        }

        if (logs.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "このコミュニティの受け取りログはまだありません。");
            return;
        }

        String displayName = manager.getDisplayName(communityName);

        // ページネーション
        int logsPerPage = 45; // 5行 x 9列 = 45スロット
        int totalPages = (int) Math.ceil((double) logs.size() / logsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));

        int startIndex = page * logsPerPage;
        int endIndex = Math.min(startIndex + logsPerPage, logs.size());

        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE + " - ログ - " + displayName);

        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd HH:mm:ss");

        // ログアイテムを表示
        for (int i = startIndex; i < endIndex; i++) {
            CommunityDistributionData data = logs.get(i);
            ItemStack logItem = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta logMeta = logItem.getItemMeta();

            if (logMeta != null) {
                String playerName = Bukkit.getOfflinePlayer(data.getPlayerId()).getName();
                if (playerName == null) {
                    playerName = data.getPlayerId().toString().substring(0, 8);
                }

                logMeta.setDisplayName(ChatColor.YELLOW + playerName);
                List<String> logLore = new ArrayList<>();
                logLore.add(ChatColor.GRAY + "受取数: " + ChatColor.WHITE + data.getClaimedAmount() + "個");
                logLore.add(ChatColor.GRAY + "最終受取: " + ChatColor.WHITE +
                           dateFormat.format(data.getLastClaimTime()));
                logMeta.setLore(logLore);
                logItem.setItemMeta(logMeta);
            }

            inv.setItem(i - startIndex, logItem);
        }

        // ナビゲーションボタン
        if (page > 0) {
            // 前のページボタン
            ItemStack prevButton = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevButton.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(ChatColor.GREEN + "前のページ");
                prevButton.setItemMeta(prevMeta);
            }
            inv.setItem(48, prevButton);
        }

        if (page < totalPages - 1) {
            // 次のページボタン
            ItemStack nextButton = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.GREEN + "次のページ");
                nextButton.setItemMeta(nextMeta);
            }
            inv.setItem(50, nextButton);
        }

        // 戻るボタン
        ItemStack backButton = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backButton.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "戻る");
            backButton.setItemMeta(backMeta);
        }
        inv.setItem(49, backButton);

        // ページ情報
        ItemStack pageInfo = new ItemStack(Material.PAPER);
        ItemMeta pageInfoMeta = pageInfo.getItemMeta();
        if (pageInfoMeta != null) {
            pageInfoMeta.setDisplayName(ChatColor.AQUA + "ページ " + (page + 1) + "/" + totalPages);
            List<String> pageInfoLore = new ArrayList<>();
            pageInfoLore.add(ChatColor.GRAY + "総ログ数: " + ChatColor.WHITE + logs.size());
            pageInfoMeta.setLore(pageInfoLore);
            pageInfo.setItemMeta(pageInfoMeta);
        }
        inv.setItem(53, pageInfo);

        playerViewingCommunity.put(player.getUniqueId(), communityName);
        playerLogPage.put(player.getUniqueId(), page);
        player.openInventory(inv);
    }

    public void cleanup() {
        playerViewingCommunity.clear();
        playerAwaitingAmountInput.clear();
        playerLogPage.clear();
    }
}
