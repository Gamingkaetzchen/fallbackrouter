package de.darksmp;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** A configured rejection must also prevent Velocity's default redirect. */
public final class KickReasonPolicy {
    private static final List<String> DEFAULT_REASONS = List.of("server closed", "disconnected",
            "timed out", "server_shutdown", "server shutdown", "server restarting",
            "server is restarting", "server shutting down", "server is shutting down",
            "server stopping", "server is stopping");
    private final boolean configured;
    private final boolean whitelist;
    private final boolean fallbackOnNoReason;
    private final List<Predicate<String>> reasons;

    private KickReasonPolicy(boolean configured, boolean whitelist, boolean fallbackOnNoReason,
                             List<Predicate<String>> reasons) {
        this.configured = configured;
        this.whitelist = whitelist;
        this.fallbackOnNoReason = fallbackOnNoReason;
        this.reasons = List.copyOf(reasons);
    }

    public static KickReasonPolicy from(Map<?, ?> config) {
        if (!config.containsKey("kick-reasons")) {
            return new KickReasonPolicy(false, true, true, DEFAULT_REASONS.stream()
                    .map(KickReasonPolicy::matcher).toList());
        }
        if (!(config.get("kick-reasons") instanceof Map<?, ?> section)) {
            throw new IllegalArgumentException("'kick-reasons' must be a map");
        }
        boolean whitelist = bool(section, "whitelist", true);
        boolean noReason = bool(section, "fallback-on-no-reason", true);
        if (!(section.get("reasons") instanceof List<?> list)) {
            throw new IllegalArgumentException("'kick-reasons.reasons' must be a list of strings");
        }
        List<Predicate<String>> matchers = list.stream().map(value -> {
            if (!(value instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException("'kick-reasons.reasons' entries must be non-empty strings");
            }
            return matcher(text);
        }).toList();
        return new KickReasonPolicy(true, whitelist, noReason, matchers);
    }

    private static boolean bool(Map<?, ?> section, String name, boolean defaultValue) {
        if (!section.containsKey(name)) return defaultValue;
        if (!(section.get(name) instanceof Boolean value)) {
            throw new IllegalArgumentException("'kick-reasons." + name + "' must be true or false");
        }
        return value;
    }

    private static Predicate<String> matcher(String text) {
        if (!text.startsWith("regex:")) {
            String needle = text.toLowerCase(Locale.ROOT);
            return reason -> reason.toLowerCase(Locale.ROOT).contains(needle);
        }
        String expression = text.substring("regex:".length());
        if (expression.isBlank()) throw new IllegalArgumentException("Empty regex in 'kick-reasons.reasons'");
        try {
            Pattern pattern = Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return reason -> pattern.matcher(reason).find();
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex in 'kick-reasons.reasons': " + text, e);
        }
    }

    public boolean configured() {
        return configured;
    }

    public boolean allows(Optional<Component> kickReason) {
        String reason = kickReason.map(component -> PlainTextComponentSerializer.plainText().serialize(component))
                .orElse("").replaceAll("(?i)\u00a7[0-9a-fk-orx]", "");
        // A socket failure can have no backend reason at all. Treat it independently
        // of whitelist inversion, or an empty reason would change meaning between modes.
        if (reason.isBlank()) return fallbackOnNoReason;
        boolean matched = reasons.stream().anyMatch(rule -> rule.test(reason));
        return whitelist == matched;
    }
}
