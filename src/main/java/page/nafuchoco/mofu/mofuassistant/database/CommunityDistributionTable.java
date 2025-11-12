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
        try (Connection connection = getConnector().getConnection()) {
            // テーブルを作成
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS " + getTablename() + " (" +
                            "cycle_id INT NOT NULL, " +
                            "player_id VARCHAR(36) NOT NULL, " +
                            "community_name VARCHAR(255) NOT NULL, " +
                            "last_claim_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "claimed_amount INT DEFAULT 0, " +
                            "PRIMARY KEY (cycle_id, player_id, community_name)" +
                            ")")) {
                ps.execute();
            }

            // 既存テーブルにcycle_idカラムが存在しない場合は追加（マイグレーション）
            migrateTableIfNeeded(connection);
        }
    }

    /**
     * 古いテーブル構造から新しい構造へマイグレーション
     */
    private void migrateTableIfNeeded(Connection connection) throws SQLException {
        // テーブルが存在するかチェック
        boolean tableExists = false;
        if (getConnector().isSQLite()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?")) {
                ps.setString(1, getTablename());
                try (ResultSet rs = ps.executeQuery()) {
                    tableExists = rs.next();
                }
            }
        } else {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?")) {
                ps.setString(1, getTablename());
                try (ResultSet rs = ps.executeQuery()) {
                    tableExists = rs.next();
                }
            }
        }

        // テーブルが存在しない場合は何もしない（CREATE TABLE IF NOT EXISTSで作成される）
        if (!tableExists) {
            return;
        }

        // テーブルのカラム情報を取得
        boolean hasCycleId = false;

        if (getConnector().isSQLite()) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "PRAGMA table_info(" + getTablename() + ")")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String columnName = rs.getString("name");
                        if ("cycle_id".equals(columnName)) {
                            hasCycleId = true;
                            break;
                        }
                    }
                }
            }
        } else {
            // MySQL/MariaDBの場合
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_NAME = ? AND COLUMN_NAME = 'cycle_id'")) {
                ps.setString(1, getTablename());
                try (ResultSet rs = ps.executeQuery()) {
                    hasCycleId = rs.next();
                }
            }
        }

        // cycle_idカラムが存在しない場合
        if (!hasCycleId) {
            // 古いデータをバックアップして削除（cycle_idなしのデータは不整合があるため）
            try (PreparedStatement ps = connection.prepareStatement(
                    "DROP TABLE IF EXISTS " + getTablename() + "_old")) {
                ps.execute();
            }

            // テーブルをリネーム
            if (getConnector().isSQLite()) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "ALTER TABLE " + getTablename() + " RENAME TO " + getTablename() + "_old")) {
                    ps.execute();
                }
            } else {
                try (PreparedStatement ps = connection.prepareStatement(
                        "RENAME TABLE " + getTablename() + " TO " + getTablename() + "_old")) {
                    ps.execute();
                }
            }

            // 新しい構造でテーブルを再作成
            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE " + getTablename() + " (" +
                            "cycle_id INT NOT NULL, " +
                            "player_id VARCHAR(36) NOT NULL, " +
                            "community_name VARCHAR(255) NOT NULL, " +
                            "last_claim_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "claimed_amount INT DEFAULT 0, " +
                            "PRIMARY KEY (cycle_id, player_id, community_name)" +
                            ")")) {
                ps.execute();
            }

            // 古いテーブルを削除
            try (PreparedStatement ps = connection.prepareStatement(
                    "DROP TABLE IF EXISTS " + getTablename() + "_old")) {
                ps.execute();
            }
        }
    }

    /**
     * プレイヤーの配布履歴を記録または更新（増分）
     */
    public void addClaim(int cycleId, UUID playerId, String communityName, int amount) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        if (getConnector().isSQLite()) {
            // SQLiteの場合は、既存レコードがあるかチェックしてINSERTまたはUPDATE
            try (Connection connection = getConnector().getConnection()) {
                // 既存のレコードを確認
                Integer currentAmount = null;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT claimed_amount FROM " + getTablename() +
                        " WHERE cycle_id = ? AND player_id = ? AND community_name = ?")) {
                    ps.setInt(1, cycleId);
                    ps.setString(2, playerId.toString());
                    ps.setString(3, communityName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            currentAmount = rs.getInt("claimed_amount");
                        }
                    }
                }

                if (currentAmount == null) {
                    // 新規挿入
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO " + getTablename() +
                            " (cycle_id, player_id, community_name, last_claim_time, claimed_amount) " +
                            "VALUES (?, ?, ?, ?, ?)")) {
                        ps.setInt(1, cycleId);
                        ps.setString(2, playerId.toString());
                        ps.setString(3, communityName);
                        ps.setTimestamp(4, now);
                        ps.setInt(5, amount);
                        ps.executeUpdate();
                    }
                } else {
                    // 加算更新
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE " + getTablename() +
                            " SET claimed_amount = claimed_amount + ?, last_claim_time = ? " +
                            "WHERE cycle_id = ? AND player_id = ? AND community_name = ?")) {
                        ps.setInt(1, amount);
                        ps.setTimestamp(2, now);
                        ps.setInt(3, cycleId);
                        ps.setString(4, playerId.toString());
                        ps.setString(5, communityName);
                        ps.executeUpdate();
                    }
                }
            }
        } else {
            // MySQL/MariaDBの場合はON DUPLICATE KEY UPDATEを使用
            try (Connection connection = getConnector().getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO " + getTablename() +
                         " (cycle_id, player_id, community_name, last_claim_time, claimed_amount) " +
                         "VALUES (?, ?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE claimed_amount = claimed_amount + ?, last_claim_time = ?")) {
                ps.setInt(1, cycleId);
                ps.setString(2, playerId.toString());
                ps.setString(3, communityName);
                ps.setTimestamp(4, now);
                ps.setInt(5, amount);
                ps.setInt(6, amount);
                ps.setTimestamp(7, now);
                ps.executeUpdate();
            }
        }
    }

    /**
     * プレイヤーの特定サイクル・コミュニティでの配布履歴を取得
     */
    public CommunityDistributionData getDistribution(int cycleId, UUID playerId, String communityName) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() +
                     " WHERE cycle_id = ? AND player_id = ? AND community_name = ?")) {
            ps.setInt(1, cycleId);
            ps.setString(2, playerId.toString());
            ps.setString(3, communityName);

            try (ResultSet resultSet = ps.executeQuery()) {
                if (resultSet.next()) {
                    return new CommunityDistributionData(
                            resultSet.getInt("cycle_id"),
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
                            resultSet.getInt("cycle_id"),
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
    public List<CommunityDistributionData> getCommunityDistributions(int cycleId, String communityName) throws SQLException {
        List<CommunityDistributionData> distributions = new ArrayList<>();
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() +
                     " WHERE cycle_id = ? AND community_name = ?")) {
            ps.setInt(1, cycleId);
            ps.setString(2, communityName);

            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    distributions.add(new CommunityDistributionData(
                            resultSet.getInt("cycle_id"),
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
