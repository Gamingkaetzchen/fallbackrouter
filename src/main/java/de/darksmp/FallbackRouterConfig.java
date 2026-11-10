package de.darksmp;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FallbackRouterConfig {

    public record LoadedConfig(FallbackRules rules, String language) {}

    public static LoadedConfig load(Path dataDirectory) throws Exception {
        Path configPath = dataDirectory.resolve("config.yml");

        if (!Files.exists(configPath)) {
            Files.createDirectories(dataDirectory);
            Files.writeString(configPath, """
                    language: "en"

                    # Source server -> fallback servers, in priority order.
                    fallbacks:
                      source-a:
                        - fallback-a
                        - lobby
                      source-b:
                        - fallback-b
                        - lobby

                    # Optional inverse format: fallback server -> source servers.
                    # Fallbacks are tried in the order of the keys below.
                    # An explicit entry in 'fallbacks' overrides inverse rules for that source.
                    # To use only inverse rules, remove 'fallbacks' or set it to {}.
                    # fallback-per-servers:
                    #   fallback-a: [source-a]
                    #   fallback-b: [source-b]
                    #   lobby: [source-a, source-b]
                    # Source selectors can use explicit regexes, e.g. 'regex:^source-a-.*$'.
                    # Regexes match the whole source name, case-sensitively.
                    """);
        }

        try (InputStream in = Files.newInputStream(configPath)) {
            Object configObj = new Yaml().load(in);
            if (!(configObj instanceof Map<?, ?> config)) {
                throw new IllegalArgumentException("Invalid YAML format in config.yml");
            }
            String language = "en";
            if (config.get("language") instanceof String langCode) {
                language = langCode;
            }
            if (!config.containsKey("fallbacks") && !config.containsKey("fallback-per-servers")) {
                throw new IllegalArgumentException(
                        "config.yml must contain 'fallbacks' or 'fallback-per-servers'");
            }

            FallbackRules rules = new FallbackRules();
            if (config.containsKey("fallback-per-servers")) {
                if (!(config.get("fallback-per-servers") instanceof Map<?, ?> inverseFallbacks)) {
                    throw new IllegalArgumentException(
                            "Invalid structure for 'fallback-per-servers' in config.yml: expected a map");
                }
                for (Map.Entry<?, ?> entry : inverseFallbacks.entrySet()) {
                    if (!(entry.getKey() instanceof String target) || target.isBlank()
                            || !(entry.getValue() instanceof List<?> sources)) {
                        throw new IllegalArgumentException(
                                "Each 'fallback-per-servers' entry must map a server name to a list of source servers");
                    }
                    List<String> sourceNames = new ArrayList<>();
                    for (Object sourceValue : sources) {
                        if (!(sourceValue instanceof String source) || source.isBlank()) {
                            throw new IllegalArgumentException(
                                    "Invalid source server in 'fallback-per-servers." + target + "': expected a non-empty string");
                        }
                        sourceNames.add(source);
                    }
                    rules.addInverseFallback(target, sourceNames);
                }
            }

            // Apply direct rules last: old configurations remain authoritative,
            // and an explicit [] must disable inverse destinations for that source.
            // Keep the legacy format's parsing and priority, including explicit empty lists.
            if (config.containsKey("fallbacks")) {
                if (!(config.get("fallbacks") instanceof Map<?, ?> typedFallbacks)) {
                    throw new IllegalArgumentException("Invalid structure for 'fallbacks' in config.yml");
                }
                for (Map.Entry<?, ?> entry : typedFallbacks.entrySet()) {
                    if (entry.getKey() instanceof String source && entry.getValue() instanceof List<?> list) {
                        List<String> servers = list.stream()
                                .filter(e -> e instanceof String)
                                .map(e -> (String) e)
                                .toList();
                        rules.addFallback(source, servers);
                    }
                }
            }
            return new LoadedConfig(rules, language);
        }
    }
}
