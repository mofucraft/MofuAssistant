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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.val;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseConnector {
    private final HikariDataSource dataSource;
    private final String prefix;
    private final DatabaseType databaseType;

    public DatabaseConnector(DatabaseType databaseType, String address, String database, String username, String password, String prefix) {
        this.databaseType = databaseType;
        val hconfig = new HikariConfig();
        hconfig.setDriverClassName(databaseType.getJdbcClass());

        if (databaseType == DatabaseType.SQLITE) {
            // SQLiteの場合はファイルパスを使用
            hconfig.setJdbcUrl(databaseType.getAddressPrefix() + database);
            // SQLiteはユーザー名・パスワード不要
            hconfig.setMaximumPoolSize(1); // SQLiteは単一接続推奨
        } else {
            // MySQL/MariaDBの場合
            hconfig.setJdbcUrl(databaseType.getAddressPrefix() + address + "/" + database);
            hconfig.addDataSourceProperty("user", username);
            hconfig.addDataSourceProperty("password", password);
        }

        dataSource = new HikariDataSource(hconfig);
        this.prefix = prefix;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        dataSource.close();
    }

    public String getPrefix() {
        return prefix;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public boolean isSQLite() {
        return databaseType == DatabaseType.SQLITE;
    }


    public enum DatabaseType {
        SQLITE("org.sqlite.JDBC", "jdbc:sqlite:"),
        MARIADB("org.mariadb.jdbc.Driver", "jdbc:mariadb://"),
        MYSQL("com.mysql.jdbc.Driver", "jdbc:mysql://");

        private final String jdbcClass;
        private final String addressPrefix;

        DatabaseType(String jdbcClass, String addressPrefix) {
            this.jdbcClass = jdbcClass;
            this.addressPrefix = addressPrefix;
        }

        public String getJdbcClass() {
            return jdbcClass;
        }

        public String getAddressPrefix() {
            return addressPrefix;
        }
    }
}
