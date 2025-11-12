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

package page.nafuchoco.mofu.mofuassistant.database;

import page.nafuchoco.mofu.mofuassistant.community.CommunityDistributionData;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommunityDistributionTable extends DatabaseTable {

    public CommunityDistributionTable(String tablename, DatabaseConnector connector) {
        super(tablename, connector);
    }

    public void createTable() throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + getTablename() + " (" +
                             "player_id VARCHAR(36) NOT NULL, " +
                             "community_name VARCHAR(255) NOT NULL, " +
                             "last_claim_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                             "claimed_amount INT DEFAULT 0, " +
                             "PRIMARY KEY (player_id, community_name)" +
                             ")")) {
            ps.execute();
        }
    }

    /**
     * プレイヤーの配布履歴を記録または更新
     */
    public void updateDistribution(UUID playerId, String communityName, int amount) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        if (getConnector().isSQLite()) {
            // SQLiteの場合はREPLACE INTOを使用
            try (Connection connection = getConnector().getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "REPLACE INTO " + getTablename() + " (player_id, community_name, last_claim_time, claimed_amount) " +
                                 "VALUES (?, ?, ?, ?)")) {
                ps.setString(1, playerId.toString());
                ps.setString(2, communityName);
                ps.setTimestamp(3, now);
                ps.setInt(4, amount);
                ps.executeUpdate();
            }
        } else {
            // MySQL/MariaDBの場合はON DUPLICATE KEY UPDATEを使用
            try (Connection connection = getConnector().getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO " + getTablename() + " (player_id, community_name, last_claim_time, claimed_amount) " +
                                 "VALUES (?, ?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE last_claim_time = ?, claimed_amount = ?")) {
                ps.setString(1, playerId.toString());
                ps.setString(2, communityName);
                ps.setTimestamp(3, now);
                ps.setInt(4, amount);
                ps.setTimestamp(5, now);
                ps.setInt(6, amount);
                ps.executeUpdate();
            }
        }
    }

    /**
     * プレイヤーの特定コミュニティでの配布履歴を取得
     */
    public CommunityDistributionData getDistribution(UUID playerId, String communityName) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE player_id = ? AND community_name = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, communityName);

            try (ResultSet resultSet = ps.executeQuery()) {
                if (resultSet.next()) {
                    return new CommunityDistributionData(
                            UUID.fromString(resultSet.getString("player_id")),
                            resultSet.getString("community_name"),
                            resultSet.getTimestamp("last_claim_time"),
                            resultSet.getInt("claimed_amount")
                    );
                }
            }
        }
        return null;
    }

    /**
     * プレイヤーの全ての配布履歴を取得
     */
    public List<CommunityDistributionData> getPlayerDistributions(UUID playerId) throws SQLException {
        List<CommunityDistributionData> distributions = new ArrayList<>();
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());

            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    distributions.add(new CommunityDistributionData(
                            UUID.fromString(resultSet.getString("player_id")),
                            resultSet.getString("community_name"),
                            resultSet.getTimestamp("last_claim_time"),
                            resultSet.getInt("claimed_amount")
                    ));
                }
            }
        }
        return distributions;
    }

    /**
     * 特定コミュニティの全配布履歴を取得
     */
    public List<CommunityDistributionData> getCommunityDistributions(String communityName) throws SQLException {
        List<CommunityDistributionData> distributions = new ArrayList<>();
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE community_name = ?")) {
            ps.setString(1, communityName);

            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    distributions.add(new CommunityDistributionData(
                            UUID.fromString(resultSet.getString("player_id")),
                            resultSet.getString("community_name"),
                            resultSet.getTimestamp("last_claim_time"),
                            resultSet.getInt("claimed_amount")
                    ));
                }
            }
        }
        return distributions;
    }
}
