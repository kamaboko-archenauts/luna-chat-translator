package dev.lunachat;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.*;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class LunaChatClient implements ClientModInitializer {
    private static LunaChatClient INSTANCE;
    private final Map<ChatHudLine, ChatHudLine> translations = new IdentityHashMap<>();
    private final Set<ChatHudLine> arrivals = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<ChatHudLine> queued = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<Job> queue = new ArrayDeque<>();
    private final Map<String, String> cache = new LinkedHashMap<>(512, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> e) { return size() > 512; }
    };
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> Thread.ofPlatform().daemon().name("luna-translator").unstarted(r));
    private TranslatorConfig config;
    private CodexBridge bridge;
    private UsageLedger usage;
    private final Set<ChatHudLine> localMessages = Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean writingLocal;
    private KeyBinding translateKey, toggleKey, originalKey;
    private boolean automatic, busy;
    private volatile long epoch;
    private long nextBatchAt;
    private record Job(ChatAccess.Selection selection, boolean automatic) { }

    @Override public void onInitializeClient() {
        INSTANCE = this;
        try { config = TranslatorConfig.load(FabricLoader.getInstance().getConfigDir().resolve("luna-chat.json")); }
        catch (Exception e) {
            config = new TranslatorConfig();
            LoggerFactory.getLogger("luna_chat").warn("Could not load luna-chat.json; using defaults");
        }
        Path home = FabricLoader.getInstance().getGameDir().resolve("luna-chat-private");
        usage = new UsageLedger();
        bridge = new CodexBridge(config, home, LunaChatClient::notice, usage);
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("luna_chat", "keys"));
        translateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.luna_chat.translate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.luna_chat.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F7, category));
        originalKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.luna_chat.original", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, category));
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { automatic = false; reset(); });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> worker.submit(() -> { try { bridge.warmup(); } catch (Exception ignored) { } }));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> { bridge.close(); worker.shutdownNow(); });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("lunachat")
            .executes(ctx -> { openSettings(); return 1; })
            .then(literal("usage").executes(ctx -> { showUsage(); return 1; }))
            .then(literal("click").executes(ctx -> { toggleClick(); return 1; }))
            .then(literal("earthmc").executes(ctx -> { toggleEarth(); return 1; }))
            .then(literal("login").executes(ctx -> { login(); return 1; }))
            .then(literal("status").executes(ctx -> { accountTask(() -> notice(bridge.status())); return 1; }))
            .then(literal("logout").executes(ctx -> { automatic = false; reset(); accountTask(() -> { bridge.logout(); notice("ログアウトしました"); }); return 1; }))
            .then(literal("toggle").executes(ctx -> { toggle(); return 1; }))
            .then(literal("translate").executes(ctx -> { openPicker(); return 1; }))
            .then(literal("original").executes(ctx -> { restore(); return 1; }))));
    }

    public static ChatHudLine displayLine(ChatHudLine original) {
        return INSTANCE == null ? original : INSTANCE.translations.getOrDefault(original, original);
    }
    public static void received(ChatHudLine line) {
        if (INSTANCE == null) return;
        if (INSTANCE.writingLocal) { INSTANCE.localMessages.add(line); return; }
        if (INSTANCE.automatic && eligible(line)) INSTANCE.arrivals.add(line);
    }
    public static void clearTranslations() { if (INSTANCE != null) INSTANCE.reset(); }

    public static boolean handleChatKey(KeyInput key) {
        LunaChatClient self = INSTANCE;
        if (self == null) return false;
        if (self.translateKey.matchesKey(key)) { self.toggleClick(); return true; }
        if (self.toggleKey.matchesKey(key)) { self.toggle(); return true; }
        if (self.originalKey.matchesKey(key)) { self.restore(); return true; }
        return false;
    }

    private ChatAccess hud() { return (ChatAccess) MinecraftClient.getInstance().inGameHud.getChatHud(); }

    private void tick(MinecraftClient client) {
        while (translateKey.wasPressed()) if (client.currentScreen == null) openPicker();
        while (toggleKey.wasPressed()) if (client.currentScreen == null) toggle();
        while (originalKey.wasPressed()) if (client.currentScreen == null) restore();
        if (client.world == null) return;
        Set<ChatHudLine> alive = Collections.newSetFromMap(new IdentityHashMap<>());
        alive.addAll(hud().luna$messages());
        translations.keySet().retainAll(alive);
        localMessages.retainAll(alive);
        arrivals.retainAll(alive);
        if (automatic && !arrivals.isEmpty()) {
            for (ChatAccess.Selection selection : hud().luna$visible()) {
                if (arrivals.remove(selection.line())) enqueue(selection, true);
            }
            // Messages hidden by scroll/F1/visibility settings must never be translated later implicitly.
            arrivals.clear();
        }
        if (!busy && !queue.isEmpty() && System.currentTimeMillis() >= nextBatchAt) dispatch();
    }

    private void enqueue(ChatAccess.Selection selection, boolean auto) {
        if (!eligible(selection.line())) return;
        String text = selection.line().content().getString().substring(selection.start(), selection.end());
        if (text.isBlank() || text.length() > 8192 || translations.containsKey(selection.line()) || queued.contains(selection.line())) return;
        if (queue.size() >= 100) return;
        if (queue.isEmpty()) nextBatchAt = System.currentTimeMillis() + config.batchDelayMillis;
        if (auto) queue.addLast(new Job(selection, true));
        else { queue.addFirst(new Job(selection, false)); nextBatchAt = 0; }
        queued.add(selection.line());
    }

    private void toggle() {
        if (MinecraftClient.getInstance().world == null) return;
        automatic = !automatic;
        if (!automatic) cancel();
        notice("自動翻訳 " + (automatic ? "ON（新着から）" : "OFF"));
    }

    private void restore() {
        automatic = false;
        reset();
        hud().luna$refreshKeepingScroll();
        notice("原文に戻しました（自動翻訳OFF）");
    }

    private void cancel() {
        epoch++;
        queue.clear(); queued.clear(); arrivals.clear();
        if (bridge != null) bridge.close();
        // busy stays true until the cancelled worker actually exits.
    }
    private void reset() { cancel(); translations.clear(); }

    private void dispatch() {
        List<Job> batch = new ArrayList<>();
        List<ChatAccess.Selection> visibleNow = hud().luna$visible();
        List<ChatHudLine> alive = hud().luna$messages();
        while (!queue.isEmpty() && batch.size() < config.batchSize) {
            Job job = queue.removeFirst();
            // Identity, not message text: duplicate chat must remain distinct.
            boolean exists = alive.stream().anyMatch(line -> line == job.selection.line());
            boolean visible = visibleNow.stream().anyMatch(s -> s.line() == job.selection.line()
                    && s.start() <= job.selection.start() && s.end() >= job.selection.end());
            if (!exists || !eligible(job.selection.line()) || (job.automatic && (!automatic || !visible))) { queued.remove(job.selection.line()); continue; }
            if (!batch.isEmpty() && batch.getFirst().automatic != job.automatic) { queue.addFirst(job); break; }
            batch.add(job);
        }
        if (batch.isEmpty()) return;
        busy = true;
        long generation = epoch;
        List<String> texts = batch.stream().map(j -> j.selection.line().content().getString().substring(j.selection.start(), j.selection.end())).toList();
        String language = batch.getFirst().automatic ? config.targetLanguage : "Japanese";
        List<String> cached = texts.stream().map(t -> cache.get(language + "\n" + t)).toList();
        worker.submit(() -> {
            try {
                List<String> result = new ArrayList<>(cached);
                List<String> missing = new ArrayList<>();
                List<Integer> indexes = new ArrayList<>();
                for (int i = 0; i < texts.size(); i++) if (result.get(i) == null) { indexes.add(i); missing.add(texts.get(i)); }
                if (!missing.isEmpty()) {
                    List<String> unique = new ArrayList<>(new LinkedHashSet<>(missing));
                    List<String> translated = bridge.translate(unique, language, () -> epoch == generation);
                    for (int i = 0; i < indexes.size(); i++) result.set(indexes.get(i), translated.get(unique.indexOf(missing.get(i))));
                }
                MinecraftClient.getInstance().execute(() -> {
                    if (generation == epoch) {
                        List<ChatHudLine> current = hud().luna$messages();
                        for (int i = 0; i < batch.size(); i++) {
                            ChatAccess.Selection s = batch.get(i).selection;
                            if (current.stream().noneMatch(line -> line == s.line())) continue;
                            Text replaced = ChatText.replace(s.line().content(), s.start(), s.end(), result.get(i));
                            MessageIndicator indicator = new MessageIndicator(0x7DB8FF, null,
                                    Text.literal("Luna 翻訳（自分の画面のみ）\n原文: ").append(s.line().content()), "Luna");
                            translations.put(s.line(), new ChatHudLine(s.line().creationTick(), replaced, s.line().signature(), indicator));
                            cache.put(language + "\n" + texts.get(i), result.get(i));
                        }
                        hud().luna$refreshKeepingScroll();
                    }
                    finish(batch, generation);
                });
            } catch (Exception error) {
                MinecraftClient.getInstance().execute(() -> {
                    if (generation == epoch) {
                        automatic = false;
                        queue.clear(); queued.clear();
                        Throwable cause = error;
                        while (cause.getCause() != null) cause = cause.getCause();
                        String detail = cause instanceof TimeoutException ? "タイムアウト" : cause.getMessage();
                        notice("翻訳停止: " + (detail == null ? "接続エラー" : detail.substring(0, Math.min(200, detail.length()))));
                    }
                    finish(batch, generation);
                });
            }
        });
    }

    private void finish(List<Job> batch, long generation) {
        if (generation == epoch) batch.forEach(j -> queued.remove(j.selection.line()));
        busy = false;
        nextBatchAt = System.currentTimeMillis() + config.batchDelayMillis;
    }

    private void login() {
        notice("翻訳対象の文字はOpenAIへ送信され、各自のCodex利用枠を使います。ブラウザでログインしてください");
        accountTask(() -> {
            String url = bridge.login();
            java.net.URI uri = java.net.URI.create(url);
            String host = uri.getHost();
            if (!"https".equals(uri.getScheme()) || host == null
                    || !(host.equals("chatgpt.com") || host.endsWith(".chatgpt.com") || host.equals("openai.com") || host.endsWith(".openai.com")))
                throw new java.io.IOException("Unexpected login URL");
            MinecraftClient.getInstance().execute(() -> Util.getOperatingSystem().open(uri));
        });
    }

    @FunctionalInterface private interface AccountAction { void run() throws Exception; }
    private void accountTask(AccountAction action) {
        worker.submit(() -> {
            try { action.run(); }
            catch (Exception e) { notice("Codex接続エラー: " + (e.getCause() == null ? e.getMessage() : e.getCause().getMessage())); }
        });
    }

    private static void notice(String text) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> { if (client.player != null) client.player.sendMessage(Text.literal("[Luna] " + text), true); });
    }

    public static LunaChatClient instance() { return INSTANCE; }
    public boolean automatic() { return automatic; }
    public boolean clicking() { return config.clickToTranslate; }
    public boolean earthMc() { return config.earthMcMode; }
    public List<String> usageLines() { return usage.lines(); }
    public static boolean eligible(ChatHudLine line) {
        return INSTANCE != null && !INSTANCE.localMessages.contains(line)
                && !(INSTANCE.config.earthMcMode && EarthMcFilter.ignore(line.content().getString()));
    }
    public static boolean canClick() { return INSTANCE != null && INSTANCE.config.clickToTranslate; }
    public static void click(ChatAccess.Selection selection) {
        if (selection != null && INSTANCE != null) {
            INSTANCE.enqueue(selection, false);
            notice("選択したチャットを翻訳中…");
        }
    }
    public void translateSelected(List<ChatAccess.Selection> selections) {
        for (int i = selections.size() - 1; i >= 0; i--) enqueue(selections.get(i), false);
        if (!selections.isEmpty()) notice("選択したチャットをまとめて翻訳中…");
    }

    public void translateDraft(String draft, java.util.function.Consumer<String> success, Runnable failure) {
        if (draft.isBlank() || draft.startsWith("/") || draft.length() > 8192) { failure.run(); return; }
        String key = "English\n" + draft;
        String saved = cache.get(key);
        if (saved != null) { success.accept(saved); return; }
        long generation = epoch;
        worker.submit(() -> {
            try {
                String result = bridge.translate(List.of(draft), "English", () -> epoch == generation).getFirst();
                MinecraftClient.getInstance().execute(() -> {
                    if (generation != epoch) { failure.run(); return; }
                    cache.put(key, result);
                    success.accept(result);
                });
            } catch (Exception e) {
                MinecraftClient.getInstance().execute(() -> { failure.run(); notice("入力文の英訳に失敗しました。原文は保持されています"); });
            }
        });
    }
    public void toggleAutomatic() { toggle(); }
    public void restoreOriginal() { restore(); }
    public void beginLogin() { login(); }
    public void toggleClick() {
        config.clickToTranslate = !config.clickToTranslate; saveConfig();
        notice("選択翻訳 " + (config.clickToTranslate ? "ON" : "OFF"));
    }
    public void toggleEarth() {
        config.earthMcMode = !config.earthMcMode; saveConfig();
        // Cancel queued/in-flight content immediately when the exclusion policy changes.
        cancel();
        notice("EarthMC mode " + (config.earthMcMode ? "ON" : "OFF"));
    }
    private void saveConfig() {
        try { config.save(FabricLoader.getInstance().getConfigDir().resolve("luna-chat.json")); }
        catch (Exception e) { notice("設定を保存できませんでした"); }
    }
    public void openPicker() {
        config.clickToTranslate = true; saveConfig();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) client.execute(() -> client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("", false)));
    }
    public void openSettings() {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> client.setScreen(new LunaSettingsScreen(null)));
    }
    public void showUsage() {
        writingLocal = true;
        try { for (String line : usage.lines()) MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(Text.literal("[Luna] " + line)); }
        finally { writingLocal = false; }
    }
}
