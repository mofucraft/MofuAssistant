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

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * 配布サイクル情報を管理するクラス
 */
public class DistributionCycle {
    private final int cycleId;
    private final Timestamp startTime;
    private final Timestamp endTime;
    private final boolean active;

    public DistributionCycle(int cycleId, Timestamp startTime, Timestamp endTime, boolean active) {
        this.cycleId = cycleId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.active = active;
    }

    public int getCycleId() {
        return cycleId;
    }

    public Timestamp getStartTime() {
        return startTime;
    }

    public Timestamp getEndTime() {
        return endTime;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * 現在時刻がこのサイクル内かチェック
     */
    public boolean isCurrentlyValid() {
        if (!active) {
            return false;
        }
        Timestamp now = new Timestamp(System.currentTimeMillis());
        return now.after(startTime) && now.before(endTime);
    }

    /**
     * 次の配布開始時刻を計算（隔週土曜日15:00）
     * @param lastDistributionTime 前回の配布時刻
     * @return 次の配布開始時刻
     */
    public static LocalDateTime calculateNextDistributionTime(LocalDateTime lastDistributionTime) {
        LocalDateTime nextSaturday = lastDistributionTime
                .with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
                .withHour(15)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        // 隔週なので2週間後
        return nextSaturday.plusWeeks(1);
    }

    /**
     * 現在時刻から次の配布サイクルを作成
     */
    public static DistributionCycle createNewCycle() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));

        // 今日が土曜日15時以降なら次の隔週土曜日、それ以外なら次の土曜日から
        LocalDateTime startDateTime;
        if (now.getDayOfWeek() == DayOfWeek.SATURDAY && now.getHour() >= 15) {
            startDateTime = calculateNextDistributionTime(now);
        } else if (now.getDayOfWeek().getValue() > DayOfWeek.SATURDAY.getValue() ||
                   (now.getDayOfWeek() == DayOfWeek.SATURDAY && now.getHour() < 15)) {
            startDateTime = now
                    .with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
                    .withHour(15)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
        } else {
            startDateTime = now
                    .with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
                    .withHour(15)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
        }

        // 終了時刻は次の配布開始時刻の直前
        LocalDateTime endDateTime = calculateNextDistributionTime(startDateTime);

        Timestamp startTime = Timestamp.valueOf(startDateTime);
        Timestamp endTime = Timestamp.valueOf(endDateTime);

        return new DistributionCycle(0, startTime, endTime, true);
    }

    /**
     * 今すぐ開始する配布サイクルを作成（手動配布用）
     */
    public static DistributionCycle createImmediateCycle() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Tokyo"));

        // 次の隔週土曜日15時までを配布期間とする
        LocalDateTime endDateTime = now
                .with(TemporalAdjusters.next(DayOfWeek.SATURDAY))
                .withHour(15)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);

        // まだ次の土曜日が来ていない場合は、さらに次へ
        if (endDateTime.isBefore(now.plusDays(1))) {
            endDateTime = calculateNextDistributionTime(endDateTime.minusWeeks(1));
        }

        Timestamp startTime = Timestamp.valueOf(now);
        Timestamp endTime = Timestamp.valueOf(endDateTime);

        return new DistributionCycle(0, startTime, endTime, true);
    }

    @Override
    public String toString() {
        return "DistributionCycle{" +
                "cycleId=" + cycleId +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", active=" + active +
                '}';
    }
}
