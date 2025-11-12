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
import java.util.UUID;

/**
 * コミュニティアイテム配布の受け取り記録を表すデータクラス
 */
public class CommunityDistributionData {
    private final int cycleId;
    private final UUID playerId;
    private final String communityName;
    private final Timestamp lastClaimTime;
    private final int claimedAmount;

    public CommunityDistributionData(int cycleId, UUID playerId, String communityName, Timestamp lastClaimTime, int claimedAmount) {
        this.cycleId = cycleId;
        this.playerId = playerId;
        this.communityName = communityName;
        this.lastClaimTime = lastClaimTime;
        this.claimedAmount = claimedAmount;
    }

    public int getCycleId() {
        return cycleId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getCommunityName() {
        return communityName;
    }

    public Timestamp getLastClaimTime() {
        return lastClaimTime;
    }

    public int getClaimedAmount() {
        return claimedAmount;
    }

    @Override
    public String toString() {
        return "CommunityDistributionData{" +
                "cycleId=" + cycleId +
                ", playerId=" + playerId +
                ", communityName='" + communityName + '\'' +
                ", lastClaimTime=" + lastClaimTime +
                ", claimedAmount=" + claimedAmount +
                '}';
    }
}
