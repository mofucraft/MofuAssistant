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

/**
 * コミュニティアイテムプールの情報を表すデータクラス
 */
public class CommunityPool {
    private final int cycleId;
    private final String communityName;
    private final int totalAmount;
    private final int remainingAmount;
    private final Timestamp lastUpdated;

    public CommunityPool(int cycleId, String communityName, int totalAmount, int remainingAmount, Timestamp lastUpdated) {
        this.cycleId = cycleId;
        this.communityName = communityName;
        this.totalAmount = totalAmount;
        this.remainingAmount = remainingAmount;
        this.lastUpdated = lastUpdated;
    }

    public int getCycleId() {
        return cycleId;
    }

    public String getCommunityName() {
        return communityName;
    }

    public int getTotalAmount() {
        return totalAmount;
    }

    public int getRemainingAmount() {
        return remainingAmount;
    }

    public Timestamp getLastUpdated() {
        return lastUpdated;
    }

    public boolean hasRemaining() {
        return remainingAmount > 0;
    }

    @Override
    public String toString() {
        return "CommunityPool{" +
                "cycleId=" + cycleId +
                ", communityName='" + communityName + '\'' +
                ", totalAmount=" + totalAmount +
                ", remainingAmount=" + remainingAmount +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
