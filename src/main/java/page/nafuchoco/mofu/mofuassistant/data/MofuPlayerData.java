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

package page.nafuchoco.mofu.mofuassistant.data;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;

import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class MofuPlayerData {
    private final UUID id;
    private final String playerName;
    private final Player bukkitPlayer;
    private final PlayerSettings settings;


    public MofuPlayerData(UUID id, String playerName, PlayerSettings settings) {
        this.id = id;
        this.playerName = playerName;
        bukkitPlayer = Bukkit.getPlayer(id);
        this.settings = settings;
    }

    /**
     * このメソッドはプレイヤーに付与された不変のUUIDを返します。
     *
     * @return プレイヤーのUUID
     */
    public UUID getId() {
        return id;
    }

    /**
     * このメソッドはプレイヤーが最後にログインしたときのプレイヤー名を返します。
     *
     * @return プレイヤー名
     */
    public String getPlayerName() {
        return playerName;
    }

    /**
     * メソッドはBukkitのPlayerクラスを返します。
     * このプレイヤーデータが読み込まれた際にプレイヤーがサーバーにログインしていなかった場合はnullを返します。
     *
     * @return BukkitのPlayer
     */
    public Player getBukkitPlayer() {
        return bukkitPlayer;
    }

    /**
     * このメソッドはMofuAssistantのプレイヤー設定を返します。
     *
     * @return MofuAssistantのプレイヤー設定
     */
    public PlayerSettings getSettings() {
        return settings;
    }

    public void updatePlayerData() {
        var updateDate = this;
        if (bukkitPlayer != null && !playerName.equals(bukkitPlayer.getName()))
            updateDate = new MofuPlayerData(id, bukkitPlayer.getName(), settings);

        try {
            MofuAssistant.getInstance().getMofuAssistantTable().updatePlayerData(updateDate);
        } catch (SQLException e) {
            MofuAssistant.getInstance().getLogger().log(Level.WARNING, "Failed to update the player data.", e);
        }
    }


    public static class PlayerSettings {
        private boolean peacefulMode;


        public boolean isPeacefulMode() {
            return peacefulMode;
        }

        public void setPeacefulMode(boolean peacefulMode) {
            this.peacefulMode = peacefulMode;
        }
    }
}
