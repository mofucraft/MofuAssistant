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

import com.google.gson.Gson;
import lombok.val;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;
import page.nafuchoco.mofu.mofuassistant.data.MofuPlayerData;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class MofuAssistantTable extends DatabaseTable {
    private static final Gson mapper = new Gson();

    public MofuAssistantTable(String tablename, DatabaseConnector connector) {
        super(tablename, connector);
    }

    public void createTable() throws SQLException {
        super.createTable("id VARCHAR(36) PRIMARY KEY, playername VARCHAR(16), player_data LONGTEXT");
    }

    public void registerPlayer(MofuPlayerData playerData) throws SQLException {
        try (var connection = getConnector().getConnection();
             var ps = connection.prepareStatement(
                     "INSERT INTO " + getTablename() + " (id, playername, player_data) " +
                             "VALUES (?, ?, ?)"
             )) {
            ps.setString(1, playerData.getId().toString());
            ps.setString(2, playerData.getPlayerName());
            ps.setString(3, mapper.toJson(playerData.getSettings()));
            ps.execute();
        }
    }

    public MofuPlayerData getPlayerData(UUID id) {
        try (var connection = getConnector().getConnection();
             var ps = connection.prepareStatement(
                     "SELECT * FROM " + getTablename() + " WHERE id = ?"
             )) {
            ps.setString(1, id.toString());
            try (var resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    val playername = resultSet.getString("playername");
                    val playerSettings = mapper.fromJson(resultSet.getString("player_data"), MofuPlayerData.PlayerSettings.class);
                    return new MofuPlayerData(id, playername, playerSettings);
                }
            }
        } catch (SQLException e) {
            MofuAssistant.getInstance().getLogger().log(Level.WARNING, "Failed to get player data.", e);
        }
        return null;
    }

    public void updatePlayerData(MofuPlayerData playerData) throws SQLException {
        try (var connection = getConnector().getConnection();
             var ps = connection.prepareStatement(
                     "UPDATE " + getTablename() + " SET playername = ?,  player_data = ? WHERE id = ?"
             )) {
            ps.setString(3, playerData.getId().toString());
            ps.setString(1, playerData.getPlayerName());
            ps.setString(2, mapper.toJson(playerData.getSettings()));
            ps.execute();
        }
    }
}
