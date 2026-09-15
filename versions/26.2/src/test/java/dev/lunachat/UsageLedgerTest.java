package dev.lunachat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class UsageLedgerTest {
    private com.google.gson.JsonObject event(long n) {
        return TranslationProtocol.object("total", TranslationProtocol.object("totalTokens", n, "inputTokens", n - 10,
                "outputTokens", 10, "cachedInputTokens", 5, "reasoningOutputTokens", 2));
    }
    @Test void deduplicatesCumulativeEvents() {
        UsageLedger ledger = new UsageLedger();
        ledger.begin("a"); ledger.update("a", event(100)); ledger.update("a", event(100));
        ledger.update("a", event(120)); ledger.update("a", event(100));
        ledger.begin("b"); ledger.update("b", event(50));
        assertTrue(ledger.lines().get(1).contains("合計 170 tokens"));
        assertTrue(ledger.lines().get(3).contains("翻訳依頼 2 回 / 使用量受信 2 回"));
    }
    @Test void newMinecraftSessionStartsAtZero() {
        UsageLedger oldLaunch = new UsageLedger(); oldLaunch.begin("a"); oldLaunch.update("a", event(100));
        UsageLedger newLaunch = new UsageLedger();
        assertTrue(newLaunch.lines().get(1).contains("合計 0 tokens"));
        assertTrue(newLaunch.lines().get(0).contains("まだ翻訳していません"));
    }
    @Test void unknownUsageIsMarkedRatherThanInvented() {
        UsageLedger ledger = new UsageLedger(); ledger.begin("a"); ledger.update("a", TranslationProtocol.object());
        assertTrue(ledger.lines().get(3).contains("未報告"));
    }
    @Test void readingDoesNotStartMeasurement() {
        UsageLedger ledger = new UsageLedger(); ledger.lines(); ledger.lines();
        assertTrue(ledger.lines().get(0).contains("まだ翻訳していません"));
    }
}
