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
import java.util.HashMap;
import java.util.Map;
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
    private final DiscordWebhookNotifier webhookNotifier;
    private BukkitTask schedulerTask;

    public DistributionScheduler(MofuAssistant plugin, DistributionCycleTable cycleTable,
                                CommunityPoolTable poolTable, CommunityDistributionManager communityManager) {
        this.plugin = plugin;
        this.cycleTable = cycleTable;
        this.poolTable = poolTable;
        this.communityManager = communityManager;

        // Discord Webhook設定を読み込み
        String webhookUrl = plugin.getConfig().getString("discord.webhookUrl", "");
        boolean enableNotifications = plugin.getConfig().getBoolean("discord.enableNotifications", true);
        this.webhookNotifier = new DiscordWebhookNotifier(plugin, webhookUrl, enableNotifications);
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

        // 一時停止中の場合は何もしない
        if (activeCycle.isPaused()) {
            plugin.getLogger().log(Level.INFO, "配布サイクルは一時停止中です。");
            return;
        }

        // 未来のサイクル（まだ開始していない）場合は待機
        if (activeCycle.isFuture()) {
            plugin.getLogger().log(Level.INFO, "配布サイクルは未来の日時に設定されています。開始時刻: " + activeCycle.getFormattedStartTime());
            return;
        }

        // 現在時刻がサイクルの終了時刻を過ぎている場合のみ新しいサイクルを作成
        if (activeCycle.isExpired()) {
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
        Map<String, Integer> distributions = initializePools(cycleId);

        // Discord通知を送信（表示名に変換）
        webhookNotifier.sendDistributionStartNotification(
                "定期配布",
                newCycle.getFormattedStartTime(),
                newCycle.getFormattedEndTime(),
                convertToDisplayNames(distributions)
        );

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.GREEN + "[おすそわ券配布] 新しい配布サイクルが開始されました。");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "開始時刻: " + newCycle.getFormattedStartTime());
            Bukkit.broadcastMessage(ChatColor.YELLOW + "終了時刻: " + newCycle.getFormattedEndTime());
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
        Map<String, Integer> distributions = initializePools(cycleId);

        plugin.getLogger().log(Level.INFO, "配布サイクルを更新しました。新しいサイクルID: " + cycleId);

        // Discord通知を送信（表示名に変換）
        webhookNotifier.sendDistributionStartNotification(
                "定期配布",
                newCycle.getFormattedStartTime(),
                newCycle.getFormattedEndTime(),
                convertToDisplayNames(distributions)
        );

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.RED + "[おすそわ券配布] 前回の配布期間が終了しました。");
            Bukkit.broadcastMessage(ChatColor.RED + "未回収のアイテムは破棄されました。");
            Bukkit.broadcastMessage(ChatColor.GREEN + "[おすそわ券配布] 新しい配布サイクルが開始されました。");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "次の配布期間: " + newCycle.getFormattedStartTime() + " ～ " + newCycle.getFormattedEndTime());
        });
    }

    /**
     * 手動で配布サイクルを開始（即座に開始）
     */
    public void startManualCycle() throws SQLException {
        startManualCycle(null);
    }

    /**
     * 手動で配布サイクルを開始
     * @param startDateTime 開始日時（nullの場合は即座に開始）
     */
    public void startManualCycle(LocalDateTime startDateTime) throws SQLException {
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

        // サイクルを作成
        DistributionCycle newCycle;
        String cycleType;
        if (startDateTime == null) {
            // 今すぐ開始するサイクルを作成
            newCycle = DistributionCycle.createImmediateCycle();
            cycleType = "手動配布";
        } else {
            // 指定日時から開始するサイクルを作成
            newCycle = DistributionCycle.createScheduledCycle(startDateTime);
            cycleType = "予約配布";
        }

        int cycleId = cycleTable.createCycle(newCycle);

        // 新しいプールを初期化
        Map<String, Integer> distributions = initializePools(cycleId);

        plugin.getLogger().log(Level.INFO, cycleType + "サイクルを開始しました。サイクルID: " + cycleId);

        // Discord通知を送信（表示名に変換）
        webhookNotifier.sendDistributionStartNotification(
                cycleType,
                newCycle.getFormattedStartTime(),
                newCycle.getFormattedEndTime(),
                convertToDisplayNames(distributions)
        );

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (startDateTime == null) {
                Bukkit.broadcastMessage(ChatColor.GREEN + "[おすそわ券配布] 手動配布が開始されました。");
            } else {
                Bukkit.broadcastMessage(ChatColor.GREEN + "[おすそわ券配布] 配布が予約されました。");
                Bukkit.broadcastMessage(ChatColor.YELLOW + "開始予定: " + newCycle.getFormattedStartTime());
            }
            Bukkit.broadcastMessage(ChatColor.YELLOW + "配布期間: " + newCycle.getFormattedStartTime() + " ～ " + newCycle.getFormattedEndTime());
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
            Bukkit.broadcastMessage(ChatColor.RED + "[おすそわ券配布] 配布期間が終了しました。");
            Bukkit.broadcastMessage(ChatColor.RED + "未回収のアイテムは破棄されました。");
        });
    }

    /**
     * 全コミュニティのプールを初期化
     * @return コミュニティ名と配布数のマップ
     */
    private Map<String, Integer> initializePools(int cycleId) throws SQLException {
        Set<String> communities = communityManager.getAllCommunities();
        Map<String, Integer> distributions = new HashMap<>();

        for (String communityName : communities) {
            int memberCount = communityManager.getCommunityMemberCount(communityName);
            int totalAmount = communityManager.calculateDistributionAmount(memberCount);

            poolTable.createOrResetPool(cycleId, communityName, totalAmount);
            distributions.put(communityName, totalAmount);
            plugin.getLogger().log(Level.INFO, "コミュニティ「" + communityName + "」のプールを初期化しました。総配布数: " + totalAmount);
        }

        return distributions;
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

    /**
     * 配布サイクルを一時停止
     */
    public void pauseCycle() throws SQLException {
        DistributionCycle activeCycle = cycleTable.getActiveCycle();
        if (activeCycle == null) {
            throw new IllegalStateException("アクティブな配布サイクルがありません。");
        }

        if (activeCycle.isPaused()) {
            throw new IllegalStateException("配布サイクルは既に一時停止中です。");
        }

        cycleTable.pauseCycle(activeCycle.getCycleId());
        plugin.getLogger().log(Level.INFO, "配布サイクルを一時停止しました。サイクルID: " + activeCycle.getCycleId());

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.YELLOW + "[おすそわ券配布] 配布サイクルが一時停止されました。");
        });
    }

    /**
     * 配布サイクルを再開
     */
    public void resumeCycle() throws SQLException {
        DistributionCycle activeCycle = cycleTable.getActiveCycle();
        if (activeCycle == null) {
            throw new IllegalStateException("アクティブな配布サイクルがありません。");
        }

        if (!activeCycle.isPaused()) {
            throw new IllegalStateException("配布サイクルは一時停止していません。");
        }

        cycleTable.resumeCycle(activeCycle.getCycleId());
        plugin.getLogger().log(Level.INFO, "配布サイクルを再開しました。サイクルID: " + activeCycle.getCycleId());

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.GREEN + "[おすそわ券配布] 配布サイクルが再開されました。");
        });
    }

    /**
     * 配布サイクルを1週間早める
     */
    public void advanceCycle() throws SQLException {
        DistributionCycle activeCycle = cycleTable.getActiveCycle();
        if (activeCycle == null) {
            throw new IllegalStateException("アクティブな配布サイクルがありません。");
        }

        // 新しい開始時刻と終了時刻を計算（1週間早める）
        LocalDateTime currentStart = activeCycle.getStartTime().toLocalDateTime();
        LocalDateTime currentEnd = activeCycle.getEndTime().toLocalDateTime();

        LocalDateTime newStart = currentStart.minusWeeks(1);
        LocalDateTime newEnd = currentEnd.minusWeeks(1);

        // 過去の日時になっていないかチェック
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));
        if (newStart.isBefore(now)) {
            throw new IllegalStateException("1週間早めると開始時刻が過去になってしまいます。");
        }

        // サイクルの日時を更新
        Timestamp newStartTime = Timestamp.valueOf(newStart);
        Timestamp newEndTime = Timestamp.valueOf(newEnd);
        cycleTable.updateCycleTimes(activeCycle.getCycleId(), newStartTime, newEndTime);

        plugin.getLogger().log(Level.INFO, "配布サイクルを1週間早めました。サイクルID: " + activeCycle.getCycleId());

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.GREEN + "[おすそわ券配布] 配布サイクルを1週間早めました。");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "新しい開始時刻: " + newStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
            Bukkit.broadcastMessage(ChatColor.YELLOW + "新しい終了時刻: " + newEnd.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        });
    }

    /**
     * 配布サイクルを1週間遅くする
     */
    public void delayCycle() throws SQLException {
        DistributionCycle activeCycle = cycleTable.getActiveCycle();
        if (activeCycle == null) {
            throw new IllegalStateException("アクティブな配布サイクルがありません。");
        }

        // 新しい開始時刻と終了時刻を計算（1週間遅くする）
        LocalDateTime currentStart = activeCycle.getStartTime().toLocalDateTime();
        LocalDateTime currentEnd = activeCycle.getEndTime().toLocalDateTime();

        LocalDateTime newStart = currentStart.plusWeeks(1);
        LocalDateTime newEnd = currentEnd.plusWeeks(1);

        // サイクルの日時を更新
        Timestamp newStartTime = Timestamp.valueOf(newStart);
        Timestamp newEndTime = Timestamp.valueOf(newEnd);
        cycleTable.updateCycleTimes(activeCycle.getCycleId(), newStartTime, newEndTime);

        plugin.getLogger().log(Level.INFO, "配布サイクルを1週間遅くしました。サイクルID: " + activeCycle.getCycleId());

        // オンラインプレイヤーに通知
        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.broadcastMessage(ChatColor.GREEN + "[おすそわ券配布] 配布サイクルを1週間遅くしました。");
            Bukkit.broadcastMessage(ChatColor.YELLOW + "新しい開始時刻: " + newStart.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
            Bukkit.broadcastMessage(ChatColor.YELLOW + "新しい終了時刻: " + newEnd.format(java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")));
        });
    }

    /**
     * 内部IDから表示名へのマップに変換
     */
    private Map<String, Integer> convertToDisplayNames(Map<String, Integer> distributions) {
        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : distributions.entrySet()) {
            String displayName = communityManager.getDisplayName(entry.getKey());
            result.put(displayName, entry.getValue());
        }
        return result;
    }
}
