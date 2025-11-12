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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;
import page.nafuchoco.mofu.mofuassistant.database.CommunityDistributionTable;

import java.sql.SQLException;
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
    private final Map<UUID, String> playerViewingCommunity;

    public DistributionGUI(MofuAssistant plugin, CommunityDistributionManager manager,
                          CommunityItemStorage storage, CommunityDistributionTable distributionTable) {
        this.plugin = plugin;
        this.manager = manager;
        this.storage = storage;
        this.distributionTable = distributionTable;
        this.playerViewingCommunity = new HashMap<>();
    }

    /**
     * プレイヤーにコミュニティ選択GUIを表示
     */
    public void openCommunitySelectionGUI(Player player) {
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
            int memberCount = manager.getCommunityMemberCount(communityName);
            int distributionAmount = manager.calculateDistributionAmount(memberCount);

            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatColor.GREEN + communityName);

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

        int memberCount = manager.getCommunityMemberCount(communityName);
        int totalAmount = manager.calculateDistributionAmount(memberCount);

        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE + " - " + communityName);

        // 配布アイテムを表示（クリックで受け取る）
        ItemStack displayItem = distributionItem.clone();
        displayItem.setAmount(1);
        ItemMeta meta = displayItem.getItemMeta();

        List<String> lore = meta != null && meta.hasLore() ? meta.getLore() : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "コミュニティ: " + ChatColor.WHITE + communityName);
        lore.add(ChatColor.GRAY + "メンバー数: " + ChatColor.WHITE + memberCount + "人");
        lore.add(ChatColor.GRAY + "受け取れる数: " + ChatColor.WHITE + totalAmount + "個");
        lore.add("");
        lore.add(ChatColor.YELLOW + "クリックして全て受け取る");

        if (meta != null) {
            meta.setLore(lore);
            displayItem.setItemMeta(meta);
        }

        inv.setItem(13, displayItem);
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
            ItemMeta meta = clicked.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String communityName = ChatColor.stripColor(meta.getDisplayName());
                player.closeInventory();
                openDistributionGUI(player, communityName);
            }
            return;
        }

        // 配布GUI
        if (title.startsWith(GUI_TITLE + " - ")) {
            String communityName = playerViewingCommunity.get(player.getUniqueId());
            if (communityName == null) {
                player.closeInventory();
                return;
            }

            if (event.getSlot() == 13) {
                giveItemToPlayer(player, communityName);
            }
        }
    }

    /**
     * プレイヤーにアイテムを配布
     */
    private void giveItemToPlayer(Player player, String communityName) {
        ItemStack distributionItem = storage.loadItem();
        if (distributionItem == null) {
            player.sendMessage(ChatColor.RED + "配布するアイテムが設定されていません。");
            player.closeInventory();
            return;
        }

        int memberCount = manager.getCommunityMemberCount(communityName);
        int totalAmount = manager.calculateDistributionAmount(memberCount);

        // アイテムをプレイヤーのインベントリに追加
        ItemStack giveItem = distributionItem.clone();
        int maxStackSize = giveItem.getMaxStackSize();
        int remaining = totalAmount;

        List<ItemStack> itemsToGive = new ArrayList<>();
        while (remaining > 0) {
            ItemStack stack = giveItem.clone();
            int amount = Math.min(remaining, maxStackSize);
            stack.setAmount(amount);
            itemsToGive.add(stack);
            remaining -= amount;
        }

        // インベントリに空きがあるかチェック
        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }

        if (emptySlots < itemsToGive.size()) {
            player.sendMessage(ChatColor.RED + "インベントリに十分な空きがありません。");
            return;
        }

        // アイテムを付与
        for (ItemStack item : itemsToGive) {
            player.getInventory().addItem(item);
        }

        // 配布履歴を記録
        try {
            distributionTable.updateDistribution(player.getUniqueId(), communityName, totalAmount);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "配布履歴の記録に失敗しました。", e);
        }

        player.sendMessage(ChatColor.GREEN + "コミュニティ「" + communityName + "」から " +
                          totalAmount + "個のアイテムを受け取りました。");
        player.closeInventory();
        playerViewingCommunity.remove(player.getUniqueId());
    }

    public void cleanup() {
        playerViewingCommunity.clear();
    }
}
