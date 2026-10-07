package com.linkedshield.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * settings.json 的读写 + config/linkedshield/config_editor.html 的生成。
 * 这份 JSON 就是模组唯一的配置来源（游戏内 /linkedshield reload 热重载）。
 */
public final class ConfigManager {

    /** 改这个数字会让客户端下次启动时覆盖旧的 HTML（用于升级编辑器界面）。 */
    public static final String EDITOR_VERSION = "12";

    private static final Logger LOG = LoggerFactory.getLogger("LinkedShield/Config");
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private static final String TOKEN_SETTINGS = "__LINKEDSHIELD_SETTINGS_JSON__";
    private static final String TOKEN_DEFAULTS = "__LINKEDSHIELD_DEFAULTS_JSON__";
    private static final String TOKEN_VERSION = "__LINKEDSHIELD_EDITOR_VERSION__";
    private static final String TOKEN_I18N = "__LINKEDSHIELD_EDITOR_I18N__";

    /** HTML 编辑器提供切换的语言（与 MC 的语言代码一致）。 */
    public static final String[] EDITOR_LANGS = {"zh_cn", "en_us", "en_gb", "ru_ru"};
    /** 编辑器翻译键前缀。 */
    private static final String EDITOR_PREFIX = "linkedshield.editor.";

    private static volatile LinkedShieldSettings current = new LinkedShieldSettings();

    private ConfigManager() {
    }

    public static LinkedShieldSettings get() {
        return current;
    }

    public static Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve("linkedshield");
    }

    public static Path settingsFile() {
        return configDir().resolve("settings.json");
    }

    public static Path htmlFile() {
        return configDir().resolve("config_editor.html");
    }

    /** 启动时调用：确保目录/文件存在，合并缺省值，并按需生成 HTML。 */
    public static synchronized LinkedShieldSettings load() {
        LinkedShieldSettings loaded = new LinkedShieldSettings();
        try {
            Files.createDirectories(configDir());
            current = loaded = readSettings();
            save(loaded);
            writeHtmlEditor(loaded);
            LOG.info("[LinkedShield] 配置文件: {}", settingsFile().toAbsolutePath());
            LOG.info("[LinkedShield] HTML 配置编辑器: {}", htmlFile().toAbsolutePath());
        } catch (Exception e) {
            LOG.error("[LinkedShield] 读取配置失败，使用内置默认值", e);
            current = new LinkedShieldSettings();
        }
        return current;
    }

    /** /linkedshield reload 用。 */
    public static synchronized LinkedShieldSettings reload() {
        return load();
    }

    private static LinkedShieldSettings readSettings() throws IOException {
        Path file = settingsFile();
        LinkedShieldSettings defaults = new LinkedShieldSettings();
        if (!Files.exists(file)) {
            LOG.info("[LinkedShield] 首次运行，生成默认配置: {}", file.toAbsolutePath());
            return defaults;
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            return defaults;
        }
        JsonObject merged;
        try {
            JsonObject onDisk = JsonParser.parseString(text).getAsJsonObject();
            JsonObject defaultJson = GSON.toJsonTree(defaults).getAsJsonObject();
            merged = deepMerge(defaultJson, onDisk);
        } catch (Exception e) {
            LOG.warn("[LinkedShield] settings.json 解析失败（{}），本次使用默认值", e.getMessage());
            return defaults;
        }
        return GSON.fromJson(merged, LinkedShieldSettings.class);
    }

    public static synchronized void save(LinkedShieldSettings settings) {
        try {
            Files.createDirectories(configDir());
            Path file = settingsFile();
            Path tmp = configDir().resolve("settings.json.tmp");
            Files.writeString(tmp, GSON.toJson(settings) + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOG.error("[LinkedShield] 保存配置失败", e);
        }
    }

    /** 把内嵌了当前配置的 HTML 编辑器写到 config/linkedshield/。 */
    public static synchronized void writeHtmlEditor(LinkedShieldSettings settings) {
        if (!settings.misc.generateHtmlEditor) {
            return;
        }
        Path target = htmlFile();
        try {
            if (Files.exists(target) && EDITOR_VERSION.equals(readEditorVersion(target))) {
                return;
            }
            String template = readResource("/linkedshield/config_editor.html");
            if (template == null) {
                LOG.warn("[LinkedShield] 找不到内置的 config_editor.html 模板，跳过生成");
                return;
            }
            String defaultsJson = GSON.toJson(new LinkedShieldSettings());
            String settingsJson = GSON.toJson(settings);
            // 注意：String.replace 的替换串是“字面量”，这里绝不能再套 Matcher.quoteReplacement，
            // 否则内嵌 JSON 里的 \" 会被写成 \\"，注入 HTML 后就是一段坏掉的 JS。
            String html = template
                    .replace(TOKEN_VERSION, EDITOR_VERSION)
                    .replace(TOKEN_DEFAULTS, defaultsJson)
                    .replace(TOKEN_SETTINGS, settingsJson)
                    .replace(TOKEN_I18N, editorI18nJson());
            Files.createDirectories(configDir());
            Files.writeString(target, html, StandardCharsets.UTF_8);
            LOG.info("[LinkedShield] 已生成 HTML 配置编辑器: {}", target.toAbsolutePath());
        } catch (Exception e) {
            LOG.error("[LinkedShield] 生成 HTML 配置编辑器失败", e);
        }
    }

    private static String readEditorVersion(Path html) {
        try {
            String text = Files.readString(html, StandardCharsets.UTF_8);
            int i = text.indexOf("linkedshield-editor-version");
            if (i < 0) {
                return null;
            }
            int q1 = text.indexOf('"', i);
            int q2 = q1 < 0 ? -1 : text.indexOf('"', q1 + 1);
            int q3 = q2 < 0 ? -1 : text.indexOf('"', q2 + 1);
            int q4 = q3 < 0 ? -1 : text.indexOf('"', q3 + 1);
            if (q3 < 0 || q4 < 0) {
                return null;
            }
            return text.substring(q3 + 1, q4);
        } catch (IOException e) {
            return null;
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = ConfigManager.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 把各语言 lang 文件里 {@code linkedshield.editor.*} 的键抽出来，内嵌进 HTML，
     * 这样配置页就能用右上角的国旗按钮切换语言（MC 语言代码：zh_cn / en_us / en_gb / ru_ru）。
     * 缺键由前端按 en_us → zh_cn 的顺序回退。
     */
    private static String editorI18nJson() {
        JsonObject all = new JsonObject();
        for (String lang : EDITOR_LANGS) {
            JsonObject dict = new JsonObject();
            try {
                String raw = readResource("/assets/linkedshield/lang/" + lang + ".json");
                if (raw != null) {
                    JsonObject json = JsonParser.parseString(raw).getAsJsonObject();
                    for (String key : json.keySet()) {
                        if (key.startsWith(EDITOR_PREFIX) && json.get(key).isJsonPrimitive()) {
                            dict.addProperty(key, json.get(key).getAsString());
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warn("[LinkedShield] 读取 {} 的编辑器翻译失败：{}", lang, e.getMessage());
            }
            all.add(lang, dict);
            LOG.info("[LinkedShield] 编辑器翻译 {}: {} 条", lang, dict.size());
        }
        return GSON.toJson(all);
    }

    private static JsonObject deepMerge(JsonObject base, JsonObject override) {
        JsonObject out = base.deepCopy();
        for (String key : override.keySet()) {
            if (out.has(key) && out.get(key).isJsonObject() && override.get(key).isJsonObject()) {
                out.add(key, deepMerge(out.getAsJsonObject(key), override.getAsJsonObject(key)));
            } else {
                out.add(key, override.get(key).deepCopy());
            }
        }
        return out;
    }

    /** 供聊天栏可点击链接使用。 */
    public static String htmlUri() {
        return htmlFile().toAbsolutePath().toUri().toString();
    }

    /**
     * 聊天栏里“点一下就用系统默认程序打开 HTML 配置页”的组件。
     * 只有本机玩家（单人 / 局域网房主）点得动，远程玩家的文件不在这台机器上。
     */
    public static net.minecraft.network.chat.MutableComponent clickableHtmlLink() {
        net.minecraft.network.chat.MutableComponent link =
                net.minecraft.network.chat.Component.literal("config/linkedshield/config_editor.html")
                        .withStyle(net.minecraft.ChatFormatting.UNDERLINE)
                        .withStyle(net.minecraft.ChatFormatting.AQUA);
        link.withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenFile(htmlFile().toFile())));
        return link;
    }
}
