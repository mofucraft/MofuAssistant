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

import page.nafuchoco.mofu.mofuassistant.community.CommunityPool;

import java.sql.*;

public class CommunityPoolTable extends DatabaseTable {

    public CommunityPoolTable(String tablename, DatabaseConnector connector) {
        super(tablename, connector);
    }

    @Override
    public void createTable() throws SQLException {
        boolean isSQLite = getConnector().isSQLite();

        try (Connection connection = getConnector().getConnection()) {
            String createTableSQL;
            if (isSQLite) {
                createTableSQL = "CREATE TABLE IF NOT EXISTS " + getTablename() + " (" +
                        "cycle_id INTEGER NOT NULL, " +
                        "community_name VARCHAR(255) NOT NULL, " +
                        "total_amount INT NOT NULL, " +
                        "remaining_amount INT NOT NULL, " +
                        "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (cycle_id, community_name)" +
                        ")";
            } else {
                createTableSQL = "CREATE TABLE IF NOT EXISTS " + getTablename() + " (" +
                        "cycle_id INT NOT NULL, " +
                        "community_name VARCHAR(255) NOT NULL, " +
                        "total_amount INT NOT NULL, " +
                        "remaining_amount INT NOT NULL, " +
                        "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (cycle_id, community_name)" +
                        ")";
            }

            try (PreparedStatement ps = connection.prepareStatement(createTableSQL)) {
                ps.execute();
            }
        }
    }

    /**
     * コミュニティプールを作成または初期化
     */
    public void createOrResetPool(int cycleId, String communityName, int totalAmount) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        if (getConnector().isSQLite()) {
            try (Connection connection = getConnector().getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "REPLACE INTO " + getTablename() + " (cycle_id, community_name, total_amount, remaining_amount, last_updated) " +
                                 "VALUES (?, ?, ?, ?, ?)")) {
                ps.setInt(1, cycleId);
                ps.setString(2, communityName);
                ps.setInt(3, totalAmount);
                ps.setInt(4, totalAmount);
                ps.setTimestamp(5, now);
                ps.executeUpdate();
            }
        } else {
            try (Connection connection = getConnector().getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO " + getTablename() + " (cycle_id, community_name, total_amount, remaining_amount, last_updated) " +
                                 "VALUES (?, ?, ?, ?, ?) " +
                                 "ON DUPLICATE KEY UPDATE total_amount = ?, remaining_amount = ?, last_updated = ?")) {
                ps.setInt(1, cycleId);
                ps.setString(2, communityName);
                ps.setInt(3, totalAmount);
                ps.setInt(4, totalAmount);
                ps.setTimestamp(5, now);
                ps.setInt(6, totalAmount);
                ps.setInt(7, totalAmount);
                ps.setTimestamp(8, now);
                ps.executeUpdate();
            }
        }
    }

    /**
     * プールから指定数量を減らす（アトミック操作）
     */
    public boolean claimFromPool(int cycleId, String communityName, int amount) throws SQLException {
        try (Connection connection = getConnector().getConnection()) {
            connection.setAutoCommit(false);
            try {
                // 現在の残量を確認
                int currentRemaining;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT remaining_amount FROM " + getTablename() + " WHERE cycle_id = ? AND community_name = ? FOR UPDATE")) {
                    ps.setInt(1, cycleId);
                    ps.setString(2, communityName);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            connection.rollback();
                            return false;
                        }
                        currentRemaining = rs.getInt("remaining_amount");
                    }
                }

                // 十分な残量があるかチェック
                if (currentRemaining < amount) {
                    connection.rollback();
                    return false;
                }

                // 残量を減らす
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + getTablename() + " SET remaining_amount = remaining_amount - ?, last_updated = ? " +
                                "WHERE cycle_id = ? AND community_name = ?")) {
                    ps.setInt(1, amount);
                    ps.setTimestamp(2, new Timestamp(System.currentTimeMillis()));
                    ps.setInt(3, cycleId);
                    ps.setString(4, communityName);
                    ps.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    /**
     * コミュニティプール情報を取得
     */
    public CommunityPool getPool(int cycleId, String communityName) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE cycle_id = ? AND community_name = ?")) {
            ps.setInt(1, cycleId);
            ps.setString(2, communityName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CommunityPool(
                            rs.getInt("cycle_id"),
                            rs.getString("community_name"),
                            rs.getInt("total_amount"),
                            rs.getInt("remaining_amount"),
                            rs.getTimestamp("last_updated")
                    );
                }
            }
        }
        return null;
    }

    /**
     * サイクル終了時にプールをクリア
     */
    public void clearPoolsForCycle(int cycleId) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM " + getTablename() + " WHERE cycle_id = ?")) {
            ps.setInt(1, cycleId);
            ps.executeUpdate();
        }
    }
}
