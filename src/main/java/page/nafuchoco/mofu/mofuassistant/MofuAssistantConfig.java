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

import java.util.List;

public class MofuAssistantConfig {
    private static final MofuAssistant instance = MofuAssistant.getInstance();
    private InitConfig initConfig;
    private PeacefulModeConfig peacefulModeConfig;
    private boolean debug;

    public void reloadConfig() {
        instance.reloadConfig();
        FileConfiguration config = instance.getConfig();

        // TODO: 2022/03/09 何らかの影響でMariaDBのドライバが他プラグインと競合したため一時的に無効化
        // val databaseType = DatabaseConnector.DatabaseType.valueOf(config.getString("initialization.database.type"));
        val databaseType = DatabaseConnector.DatabaseType.MYSQL;
        val address = config.getString("initialization.database.address");
        val port = config.getInt("initialization.database.port", 3306);
        val database = config.getString("initialization.database.database");
        val username = config.getString("initialization.database.username");
        val password = config.getString("initialization.database.password");
        val tablePrefix = config.getString("initialization.database.tablePrefix");
        val bypassHikariCP = config.getBoolean("initialization.database.bypassHikariCP", true);
        initConfig = new InitConfig(databaseType, address, port, database, username, password, tablePrefix, bypassHikariCP);

        val peacefulModeEnable = config.getBoolean("peacefulMode.enable");
        val worldWhitelist = config.getBoolean("peacefulMode.worldWhitelist");
        val targetWorld = config.getStringList("peacefulMode.targetWorld");
        val keepChangeWorld = config.getBoolean("peacefulMode.keepChangeWorld");
        peacefulModeConfig = new PeacefulModeConfig(peacefulModeEnable, worldWhitelist, targetWorld, keepChangeWorld);

        debug = config.getBoolean("debug");
    }

    public InitConfig getInitConfig() {
        return initConfig;
    }

    public PeacefulModeConfig getPeacefulModeConfig() {
        return peacefulModeConfig;
    }

    public boolean isDebug() {
        return debug;
    }

    public record InitConfig(DatabaseConnector.DatabaseType databaseType,
                             String address, int port, String database, String username,
                             String password, String tablePrefix, boolean bypassHikariCP) {

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

        public boolean bypassHikariCP() {
            return bypassHikariCP;
        }
    }

    public record PeacefulModeConfig(boolean enable, boolean worldWhitelist, List<String> targetWorld,
                                     boolean keepChangeWorld) {
    }

    @Override
    public String toString() {
        return "MofuAssistantConfig{" +
                "initConfig=" + initConfig +
                ", peacefulModeConfig=" + peacefulModeConfig +
                ", debug=" + debug +
                '}';
    }
}
