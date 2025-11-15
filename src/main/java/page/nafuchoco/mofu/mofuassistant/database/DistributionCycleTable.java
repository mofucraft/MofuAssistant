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

    public void createTable() throws SQLException {
        boolean isSQLite = getConnector().isSQLite();

        try (Connection connection = getConnector().getConnection()) {
            // テーブル作成
            String createTableSQL;
            if (isSQLite) {
                createTableSQL = "CREATE TABLE IF NOT EXISTS " + getTablename() + " (" +
                        "cycle_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "start_time TIMESTAMP NOT NULL, " +
                        "end_time TIMESTAMP NOT NULL, " +
                        "active BOOLEAN DEFAULT 1, " +
                        "paused BOOLEAN DEFAULT 0" +
                        ")";
            } else {
                createTableSQL = "CREATE TABLE IF NOT EXISTS " + getTablename() + " (" +
                        "cycle_id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "start_time TIMESTAMP NOT NULL, " +
                        "end_time TIMESTAMP NOT NULL, " +
                        "active BOOLEAN DEFAULT TRUE, " +
                        "paused BOOLEAN DEFAULT FALSE, " +
                        "INDEX idx_active (active), " +
                        "INDEX idx_times (start_time, end_time)" +
                        ")";
            }

            try (PreparedStatement ps = connection.prepareStatement(createTableSQL)) {
                ps.execute();
            }

            // SQLiteの場合はインデックスを別途作成
            if (isSQLite) {
                try (PreparedStatement ps = connection.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_active ON " + getTablename() + " (active)")) {
                    ps.execute();
                }
                try (PreparedStatement ps = connection.prepareStatement(
                        "CREATE INDEX IF NOT EXISTS idx_times ON " + getTablename() + " (start_time, end_time)")) {
                    ps.execute();
                }
            }

            // 既存テーブルにpausedカラムを追加（存在しない場合）
            addPausedColumnIfNotExists(connection, isSQLite);
        }
    }

    /**
     * 既存テーブルにpausedカラムを追加（マイグレーション）
     */
    private void addPausedColumnIfNotExists(Connection connection, boolean isSQLite) throws SQLException {
        // カラムが存在するかチェック
        boolean columnExists = false;
        try (ResultSet rs = connection.getMetaData().getColumns(null, null, getTablename(), "paused")) {
            columnExists = rs.next();
        }

        // カラムが存在しない場合は追加
        if (!columnExists) {
            String alterSQL;
            if (isSQLite) {
                alterSQL = "ALTER TABLE " + getTablename() + " ADD COLUMN paused BOOLEAN DEFAULT 0";
            } else {
                alterSQL = "ALTER TABLE " + getTablename() + " ADD COLUMN paused BOOLEAN DEFAULT FALSE";
            }
            try (PreparedStatement ps = connection.prepareStatement(alterSQL)) {
                ps.execute();
            }
        }
    }

    /**
     * 新しい配布サイクルを作成
     */
    public int createCycle(DistributionCycle cycle) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO " + getTablename() + " (start_time, end_time, active, paused) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setTimestamp(1, cycle.getStartTime());
            ps.setTimestamp(2, cycle.getEndTime());
            ps.setBoolean(3, cycle.isActive());
            ps.setBoolean(4, cycle.isPaused());
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
                            rs.getBoolean("active"),
                            rs.getBoolean("paused")
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
                            rs.getBoolean("active"),
                            rs.getBoolean("paused")
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
                            rs.getBoolean("active"),
                            rs.getBoolean("paused")
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

    /**
     * サイクルを一時停止
     */
    public void pauseCycle(int cycleId) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE " + getTablename() + " SET paused = TRUE WHERE cycle_id = ?")) {
            ps.setInt(1, cycleId);
            ps.executeUpdate();
        }
    }

    /**
     * サイクルを再開
     */
    public void resumeCycle(int cycleId) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE " + getTablename() + " SET paused = FALSE WHERE cycle_id = ?")) {
            ps.setInt(1, cycleId);
            ps.executeUpdate();
        }
    }

    /**
     * サイクルの開始時刻と終了時刻を更新
     */
    public void updateCycleTimes(int cycleId, Timestamp newStartTime, Timestamp newEndTime) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "UPDATE " + getTablename() + " SET start_time = ?, end_time = ? WHERE cycle_id = ?")) {
            ps.setTimestamp(1, newStartTime);
            ps.setTimestamp(2, newEndTime);
            ps.setInt(3, cycleId);
            ps.executeUpdate();
        }
    }
}
