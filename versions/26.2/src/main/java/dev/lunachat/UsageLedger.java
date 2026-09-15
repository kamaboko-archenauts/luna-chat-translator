package dev.lunachat;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;

/** Memory-only accounting for this Minecraft launch. Never queries a model or the network. */
public final class UsageLedger {
    private final Map<String, long[]> seen = new HashMap<>();
    private static final String[] FIELDS = {"totalTokens", "inputTokens", "cachedInputTokens", "outputTokens", "reasoningOutputTokens"};
    private String startedAt;
    private long requests, reported;
    private final long[] totals = new long[5];
    public synchronized void begin(String threadId) {
        if (seen.containsKey(threadId)) return;
        if (startedAt == null) startedAt = Instant.now().toString();
        requests++; seen.put(threadId, null);
    }
    public synchronized void update(String threadId, JsonObject usage) {
        if (!seen.containsKey(threadId) || usage == null || !usage.has("total")) return;
        try {
            JsonObject total = usage.getAsJsonObject("total");
            for (String field : FIELDS) if (!total.has(field) || total.get(field).isJsonNull()) return;
            long[] previous = seen.get(threadId), current = new long[5];
            for (int i = 0; i < FIELDS.length; i++) current[i] = Math.max(0, total.get(FIELDS[i]).getAsBigDecimal().longValueExact());
            if (previous == null) { reported++; previous = new long[5]; }
            for (int i = 0; i < FIELDS.length; i++) {
                totals[i] += Math.max(0, current[i] - previous[i]);
                current[i] = Math.max(current[i], previous[i]);
            }
            seen.put(threadId, current);
        } catch (RuntimeException ignored) { /* Usage parsing must not interrupt translation. */ }
    }
    public synchronized List<String> lines() {
        return List.of("今回の起動 / 計測開始: " + (startedAt == null ? "まだ翻訳していません" : startedAt),
            "合計 " + totals[0] + " tokens / 入力 " + totals[1] + " / 出力 " + totals[3],
            "入力のキャッシュ分 " + totals[2] + " / 出力の推論分 " + totals[4] + "（内数）",
            "翻訳依頼 " + requests + " 回 / 使用量受信 " + reported + " 回"
                + (reported < requests ? "（未報告分あり・合計は受信済み分のみ）" : ""),
            "確認の生成・通信・トークン消費: 0 / Minecraft再起動でリセット");
    }
}
