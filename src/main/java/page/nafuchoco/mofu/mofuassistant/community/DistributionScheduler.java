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
import org.bukkit.scheduler.BukkitTask;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;
import page.nafuchoco.mofu.mofuassistant.database.CommunityPoolTable;
import page.nafuchoco.mofu.mofuassistant.database.DistributionCycleTable;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.logging.Level;

/**
 * 配布サイクルの自動管理を行うスケジューラー
 */
public class DistributionScheduler {
    private static final int CHECK_INTERVAL_MINUTES = 5; // 5分ごとにチェック
    private final MofuAssistant plugin;
    private final DistributionCycleTable cycleTable;
    private final CommunityPoolTable poolTable;
    private final CommunityDistributionManager communityManager;
    private BukkitTask schedulerTask;

    public DistributionScheduler(MofuAssistant plugin, DistributionCycleTable cycleTable,
                                CommunityPoolTable poolTable, CommunityDistributionManager communityManager) {
        this.plugin = plugin;
        this.cycleTable = cycleTable;
        this.poolTable = poolTable;
        this.communityManager = communityManager;
    }

    /**
     * スケジューラーを開始
     */
    public void start() {
        // 20 ticks = 1秒, 20 * 60 * 5 = 6000 ticks = 5分
        long intervalTicks = 20L * 60 * CHECK_INTERVAL_MINUTES;

        schedulerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                checkAndUpdateCycle();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "配布サイクルのチェック中にエラーが発生しました。", e);
            }
        }, 20L, intervalTicks); // 起動後1秒で最初のチェック、以降5分ごと

        plugin.getLogger().log(Level.INFO, "配布サイクルスケジューラーを開始しました。");
    }

    /**
     * スケジューラーを停止
     */
    public void stop() {
        if (schedulerTask != null && !schedulerTask.isCancelled()) {
            schedulerTask.cancel();
            plugin.getLogger().log(Level.INFO, "配布サイクルスケジューラーを停止しました。");
        }
    }

    /**
     * 現在のサイクルをチェックし、必要に応じて更新
     */
    public void checkAndUpdateCycle() throws SQLException {
        DistributionCycle activeCycle = cycleTable.getActiveCycle();

        // アクティブなサイクルがない場合は初期サイクルを作成
        if (activeCycle == null) {
            createInitialCycle();
            return;
        }

        // 現在時刻がサイクルの終了時刻を過ぎている場合
        if (!activeCycle.isCurrentlyValid()) {
            endCurrentCycleAndStartNew();
        }
    }

    /**
     * 初期サイクルを作成
     */
    private void createInitialCycle() throws SQLException {
        DistributionCycle newCycle = DistributionCycle.createNewCycle();
        int cycleId = cycleTable.createCycle(newCycle);
        plugin.getLogger().log(Level.INFO, "初期配布サイクルを作成しました。ID: " + cycleId);

        // 全コミュニティのプールを初期化
        initializePools(cycleId);

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.GREEN + "[配布システム] 新しい配布サイクルが開始されました。");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "開始時刻: " + newCycle.getStartTime());
            Bukkit.broadcastMessage(ChatColor.YELLOW + "終了時刻: " + newCycle.getEndTime());
        });
    }

    /**
     * 現在のサイクルを終了し、新しいサイクルを開始
     */
    private void endCurrentCycleAndStartNew() throws SQLException {
        // 現在のサイクルIDを取得
        DistributionCycle currentCycle = cycleTable.getActiveCycle();
        int oldCycleId = currentCycle != null ? currentCycle.getCycleId() : -1;

        // 古いプールをクリア
        if (oldCycleId != -1) {
            poolTable.clearPoolsForCycle(oldCycleId);
        }

        // 現在のサイクルを無効化
        cycleTable.deactivateAllCycles();

        // 配布履歴をリセット
        String distributionTableName = plugin.getMofuAssistantTable().getTablename().replace("playerdata", "community_distribution");
        cycleTable.resetDistributionHistory(distributionTableName);

        // 新しいサイクルを作成
        DistributionCycle newCycle = DistributionCycle.createNewCycle();
        int cycleId = cycleTable.createCycle(newCycle);

        // 新しいプールを初期化
        initializePools(cycleId);

        plugin.getLogger().log(Level.INFO, "配布サイクルを更新しました。新しいサイクルID: " + cycleId);

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.RED + "[配布システム] 前回の配布期間が終了しました。");
            Bukkit.broadcastMessage(ChatColor.RED + "未回収のアイテムは破棄されました。");
            Bukkit.broadcastMessage(ChatColor.GREEN + "[配布システム] 新しい配布サイクルが開始されました。");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "次の配布期間: " + newCycle.getStartTime() + " ～ " + newCycle.getEndTime());
        });
    }

    /**
     * 手動で配布サイクルを開始
     */
    public void startManualCycle() throws SQLException {
        // 古いサイクルのプールをクリア
        DistributionCycle oldCycle = cycleTable.getActiveCycle();
        if (oldCycle != null) {
            poolTable.clearPoolsForCycle(oldCycle.getCycleId());
        }

        // 既存のアクティブなサイクルを無効化
        cycleTable.deactivateAllCycles();

        // 配布履歴をリセット
        String distributionTableName = plugin.getMofuAssistantTable().getTablename().replace("playerdata", "community_distribution");
        cycleTable.resetDistributionHistory(distributionTableName);

        // 今すぐ開始するサイクルを作成
        DistributionCycle newCycle = DistributionCycle.createImmediateCycle();
        int cycleId = cycleTable.createCycle(newCycle);

        // 新しいプールを初期化
        initializePools(cycleId);

        plugin.getLogger().log(Level.INFO, "手動で配布サイクルを開始しました。サイクルID: " + cycleId);

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.GREEN + "[配布システム] 手動配布が開始されました。");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "配布期間: " + newCycle.getStartTime() + " ～ " + newCycle.getEndTime());
        });
    }

    /**
     * 現在のサイクルを手動で終了
     */
    public void endManualCycle() throws SQLException {
        DistributionCycle activeCycle = cycleTable.getActiveCycle();
        if (activeCycle == null) {
            throw new IllegalStateException("アクティブな配布サイクルがありません。");
        }

        // プールをクリア
        poolTable.clearPoolsForCycle(activeCycle.getCycleId());

        // 現在のサイクルを無効化
        cycleTable.deactivateAllCycles();

        // 配布履歴をリセット
        String distributionTableName = plugin.getMofuAssistantTable().getTablename().replace("playerdata", "community_distribution");
        cycleTable.resetDistributionHistory(distributionTableName);

        plugin.getLogger().log(Level.INFO, "手動で配布サイクルを終了しました。");

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.RED + "[配布システム] 配布期間が終了しました。");
            Bukkit.broadcastMessage(ChatColor.RED + "未回収のアイテムは破棄されました。");
        });
    }

    /**
     * 全コミュニティのプールを初期化
     */
    private void initializePools(int cycleId) throws SQLException {
        Set<String> communities = communityManager.getAllCommunities();

        for (String communityName : communities) {
            int memberCount = communityManager.getCommunityMemberCount(communityName);
            int totalAmount = communityManager.calculateDistributionAmount(memberCount);

            poolTable.createOrResetPool(cycleId, communityName, totalAmount);
            plugin.getLogger().log(Level.INFO, "コミュニティ「" + communityName + "」のプールを初期化しました。総配布数: " + totalAmount);
        }
    }

    /**
     * 次の配布予定時刻を取得
     */
    public LocalDateTime getNextDistributionTime() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));

        // 次の土曜日15時を計算
        LocalDateTime nextSaturday = now.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.SATURDAY))
                .withHour(15)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        // 今日が土曜日で15時前なら今日の15時
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY && now.getHour() < 15) {
            return now.withHour(15).withMinute(0).withSecond(0).withNano(0);
        }

        return nextSaturday;
    }
}
