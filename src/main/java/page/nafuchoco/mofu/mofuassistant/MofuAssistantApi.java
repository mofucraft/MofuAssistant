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

import lombok.NonNull;
import lombok.val;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import page.nafuchoco.mofu.mofuassistant.data.MofuPlayerData;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MofuAssistantApi {
    private final MofuAssistant mofuAssistant;
    private final Map<Player, MofuPlayerData> playerStore;

    public static MofuAssistantApi getInstance() {
        return ApiInstanceHolder.INSTANCE;
    }

    public MofuAssistantApi(MofuAssistant mofuAssistant) {
        this.mofuAssistant = mofuAssistant;
        playerStore = new HashMap<>();
    }

    public MofuPlayerData getPlayerData(@NonNull Player player) {
        var playerData = playerStore.get(player);
        if (playerData == null) {
            playerData = getPlayerData(player.getUniqueId());
            playerStore.put(player, playerData);
        }

        return playerData;
    }

    public MofuPlayerData getPlayerData(UUID uuid) {
        var playerData = mofuAssistant.getMofuAssistantTable().getPlayerData(uuid);
        if (playerData == null) {
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            playerData = new MofuPlayerData(offlinePlayer.getUniqueId(), offlinePlayer.getName(), new MofuPlayerData.PlayerSettings());
            try {
                mofuAssistant.getMofuAssistantTable().registerPlayer(playerData);
            } catch (SQLException e) {
                mofuAssistant.getLogger().log(Level.WARNING, "Failed to register the player data.", e);
            }
        }

        if (mofuAssistant.getPluginConfig().isDebug())
            mofuAssistant.getLogger().log(Level.INFO, playerData.toString());
        return playerData;
    }


    void dropStoreData(Player player) {
        playerStore.remove(player);
    }


    private static class ApiInstanceHolder {
        private static final MofuAssistantApi INSTANCE;

        static {
            MofuAssistant core = MofuAssistant.getInstance();
            if (core == null)
                INSTANCE = null;
            else
                INSTANCE = new MofuAssistantApi(core);
        }
    }
}
