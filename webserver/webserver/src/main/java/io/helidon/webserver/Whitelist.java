package io.helidon.webserver;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.helidon.config.Config;

/**
 * Whitelist provides a way to define a list of allowed values and to test if a value is within the bounds of the configuration.
 * <p>
 * The algorithm of testing that a value is allowed:
 * <nl>
 * <li>Iterate through all whitelist patterns, if none found, value is not permitted</li>
 * <li>Iterate through all blacklist patterns, if found, value is not permitted</li>
 * <li>Value is permitted</li>
 * </nl>
 */
public interface Whitelist extends Predicate<String> {
    /**
     * Create a fluent API builder to configure an instance.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create whitelist from configurtion.
     *
     * @param config configuration
     * @return a new configured whitelist
     */
    static Whitelist create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Test whether a value can be permitted.
     *
     * @param value value to test against
     * @return {@code true} if the value is whitelisted, {@code false} if it is not whitelisted, or it is explicitly blacklisted
     */
    boolean test(String value);

    /**
     * Fluent API builder for Whitelist
     */
    final class Builder implements io.helidon.common.Builder<Builder, Whitelist> {
        private final List<Predicate<String>> allowedPredicates = new ArrayList<>();
        private final List<Predicate<String>> deniedPredicates = new ArrayList<>();

        private Builder() {
        }

        @Override
        public Whitelist build() {
            return new WhiteListImpl(this);
        }

        /**
         * Update builder from configuration.
         *
         * @param config configuration to use
         * @return updated builder
         */
        public Builder config(Config config) {
            Config allowed = config.get("allow");
            allowed.get("exact").asList(String.class).ifPresent(this::allowed);
            allowed.get("prefix").asList(String.class).ifPresent(this::allowedPrefixes);
            allowed.get("suffix").asList(String.class).ifPresent(this::allowedSuffixes);
            allowed.get("pattern").asList(Pattern.class).ifPresent(this::allowedPatterns);
            allowed.get("all").asBoolean().filter(it -> it).ifPresent(it -> this.allowAll());

            Config denied = config.get("deny");
            denied.get("exact").asList(String.class).ifPresent(this::denied);
            denied.get("prefix").asList(String.class).ifPresent(this::deniedPrefixes);
            denied.get("suffix").asList(String.class).ifPresent(this::deniedSuffixes);
            denied.get("pattern").asList(Pattern.class).ifPresent(this::deniedPatterns);

            return this;
        }

        public Builder allowAll() {
            return addAllowed(new AllowAllPredicate());
        }

        public Builder allowed(List<String> exacts) {
            exacts.forEach(this::addAllowed);
            return this;
        }

        public Builder allowedPrefixes(List<String> prefixes) {
            prefixes.forEach(this::addAllowedPrefix);
            return this;
        }

        public Builder allowedSuffixes(List<String> suffixes) {
            suffixes.forEach(this::addAllowedSuffix);
            return this;
        }

        public Builder allowedPatterns(List<Pattern> patterns) {
            patterns.forEach(this::addAllowedPattern);
            return this;
        }

        public Builder addAllowed(String exact) {
            return addAllowed(new ExactPredicate(exact));
        }

        public Builder addAllowedPattern(Pattern pattern) {
            return addAllowed(new PatternPredicate(pattern));
        }

        public Builder addAllowedPrefix(String prefix) {
            return addAllowed(new PrefixPredicate(prefix));
        }

        public Builder addAllowedSuffix(String suffix) {
            return addAllowed(new SuffixPredicate(suffix));
        }

        public Builder addAllowed(Predicate<String> predicate) {
            this.allowedPredicates.add(predicate);
            return this;
        }

        public Builder denied(List<String> exacts) {
            exacts.forEach(this::addDenied);
            return this;
        }

        public Builder deniedPrefixes(List<String> prefixes) {
            prefixes.forEach(this::addDeniedPrefix);
            return this;
        }

        public Builder deniedSuffixes(List<String> suffixes) {
            suffixes.forEach(this::addDeniedSuffix);
            return this;
        }

        public Builder deniedPatterns(List<Pattern> patterns) {
            patterns.forEach(this::addDeniedPattern);
            return this;
        }

        public Builder addDenied(String exact) {
            return addDenied(new ExactPredicate(exact));
        }

        public Builder addDeniedPattern(Pattern pattern) {
            return addDenied(new PatternPredicate(pattern));
        }

        public Builder addDeniedPrefix(String prefix) {
            return addDenied(new PrefixPredicate(prefix));
        }

        public Builder addDeniedSuffix(String suffix) {
            return addDenied(new SuffixPredicate(suffix));
        }

        public Builder addDenied(Predicate<String> predicate) {
            this.deniedPredicates.add(predicate);
            return this;
        }
        private static final class WhiteListImpl implements Whitelist {

            private final List<Predicate<String>> allowedPredicates;
            private final List<Predicate<String>> deniedPredicates;

            private WhiteListImpl(Builder builder) {
                this.allowedPredicates = List.copyOf(builder.allowedPredicates);
                this.deniedPredicates = List.copyOf(builder.deniedPredicates);
            }

            @Override
            public boolean test(String value) {
                for (Predicate<String> allowedPredicate : allowedPredicates) {
                    if (allowedPredicate.test(value)) {
                        // value is allowed, let's check it is not explicitly denied
                        return testDenied(value);
                    }
                }

                // no allowed predicate, deny
                return false;
            }

            @Override
            public String toString() {
                return "Allowed: " + allowedPredicates + ", Denied: " + deniedPredicates;
            }

            private boolean testDenied(String value) {
                for (Predicate<String> deniedPredicate : deniedPredicates) {
                    if (deniedPredicate.test(value)) {
                        return false;
                    }
                }
                return true;
            }
        }

        private static final class AllowAllPredicate implements Predicate<String> {
            @Override
            public boolean test(String s) {
                return true;
            }

            @Override
            public String toString() {
                return "AllowAllPredicate";
            }
        }

        private static final class ExactPredicate implements Predicate<String> {
            private final String testValue;

            public ExactPredicate(String exact) {
                this.testValue = exact;
            }

            @Override
            public boolean test(String value) {
                return this.testValue.equals(value);
            }

            @Override
            public String toString() {
                return "Exact(" + testValue + ")";
            }
        }

        private static final class PrefixPredicate implements Predicate<String> {
            private final String testValue;

            private PrefixPredicate(String testValue) {
                this.testValue = testValue;
            }

            @Override
            public boolean test(String value) {
                return value.startsWith(testValue);
            }

            @Override
            public String toString() {
                return "Prefix(" + testValue + ")";
            }
        }

        private static final class SuffixPredicate implements Predicate<String> {
            private final String testValue;

            private SuffixPredicate(String testValue) {
                this.testValue = testValue;
            }

            @Override
            public boolean test(String value) {
                return value.endsWith(testValue);
            }

            @Override
            public String toString() {
                return "Suffix(" + testValue + ")";
            }
        }

        private static final class PatternPredicate implements Predicate<String> {
            private final Pattern testPattern;

            private PatternPredicate(Pattern testPattern) {
                this.testPattern = testPattern;
            }

            @Override
            public boolean test(String value) {
                return this.testPattern.matcher(value).matches();
            }

            @Override
            public String toString() {
                return "Pattern(" + testPattern.pattern() + ")";
            }
        }
    }
}
