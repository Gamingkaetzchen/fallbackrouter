package de.darksmp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public class Messages {
    private final Map<?, ?> lang;
    private final Map<?, ?> bundledLang;
    private final Map<?, ?> fallback;
    private final Map<?, ?> bundledFallback;

    public Messages(Path dataDirectory, String language) throws IOException {
        if (!language.matches("[a-zA-Z0-9_-]+")) {
            throw new IllegalArgumentException("Invalid language name in config.yml: " + language);
        }
        this.lang = loadFile(dataDirectory, language);
        this.bundledLang = loadResource(language);
        this.fallback = language.equals("en") ? lang : loadFile(dataDirectory, "en");
        this.bundledFallback = language.equals("en") ? bundledLang : loadResource("en");
    }

    private Map<?, ?> loadFile(Path dataDirectory, String code) throws IOException {
        Path path = dataDirectory.resolve("lang").resolve(code + ".yml");
        try (InputStream in = Files.exists(path) ? Files.newInputStream(path) : null) {
            return read(in, path.toString());
        }
    }

    private Map<?, ?> loadResource(String code) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + code + ".yml")) {
            return read(in, code + ".yml");
        }
    }

    private Map<?, ?> read(InputStream in, String name) {
        if (in == null) return Map.of();
        Object content = new Yaml().load(in);
        if (!(content instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Invalid language file: " + name + " (expected a map)");
        }
        return map;
    }

    public String get(String key, Map<String, String> placeholders) {
        String value = getRaw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return value;
    }

    public String getRaw(String key) {
        String[] parts = key.split("\\.");
        String value = find(lang, parts);
        // Old custom files may lack newly introduced keys. Prefer bundled text
        // in the selected language before falling back to English, without rewriting files.
        if (value == null) value = find(bundledLang, parts);
        if (value == null) value = find(fallback, parts);
        if (value == null) value = find(bundledFallback, parts);
        return value == null ? key : value;
    }

    private String find(Map<?, ?> root, String[] parts) {
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof Map<?, ?> map)) return null;
            current = map.get(part);
        }
        return current instanceof String value ? value : null;
    }
}
