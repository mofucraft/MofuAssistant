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

package page.nafuchoco.mofu.mofuassistant.listener;

import lombok.val;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import page.nafuchoco.mofu.mofuassistant.MofuAssistantApi;

public class PeacefulModeEventListener implements Listener {

    @EventHandler
    public void onEntityTargetEvent(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && event.getEntity() instanceof Monster) {
            val playerData = MofuAssistantApi.getInstance().getPlayerData(player);
            if (playerData.getSettings().isPeacefulMode()) {
                if (event.getEntity() instanceof Monster
                        && (event.getReason() == EntityTargetEvent.TargetReason.CLOSEST_PLAYER
                        || event.getReason() == EntityTargetEvent.TargetReason.TARGET_ATTACKED_ENTITY
                        || event.getReason() == EntityTargetEvent.TargetReason.TARGET_ATTACKED_NEARBY_ENTITY))
                    event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamageEvent(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            val playerData = MofuAssistantApi.getInstance().getPlayerData(player);
            if (playerData.getSettings().isPeacefulMode()) {
                if (player.getHealth() - event.getDamage() < 20.0) {
                    val effect = new PotionEffect(PotionEffectType.REGENERATION, 100, 2, false, false, false);
                    player.addPotionEffect(effect);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDamageByEntityEvent(EntityDamageByEntityEvent event) {
        boolean cancelled = event.isCancelled(); // 今後追加の可能性
        // プレイヤーに対する攻撃に関する処理
        if (event.getEntity() instanceof Player player) {
            val playerData = MofuAssistantApi.getInstance().getPlayerData(player);
            if (playerData.getSettings().isPeacefulMode()) {
                if (event.getDamager() instanceof TNTPrimed) // TNT爆破の無効化
                    cancelled = true;
            }
        } else if (event.getDamager() instanceof Player player
                && event.getEntity() instanceof Monster) { // プレイヤーによる攻撃に関する処理
            val playerData = MofuAssistantApi.getInstance().getPlayerData(player);
            if (playerData.getSettings().isPeacefulMode() && !player.hasPermission("soloservercore.peaceful.bypass"))
                cancelled = true;
        }

        event.setCancelled(cancelled);
    }

    @EventHandler
    public void onEntityPotionEffectEvent(EntityPotionEffectEvent event) {
        if (event.getEntity() instanceof Player player) {
            val playerData = MofuAssistantApi.getInstance().getPlayerData(player);
            if (playerData.getSettings().isPeacefulMode() && event.getNewEffect() != null) {
                val effectType = event.getNewEffect().getType();
                if (PotionEffectType.POISON.equals(effectType)
                        || PotionEffectType.WITHER.equals(effectType)
                        || PotionEffectType.HUNGER.equals(effectType)
                        || PotionEffectType.CONFUSION.equals(effectType)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onFoodLevelChangeEvent(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player) {
            val playerData = MofuAssistantApi.getInstance().getPlayerData(player);
            if (playerData.getSettings().isPeacefulMode()) {
                if (event.getFoodLevel() < 20) { // 空腹度の回復
                    val effect = new PotionEffect(PotionEffectType.SATURATION, 40, 0, false, false, false);
                    player.addPotionEffect(effect);
                }
            }
        }
    }
}
