package dev.lunachat;

import java.nio.file.Path;

/** Optional local smoke check: initialize + account/read only; no login or generation. */
public final class CodexBridgeSmoke {
    public static void main(String[] args) throws Exception {
        TranslatorConfig config = new TranslatorConfig();
        config.timeoutSeconds = 20;
        try (CodexBridge bridge = new CodexBridge(config, Path.of(args[0]), ignored -> {})) {
            String status = bridge.status();
            if (!status.contains("未ログイン")) throw new AssertionError("Expected fresh unauthenticated home");
            System.out.println("PASS: app-server initialize and account/read; fresh isolated authentication home");
        }
    }
}
