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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import page.nafuchoco.mofu.mofuassistant.MofuAssistant;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Level;

/**
 * Discord Webhookによる配布通知を送信するクラス
 */
public class DiscordWebhookNotifier {
    private final MofuAssistant plugin;
    private final String webhookUrl;
    private final boolean enabled;

    public DiscordWebhookNotifier(MofuAssistant plugin, String webhookUrl, boolean enabled) {
        this.plugin = plugin;
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;
    }

    /**
     * 配布開始通知を送信
     */
    public void sendDistributionStartNotification(String cycleType, String startTime, String endTime, Map<String, Integer> distributions) {
        if (!enabled || webhookUrl == null || webhookUrl.isEmpty()) {
            return;
        }

        // 非同期で送信
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                sendWebhook(cycleType, startTime, endTime, distributions);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Discord Webhookの送信に失敗しました。", e);
            }
        });
    }

    /**
     * Webhook送信処理
     */
    private void sendWebhook(String cycleType, String startTime, String endTime, Map<String, Integer> distributions) throws IOException {
        URL url = new URL(webhookUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("User-Agent", "MofuAssistant-Bot");
        connection.setDoOutput(true);

        // JSON payload作成
        JsonObject payload = new JsonObject();

        // Embed作成
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🎁 おすそわ券配布開始");
        embed.addProperty("description", cycleType + "が開始されました");
        embed.addProperty("color", 3066993); // 緑色

        // フィールド追加
        JsonArray fields = new JsonArray();

        // 配布期間
        JsonObject periodField = new JsonObject();
        periodField.addProperty("name", "📅 配布期間");
        periodField.addProperty("value", "開始: " + startTime + "\n終了: " + endTime);
        periodField.addProperty("inline", false);
        fields.add(periodField);

        // コミュニティごとの配布数
        if (distributions != null && !distributions.isEmpty()) {
            StringBuilder distributionText = new StringBuilder();
            for (Map.Entry<String, Integer> entry : distributions.entrySet()) {
                distributionText.append("**")
                        .append(entry.getKey())
                        .append("**: ")
                        .append(entry.getValue())
                        .append("個\n");
            }

            JsonObject distributionField = new JsonObject();
            distributionField.addProperty("name", "📦 コミュニティ別配布数");
            distributionField.addProperty("value", distributionText.toString());
            distributionField.addProperty("inline", false);
            fields.add(distributionField);
        }

        embed.add("fields", fields);

        // タイムスタンプ
        embed.addProperty("timestamp", java.time.Instant.now().toString());

        // Footer
        JsonObject footer = new JsonObject();
        footer.addProperty("text", "MofuAssistant おすそわ券システム");
        embed.add("footer", footer);

        // Embedをpayloadに追加
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        // 送信
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = connection.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            plugin.getLogger().log(Level.INFO, "Discord通知を送信しました。");
        } else {
            plugin.getLogger().log(Level.WARNING, "Discord通知の送信に失敗しました。HTTPコード: " + responseCode);
        }

        connection.disconnect();
    }
}
