package de.darksmp;

import java.io.InputStream;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public class Messages {

    private final Map<String, Object> lang;
    private final Map<String, Object> fallback;

    public Messages(String language) {
        this.lang = loadLanguage(language);
        this.fallback = loadLanguage("en");
    }

    private Map<String, Object> loadLanguage(String code) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + code + ".yml")) {
            if (in == null) return Map.of();
            return new Yaml().load(in);
        } catch (Exception e) {
            return Map.of();
        }
    }

    public String get(String key, Map<String, String> placeholders) {
        String value = getRaw(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            value = value.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return value;
    }

    public String getRaw(String key) {
        String[] parts = key.split("\\\\.");
        Object current = lang;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map && map.containsKey(part)) {
                current = map.get(part);
            } else {
                current = getFallback(parts);
                break;
            }
        }
        return current instanceof String ? (String) current : key;
    }

    private Object getFallback(String[] parts) {
        Object current = fallback;
        for (String part : parts) {
            if (current instanceof Map<?, ?> map && map.containsKey(part)) {
                current = map.get(part);
            } else return null;
        }
        return current;
    }
}