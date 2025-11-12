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

import page.nafuchoco.mofu.mofuassistant.community.CommunityInvite;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommunityInviteTable extends DatabaseTable {

    public CommunityInviteTable(String tablename, DatabaseConnector connector) {
        super(tablename, connector);
    }

    public void createTable() throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS " + getTablename() + " (" +
                             "player_id VARCHAR(36) NOT NULL, " +
                             "community_name VARCHAR(255) NOT NULL, " +
                             "inviter_id VARCHAR(36) NOT NULL, " +
                             "invited_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                             "PRIMARY KEY (player_id, community_name)" +
                             ")")) {
            ps.execute();
        }
    }

    /**
     * 招待を作成または更新
     */
    public void createInvite(UUID playerId, String communityName, UUID inviterId) throws SQLException {
        Timestamp now = new Timestamp(System.currentTimeMillis());

        if (getConnector().isSQLite()) {
            // SQLiteの場合
            try (Connection connection = getConnector().getConnection()) {
                // 既存の招待を確認
                boolean exists = false;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT 1 FROM " + getTablename() +
                        " WHERE player_id = ? AND community_name = ?")) {
                    ps.setString(1, playerId.toString());
                    ps.setString(2, communityName);
                    try (ResultSet rs = ps.executeQuery()) {
                        exists = rs.next();
                    }
                }

                if (exists) {
                    // 更新
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE " + getTablename() +
                            " SET inviter_id = ?, invited_at = ? " +
                            "WHERE player_id = ? AND community_name = ?")) {
                        ps.setString(1, inviterId.toString());
                        ps.setTimestamp(2, now);
                        ps.setString(3, playerId.toString());
                        ps.setString(4, communityName);
                        ps.executeUpdate();
                    }
                } else {
                    // 新規挿入
                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO " + getTablename() +
                            " (player_id, community_name, inviter_id, invited_at) " +
                            "VALUES (?, ?, ?, ?)")) {
                        ps.setString(1, playerId.toString());
                        ps.setString(2, communityName);
                        ps.setString(3, inviterId.toString());
                        ps.setTimestamp(4, now);
                        ps.executeUpdate();
                    }
                }
            }
        } else {
            // MySQL/MariaDBの場合はON DUPLICATE KEY UPDATEを使用
            try (Connection connection = getConnector().getConnection();
                 PreparedStatement ps = connection.prepareStatement(
                         "INSERT INTO " + getTablename() +
                         " (player_id, community_name, inviter_id, invited_at) " +
                         "VALUES (?, ?, ?, ?) " +
                         "ON DUPLICATE KEY UPDATE inviter_id = ?, invited_at = ?")) {
                ps.setString(1, playerId.toString());
                ps.setString(2, communityName);
                ps.setString(3, inviterId.toString());
                ps.setTimestamp(4, now);
                ps.setString(5, inviterId.toString());
                ps.setTimestamp(6, now);
                ps.executeUpdate();
            }
        }
    }

    /**
     * 招待を削除
     */
    public boolean deleteInvite(UUID playerId, String communityName) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM " + getTablename() +
                     " WHERE player_id = ? AND community_name = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, communityName);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * プレイヤーの招待を取得
     */
    public CommunityInvite getInvite(UUID playerId) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new CommunityInvite(
                            UUID.fromString(rs.getString("player_id")),
                            rs.getString("community_name"),
                            UUID.fromString(rs.getString("inviter_id")),
                            rs.getTimestamp("invited_at")
                    );
                }
            }
        }
        return null;
    }

    /**
     * 特定プレイヤーへの特定コミュニティの招待があるかチェック
     */
    public boolean hasInvite(UUID playerId, String communityName) throws SQLException {
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT 1 FROM " + getTablename() +
                     " WHERE player_id = ? AND community_name = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, communityName);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * プレイヤーへの全ての招待を取得
     */
    public List<CommunityInvite> getAllInvites(UUID playerId) throws SQLException {
        List<CommunityInvite> invites = new ArrayList<>();
        try (Connection connection = getConnector().getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    invites.add(new CommunityInvite(
                            UUID.fromString(rs.getString("player_id")),
                            rs.getString("community_name"),
                            UUID.fromString(rs.getString("inviter_id")),
                            rs.getTimestamp("invited_at")
                    ));
                }
            }
        }
        return invites;
    }
}
