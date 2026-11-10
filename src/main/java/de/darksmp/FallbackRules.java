package de.darksmp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Resolves source selectors without treating destination server names as patterns. */
public final class FallbackRules {
    private final Map<String, List<String>> exactFallbacks = new LinkedHashMap<>();
    private final List<PatternFallback> patternFallbacks = new ArrayList<>();
    private final List<InverseFallback> inverseFallbacks = new ArrayList<>();

    void addFallback(String source, List<String> targets) {
        SourceSelector selector = SourceSelector.parse(source, "fallbacks");
        List<String> destinations = List.copyOf(targets);
        if (selector.pattern() == null) {
            exactFallbacks.put(source, destinations);
        } else {
            patternFallbacks.add(new PatternFallback(selector, destinations));
        }
    }

    void addInverseFallback(String target, List<String> sources) {
        List<SourceSelector> selectors = sources.stream()
                .map(source -> SourceSelector.parse(source, "fallback-per-servers." + target))
                .toList();
        inverseFallbacks.add(new InverseFallback(target, selectors));
    }

    public List<String> getFallbacks(String source) {
        // Explicit entries, including [], remain authoritative for backward compatibility.
        if (exactFallbacks.containsKey(source)) {
            return exactFallbacks.get(source);
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        boolean matchedDirectPattern = false;
        for (PatternFallback rule : patternFallbacks) {
            if (rule.selector().matches(source)) {
                matchedDirectPattern = true;
                targets.addAll(rule.targets());
            }
        }
        // Matching an empty direct regex still overrides inverse rules; checking
        // only targets.isEmpty() would accidentally re-enable an excluded source.
        if (matchedDirectPattern) {
            return List.copyOf(targets);
        }
        for (InverseFallback rule : inverseFallbacks) {
            if (rule.sources().stream().anyMatch(selector -> selector.matches(source))) {
                targets.add(rule.target());
            }
        }
        return List.copyOf(targets);
    }

    private record PatternFallback(SourceSelector selector, List<String> targets) {}
    private record InverseFallback(String target, List<SourceSelector> sources) {}

    private record SourceSelector(String literal, Pattern pattern) {
        static SourceSelector parse(String value, String section) {
            if (!value.startsWith("regex:")) {
                return new SourceSelector(value, null);
            }
            String expression = value.substring("regex:".length());
            if (expression.isBlank()) {
                throw new IllegalArgumentException("Empty regex selector in '" + section + "'");
            }
            try {
                return new SourceSelector(null, Pattern.compile(expression));
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        "Invalid regex selector '" + value + "' in '" + section + "': " + e.getDescription(), e);
            }
        }

        boolean matches(String source) {
            return pattern == null ? literal.equals(source) : pattern.matcher(source).matches();
        }
    }
}
