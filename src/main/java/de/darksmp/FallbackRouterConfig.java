package de.darksmp;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FallbackRouterConfig {

    public static String language = "en";

    @SuppressWarnings("unchecked")
    public static Map<String, List<String>> load(Path dataDirectory) throws Exception {
        Path configPath = dataDirectory.resolve("config.yml");

        if (!Files.exists(configPath)) {
            Files.createDirectories(dataDirectory);
            Files.writeString(configPath, "language: \"en\"\n\nfallbacks:\n  smp:\n    - smpfb\n    - lobby\n  prac:\n    - pracfb\n    - lobby");
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Object configObj = yaml.load(in);

            if (configObj instanceof Map<?, ?> config) {
                if (config.get("language") instanceof String langCode) {
                    language = langCode;
                }
                Object fallbacksObj = config.get("fallbacks");
                if (fallbacksObj instanceof Map<?, ?> typedFallbacks) {
                    Map<String, List<String>> fallbackMap = new HashMap<>();
                    for (Map.Entry<?, ?> entry : typedFallbacks.entrySet()) {
                        if (entry.getKey() instanceof String && entry.getValue() instanceof List<?> list) {
                            List<String> servers = list.stream()
                                    .filter(e -> e instanceof String)
                                    .map(e -> (String) e)
                                    .toList();
                            fallbackMap.put((String) entry.getKey(), servers);
                        }
                    }
                    return fallbackMap;
                } else {
                    throw new IllegalArgumentException("Invalid structure for 'fallbacks' in config.yml");
                }
            } else {
                throw new IllegalArgumentException("Invalid YAML format in config.yml");
            }
        }
    }
}