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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
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

            if (MofuAssistant.getInstance().getPluginConfig().isDebug())
                MofuAssistant.getInstance().getLogger().log(Level.INFO, this.toString());
        } catch (SQLException e) {
            MofuAssistant.getInstance().getLogger().log(Level.WARNING, "Failed to update the player data.", e);
        }
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerSettings {
        private List<String> peacefulEnabledWorld;

        public boolean isPeacefulMode(World world) {
            if (peacefulEnabledWorld == null)
                peacefulEnabledWorld = new ArrayList<>();

            if (MofuAssistant.getInstance().getPluginConfig().getPeacefulModeConfig().keepChangeWorld())
                return !peacefulEnabledWorld.isEmpty();
            else
                return peacefulEnabledWorld.contains(world.getName());
        }

        public void setPeacefulMode(World world, boolean enable) {
            if (peacefulEnabledWorld == null)
                peacefulEnabledWorld = new ArrayList<>();

            if (enable) {
                if (!peacefulEnabledWorld.contains(world.getName()))
                    peacefulEnabledWorld.add(world.getName());
            } else
                peacefulEnabledWorld.remove(world.getName());
        }


        @Override
        public String toString() {
            return "PlayerSettings{" +
                    "peacefulEnabledWorld=" + peacefulEnabledWorld +
                    '}';
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        MofuPlayerData that = (MofuPlayerData) o;

        return new EqualsBuilder().append(getId(), that.getId()).append(getPlayerName(), that.getPlayerName()).append(getBukkitPlayer(), that.getBukkitPlayer()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(getId()).append(getPlayerName()).append(getBukkitPlayer()).toHashCode();
    }

    @Override
    public String toString() {
        return "MofuPlayerData{" +
                "id=" + id +
                ", playerName='" + playerName + '\'' +
                ", bukkitPlayer=" + bukkitPlayer +
                ", settings=" + settings +
                '}';
    }
}
