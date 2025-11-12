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

import page.nafuchoco.mofu.mofuassistant.community.DistributionCycle;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DistributionCycleTable extends DatabaseTable {

    public DistributionCycleTable(String tablename, DatabaseConnector connector) {
        super(tablename, connector);
    }

    @Override
    public void createTable() throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + getTablename() + " (" +
                             "cycle_id INT AUTO_INCREMENT PRIMARY KEY, " +
                             "start_time TIMESTAMP NOT NULL, " +
                             "end_time TIMESTAMP NOT NULL, " +
                             "active BOOLEAN DEFAULT TRUE, " +
                             "INDEX idx_active (active), " +
                             "INDEX idx_times (start_time, end_time)" +
                             ")")) {
            ps.execute();
        }
    }

    /**
     * 新しい配布サイクルを作成
     */
    public int createCycle(DistributionCycle cycle) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO " + getTablename() + " (start_time, end_time, active) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, cycle.getStartTime());
            ps.setTimestamp(2, cycle.getEndTime());
            ps.setBoolean(3, cycle.isActive());
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    /**
     * 現在アクティブな配布サイクルを取得
     */
    public DistributionCycle getActiveCycle() throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE active = TRUE ORDER BY cycle_id DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DistributionCycle(
                            rs.getInt("cycle_id"),
                            rs.getTimestamp("start_time"),
                            rs.getTimestamp("end_time"),
                            rs.getBoolean("active")
                    );
                }
            }
        }
        return null;
    }

    /**
     * 特定のサイクルを取得
     */
    public DistributionCycle getCycle(int cycleId) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE cycle_id = ?")) {
            ps.setInt(1, cycleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new DistributionCycle(
                            rs.getInt("cycle_id"),
                            rs.getTimestamp("start_time"),
                            rs.getTimestamp("end_time"),
                            rs.getBoolean("active")
                    );
                }
            }
        }
        return null;
    }

    /**
     * 全てのアクティブなサイクルを無効化
     */
    public void deactivateAllCycles() throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE " + getTablename() + " SET active = FALSE WHERE active = TRUE")) {
            ps.executeUpdate();
        }
    }

    /**
     * 特定のサイクルを無効化
     */
    public void deactivateCycle(int cycleId) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE " + getTablename() + " SET active = FALSE WHERE cycle_id = ?")) {
            ps.setInt(1, cycleId);
            ps.executeUpdate();
        }
    }

    /**
     * 全てのサイクルを取得（最新順）
     */
    public List<DistributionCycle> getAllCycles(int limit) throws SQLException {
        List<DistributionCycle> cycles = new ArrayList<>();
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " ORDER BY cycle_id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    cycles.add(new DistributionCycle(
                            rs.getInt("cycle_id"),
                            rs.getTimestamp("start_time"),
                            rs.getTimestamp("end_time"),
                            rs.getBoolean("active")
                    ));
                }
            }
        }
        return cycles;
    }

    /**
     * 配布履歴をリセット（新しいサイクル開始時に実行）
     */
    public void resetDistributionHistory(String distributionTableName) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM " + distributionTableName)) {
            ps.executeUpdate();
        }
    }
}
