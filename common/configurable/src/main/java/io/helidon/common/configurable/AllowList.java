/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.common.configurable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * {@code AllowList} defines a list of allowed and/or denied matches and tests if a particular value conforms to
 * the conditions.
 * <p>
 * The algorithm of testing that a value is allowed:
 * <ol>
 * <li>Iterate through all allowed patterns, if none matches, value is not permitted</li>
 * <li>Iterate through all denied patterns, if any matches, value is not permitted</li>
 * <li>Value is permitted</li>
 * </ol>
 */
public interface AllowList extends Predicate<String> {

    /**
     * Create a fluent API builder to configure an instance.
     *
     * @return a new builder
     */
    static Builder builder() {
        return new Builder();
    }

    /**
     * Create {@code AllowList} from configurtion.
     *
     * @param config configuration
     * @return a new configured {@code AllowList}
     */
    static AllowList create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Test whether a value can be permitted.
     *
     * @param value value to test against
     * @return {@code true} if the value is allowed, {@code false} if it is not allowed or it is explicitly denied
     */
    @Override
    boolean test(String value);

    /**
     * Fluent API builder for {@code AllowList}.
     */
    @Configured
    final class Builder implements io.helidon.common.Builder<Builder, AllowList> {

        private static final Logger LOGGER = Logger.getLogger(AllowList.class.getName());

        private final List<Predicate<String>> allowedPredicates = new ArrayList<>();
        private final List<Predicate<String>> deniedPredicates = new ArrayList<>();

        private boolean allowAllSetting = false;

        private Builder() {
        }

        @Override
        public AllowList build() {
            checkAndAddAllowAllPredicate();
            return new AllowListImpl(this);
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
            allowed.get("all").asBoolean().ifPresent(this::allowAll);

            Config denied = config.get("deny");
            denied.get("exact").asList(String.class).ifPresent(this::denied);
            denied.get("prefix").asList(String.class).ifPresent(this::deniedPrefixes);
            denied.get("suffix").asList(String.class).ifPresent(this::deniedSuffixes);
            denied.get("pattern").asList(Pattern.class).ifPresent(this::deniedPatterns);

            return this;
        }

        /**
         * Allows all strings to match (subject to "deny" conditions). An {@code allow.all} setting of {@code false} does
         * not deny all strings but rather represents the absence of a universal match, meaning that other allow and deny settings
         * determine the matching outcomes.
         *
         * @param value whether to allow all strings to match (subject to "deny" conditions)
         * @return updated builder
         */
        @ConfiguredOption(key = "allow.all", type = Boolean.class, value = "false")
        public Builder allowAll(boolean value) {
            allowAllSetting = value;
            return this;
        }

        /**
         * Adds a list of exact strings any of which, if matched, allows matching for a candidate string.
         *
         * @param exacts which allow matching
         * @return updated builder
         */
        @ConfiguredOption(key = "allow.exact")
        public Builder allowed(List<String> exacts) {
            exacts.forEach(this::addAllowed);
            return this;
        }

        /**
         * Adds a list of prefixes any of which, if matched, allows matching for a candidate string.
         *
         * @param prefixes which allow matching
         * @return updated builder
         */
        @ConfiguredOption(key = "allow.prefix")
        public Builder allowedPrefixes(List<String> prefixes) {
            prefixes.forEach(this::addAllowedPrefix);
            return this;
        }

        /**
         * Adds a list of suffixes any of which, if matched, allows matching for a candidate string.
         *
         * @param suffixes which allow matching
         * @return updated builder
         */
        @ConfiguredOption(key = "allow.suffix")
        public Builder allowedSuffixes(List<String> suffixes) {
            suffixes.forEach(this::addAllowedSuffix);
            return this;
        }

        /**
         * Adds a list of {@link Pattern} any of which, if matched, allows matching for a candidate string.
         *
         * @param patterns which allow matching
         * @return updated builder
         */
        @ConfiguredOption(key = "allow.pattern")
        public Builder allowedPatterns(List<Pattern> patterns) {
            patterns.forEach(this::addAllowedPattern);
            return this;
        }

        /**
         * Adds an exact string which, if matched, allows matching for a candidate string.
         *
         * @param exact which allows matching
         * @return updated builder
         */
        public Builder addAllowed(String exact) {
            return addAllowed(new ExactPredicate(exact));
        }

        /**
         * Adds a {@link Pattern} which, if matched, allows matching for a candidate string.
         *
         * @param pattern which allows matching
         * @return updated builder
         */
        public Builder addAllowedPattern(Pattern pattern) {
            return addAllowed(new PatternPredicate(pattern));
        }

        /**
         * Adds a prefix which, if matched, allows matching for a candidate string.
         *
         * @param prefix which allows matching
         * @return updated builder
         */
        public Builder addAllowedPrefix(String prefix) {
            return addAllowed(new PrefixPredicate(prefix));
        }

        /**
         * Adds a suffix which, if matched, allows matching for a candidate string.
         *
         * @param suffix which allows matching
         * @return updated builder
         */
        public Builder addAllowedSuffix(String suffix) {
            return addAllowed(new SuffixPredicate(suffix));
        }

        /**
         * Adds a predicate which, if matched, allows matching for a candidate string.
         *
         * @param predicate which allows matching
         * @return updated builder
         */
        public Builder addAllowed(Predicate<String> predicate) {
            this.allowedPredicates.add(predicate);
            return this;
        }

        /**
         * Adds exact strings a match by any of which denies matching for a candidate string.
         *
         * @param exacts which deny matching
         * @return updated builder
         */
        @ConfiguredOption(key = "deny.exact")
        public Builder denied(List<String> exacts) {
            exacts.forEach(this::addDenied);
            return this;
        }

        /**
         * Adds prefixes a match by any of which denies matching for a candidate string.
         *
         * @param prefixes which deny matching
         * @return updated builder
         */
        @ConfiguredOption(key = "deny.prefix")
        public Builder deniedPrefixes(List<String> prefixes) {
            prefixes.forEach(this::addDeniedPrefix);
            return this;
        }

        /**
         * Adds suffixes a match by any of which denies matching for a candidate string.
         *
         * @param suffixes which deny matching
         * @return updated builder
         */
        @ConfiguredOption(key = "deny.suffix")
        public Builder deniedSuffixes(List<String> suffixes) {
            suffixes.forEach(this::addDeniedSuffix);
            return this;
        }

        /**
         * Adds patterns a match by any of which denies matching for a candidate string.
         *
         * @param patterns which deny matching
         * @return updated builder
         */
        @ConfiguredOption(key = "deny.pattern")
        public Builder deniedPatterns(List<Pattern> patterns) {
            patterns.forEach(this::addDeniedPattern);
            return this;
        }

        /**
         * Adds an exact string which, if matched, denies matching for a candidate string.
         *
         * @param exact match to deny matching
         * @return updated builder
         */
        public Builder addDenied(String exact) {
            return addDenied(new ExactPredicate(exact));
        }

        /**
         * Adds a {@link Pattern} which, if matched, denies matching for a candidate string.
         *
         * @param pattern to deny matching
         * @return updated builder
         */
        public Builder addDeniedPattern(Pattern pattern) {
            return addDenied(new PatternPredicate(pattern));
        }

        /**
         * Adds a prefix which, if matched, denies matching for a candidate string.
         *
         * @param prefix to deny matching
         * @return updated builder
         */
        public Builder addDeniedPrefix(String prefix) {
            return addDenied(new PrefixPredicate(prefix));
        }

        /**
         * Adds a suffix which, if matched, denies matching for a candidate string.
         *
         * @param suffix to deny matching
         * @return updated builder
         */
        public Builder addDeniedSuffix(String suffix) {
            return addDenied(new SuffixPredicate(suffix));
        }

        /**
         * Adds a predicate which, if matched, denies matching for a candidate string.
         *
         * @param predicate to deny matching
         * @return updated builder
         */
        public Builder addDenied(Predicate<String> predicate) {
            this.deniedPredicates.add(predicate);
            return this;
        }

        private void checkAndAddAllowAllPredicate() {
            // Specifying allowAll(true) with any other allow is odd. Accept it but warn the user.
            if (allowAllSetting) {
                if (!allowedPredicates.isEmpty()) {
                    LOGGER.log(Level.INFO, getClass().getSimpleName()
                            + " allowAll=true overrides the other, more specific, allow predicates");
                }
                allowedPredicates.add(new AllowAllPredicate());
            }
        }

        private static final class AllowListImpl implements AllowList {

            private static final String ALLOWED_MATCHED_LOG_FORMAT = "Value '%s' is allowed by %s";
            private static final String DENIED_MATCHED_LOG_FORMAT = " but is denied by %s";

            private final List<Predicate<String>> allowedPredicates;
            private final List<Predicate<String>> deniedPredicates;

            private AllowListImpl(Builder builder) {
                this.allowedPredicates = List.copyOf(builder.allowedPredicates);
                this.deniedPredicates = List.copyOf(builder.deniedPredicates);
            }

            @Override
            public boolean test(String value) {
                for (Predicate<String> allowedPredicate : allowedPredicates) {
                    if (allowedPredicate.test(value)) {
                        // value is allowed, let's check it is not explicitly denied
                        Predicate<String> deniedPredicate = testNotDenied(value);
                        if (deniedPredicate == null) {
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.log(Level.FINE, String.format(ALLOWED_MATCHED_LOG_FORMAT, value, allowedPredicate));
                            }
                            return true;
                        } else {
                            if (LOGGER.isLoggable(Level.FINE)) {
                                LOGGER.log(Level.FINE, String.format(ALLOWED_MATCHED_LOG_FORMAT + DENIED_MATCHED_LOG_FORMAT,
                                                                     value,
                                                                     allowedPredicate,
                                                                     deniedPredicate));
                            }
                            return false;
                        }
                    }
                }

                // no allowed predicate, deny
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "Denying value '" + value + "'; no matching allow predicates are defined");
                }
                return false;
            }

            @Override
            public String toString() {
                return "Allowed: " + allowedPredicates + ", Denied: " + deniedPredicates;
            }

            private Predicate<String> testNotDenied(String value) {
                for (Predicate<String> deniedPredicate : deniedPredicates) {
                    if (deniedPredicate.test(value)) {
                        return deniedPredicate;
                    }
                }
                return null;
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

            /**
             * Creates a new {@code ExactPredicate} from the provided exact-match string.
             *
             * @param exact match string
             */
            ExactPredicate(String exact) {
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
