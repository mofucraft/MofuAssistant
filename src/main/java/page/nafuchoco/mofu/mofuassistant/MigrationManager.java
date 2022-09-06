/*
 * Copyright 2022 NAFU_at
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

package page.nafuchoco.mofu.mofuassistant;

import org.bukkit.OfflinePlayer;
import page.nafuchoco.mofu.mofuassistant.database.DatabaseConnector;

import java.sql.SQLException;
import java.util.logging.Level;

public class MigrationManager {
    private final MofuAssistant instance = MofuAssistant.getInstance();

    public void migrate(OfflinePlayer originalPlayer, OfflinePlayer newPlayer) {
        instance.getPluginConfig().getMigrationConfigs().forEach(migrationConfig -> {
            instance.getLogger().log(Level.INFO, "Migrating " + originalPlayer.getName() + " to " + newPlayer.getName() + "...");
            instance.getLogger().log(Level.INFO, "Migration target: " + migrationConfig.database());
            var connector = new DatabaseConnector(migrationConfig.databaseType(), migrationConfig.address() + ":" + migrationConfig.port(),
                    migrationConfig.database(), migrationConfig.username(), migrationConfig.password(), "");
            try (var connection = connector.getConnection()) {
                migrationConfig.tableConfigs().forEach(tableConfig -> {
                    instance.getLogger().log(Level.INFO, "Migrating table: " + tableConfig.tableName());
                    tableConfig.columns().forEach(colum -> {
                        instance.getLogger().log(Level.INFO, "Migrating column: " + colum);
                        try (var statement = connection.prepareStatement(
                                "UPDATE " + tableConfig.tableName() + " SET " + colum + " = REPLACE(" + colum + ", \"" + originalPlayer.getUniqueId() + "\", \"" + newPlayer.getUniqueId() + "\")" + " WHERE " + colum + " = ?")) {
                            statement.setString(1, originalPlayer.getUniqueId().toString());
                            statement.execute();
                        } catch (SQLException e) {
                            instance.getLogger().log(Level.WARNING, "Failed to migrate column: " + colum, e);
                        }
                    });
                });
            } catch (SQLException e) {
                instance.getLogger().log(Level.WARNING, "Failed to migrate " + originalPlayer.getName() + " to " + newPlayer.getName() + ".", e);
            }
        });
    }
}
