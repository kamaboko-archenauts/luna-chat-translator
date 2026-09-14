package dev.lunachat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TranslatorConfig {
    public String codexExecutable = "codex";
    public String model = "gpt-5.6-luna";
    public String targetLanguage = "Japanese";
    public int timeoutSeconds = 60;
    public int batchSize = 12;
    public int batchDelayMillis = 500;
    public boolean clickToTranslate = true;
    public boolean earthMcMode = false;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public void save(Path path) throws IOException { Files.writeString(path, JSON.toJson(this)); }

    public static TranslatorConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            TranslatorConfig config = new TranslatorConfig();
            Files.writeString(path, JSON.toJson(config));
            return config;
        }
        TranslatorConfig c = JSON.fromJson(Files.readString(path), TranslatorConfig.class);
        if (c == null || c.codexExecutable == null || c.codexExecutable.isBlank()
                || c.model == null || c.model.isBlank() || c.targetLanguage == null || c.targetLanguage.isBlank())
            throw new IOException("Invalid luna-chat.json");
        c.timeoutSeconds = Math.clamp(c.timeoutSeconds, 10, 180);
        c.batchSize = Math.clamp(c.batchSize, 1, 24);
        c.batchDelayMillis = Math.clamp(c.batchDelayMillis, 100, 5000);
        return c;
    }
}
