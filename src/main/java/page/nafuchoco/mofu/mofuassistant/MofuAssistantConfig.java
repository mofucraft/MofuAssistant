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

package page.nafuchoco.mofu.mofuassistant;

import lombok.val;
import org.bukkit.configuration.file.FileConfiguration;
import page.nafuchoco.mofu.mofuassistant.database.DatabaseConnector;

public class MofuAssistantConfig {
    private static final MofuAssistant instance = MofuAssistant.getInstance();
    private InitConfig initConfig;
    private boolean debug;

    public void reloadConfig() {
        instance.reloadConfig();
        FileConfiguration config = instance.getConfig();

        if (initConfig == null) {
            val databaseType = DatabaseConnector.DatabaseType.valueOf(config.getString("initialization.database.type"));
            val address = config.getString("initialization.database.address");
            val port = config.getInt("initialization.database.port", 3306);
            val database = config.getString("initialization.database.database");
            val username = config.getString("initialization.database.username");
            val password = config.getString("initialization.database.password");
            val tablePrefix = config.getString("initialization.database.tablePrefix");
            initConfig = new InitConfig(databaseType, address, port, database, username, password, tablePrefix);
            debug = config.getBoolean("debug");
        }
    }

    public InitConfig getInitConfig() {
        return initConfig;
    }

    public boolean isDebug() {
        return debug;
    }

    public record InitConfig(DatabaseConnector.DatabaseType databaseType,
                             String address, int port, String database, String username,
                             String password, String tablePrefix) {

        public DatabaseConnector.DatabaseType getDatabaseType() {
            return databaseType;
        }

        public String getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }

        public String getDatabase() {
            return database;
        }

        public String getUsername() {
            return username;
        }

        public String getPassword() {
            return password;
        }

        public String getTablePrefix() {
            return tablePrefix;
        }
    }
}
