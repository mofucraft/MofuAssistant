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

package page.nafuchoco.mofu.mofuassistant.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Level;

/**
 * コミュニティアイテムの保存と読み込みを管理するクラス
 */
public class CommunityItemStorage {
    private static final String ITEM_FILE = "community_item.yml";
    private final MofuAssistant plugin;
    private final File itemFile;

    public CommunityItemStorage(MofuAssistant plugin) {
        this.plugin = plugin;
        this.itemFile = new File(plugin.getDataFolder(), ITEM_FILE);
    }

    /**
     * アイテムをBase64エンコードして保存
     */
    public void saveItem(ItemStack item) {
        try {
            YamlConfiguration config = new YamlConfiguration();
            String encodedItem = itemToBase64(item);
            config.set("item", encodedItem);
            config.save(itemFile);
            plugin.getLogger().log(Level.INFO, "コミュニティアイテムを保存しました。");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "アイテムの保存中にエラーが発生しました。", e);
        }
    }

    /**
     * 保存されたアイテムを読み込み
     */
    public ItemStack loadItem() {
        if (!itemFile.exists()) {
            return null;
        }

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(itemFile);
            String encodedItem = config.getString("item");
            if (encodedItem == null || encodedItem.isEmpty()) {
                return null;
            }
            return itemFromBase64(encodedItem);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "アイテムの読み込み中にエラーが発生しました。", e);
            return null;
        }
    }

    /**
     * 保存されたアイテムが存在するかチェック
     */
    public boolean hasItem() {
        return itemFile.exists() && loadItem() != null;
    }

    /**
     * ItemStackをBase64文字列に変換
     */
    private String itemToBase64(ItemStack item) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
        dataOutput.writeObject(item);
        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    /**
     * Base64文字列からItemStackに変換
     */
    private ItemStack itemFromBase64(String data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
        ItemStack item = (ItemStack) dataInput.readObject();
        dataInput.close();
        return item;
    }
}
