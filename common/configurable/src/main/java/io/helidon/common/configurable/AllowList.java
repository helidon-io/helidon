/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.config.Config;

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
public class AllowList implements Predicate<String>, RuntimeType.Api<AllowListConfig> {
    private static final System.Logger LOGGER = System.getLogger(AllowList.class.getName());
    private static final String ALLOWED_MATCHED_LOG_FORMAT = "Value '%s' is allowed by %s";
    private static final String DENIED_MATCHED_LOG_FORMAT = " but is denied by %s";

    private final List<Predicate<String>> allowedPredicates = new ArrayList<>();
    private final List<Predicate<String>> deniedPredicates = new ArrayList<>();

    private final AllowListConfig config;

    AllowList(AllowListConfig config) {
        this.config = config;

        allowedPredicates.addAll(config.allowedPredicates());
        deniedPredicates.addAll(config.deniedPredicates());

        config.allowed().forEach(it -> allowedPredicates.add(new ExactPredicate(it)));
        config.denied().forEach(it -> deniedPredicates.add(new ExactPredicate(it)));

        config.allowedPrefixes().forEach(it -> allowedPredicates.add(new PrefixPredicate(it)));
        config.deniedPrefixes().forEach(it -> deniedPredicates.add(new PrefixPredicate(it)));

        config.allowedSuffixes().forEach(it -> allowedPredicates.add(new SuffixPredicate(it)));
        config.deniedSuffixes().forEach(it -> deniedPredicates.add(new SuffixPredicate(it)));

        config.allowedPatterns().forEach(it -> allowedPredicates.add(new PatternPredicate(it)));
        config.deniedPatterns().forEach(it -> deniedPredicates.add(new PatternPredicate(it)));

        if (config.allowAll()) {
            if (!allowedPredicates.isEmpty()) {
                LOGGER.log(Level.INFO, getClass().getSimpleName()
                        + " allowAll=true overrides the other, more specific, allow predicates");
                allowedPredicates.clear();
            }
            // just allow all
            allowedPredicates.add(new AllowAllPredicate());
        }
    }

    /**
     * Create a fluent API builder to configure an instance.
     *
     * @return a new builder
     */
    public static AllowListConfig.Builder builder() {
        return AllowListConfig.builder();
    }

    /**
     * Create {@code AllowList} from configurtion.
     *
     * @param config configuration
     * @return a new configured {@code AllowList}
     */
    public static AllowList create(Config config) {
        return builder().config(config).build();
    }

    /**
     * Create a new allow list based on its configuration.
     *
     * @param config configuration
     * @return a new allow list
     */
    public static AllowList create(AllowListConfig config) {
        return new AllowList(config);
    }

    /**
     * Create a new allow list customizing its configuration.
     *
     * @param consumer configuration consumer
     * @return a new allow list
     */
    public static AllowList create(java.util.function.Consumer<AllowListConfig.Builder> consumer) {
        var builder = AllowListConfig.builder();
        consumer.accept(builder);
        return builder.build();
    }

    @Override
    public AllowListConfig prototype() {
        return config;
    }

    /**
     * Test whether a value can be permitted.
     *
     * @param value value to test against
     * @return {@code true} if the value is allowed, {@code false} if it is not allowed or it is explicitly denied
     */
    @Override
    public boolean test(String value) {
        for (Predicate<String> allowedPredicate : allowedPredicates) {
            if (allowedPredicate.test(value)) {
                // value is allowed, let's check it is not explicitly denied
                Predicate<String> deniedPredicate = testNotDenied(value);
                if (deniedPredicate == null) {
                    if (LOGGER.isLoggable(Level.DEBUG)) {
                        LOGGER.log(Level.DEBUG, String.format(ALLOWED_MATCHED_LOG_FORMAT, value, allowedPredicate));
                    }
                    return true;
                } else {
                    if (LOGGER.isLoggable(Level.DEBUG)) {
                        LOGGER.log(Level.DEBUG, String.format(ALLOWED_MATCHED_LOG_FORMAT + DENIED_MATCHED_LOG_FORMAT,
                                                              value,
                                                              allowedPredicate,
                                                              deniedPredicate));
                    }
                    return false;
                }
            }
        }

        // no allowed predicate, deny
        if (LOGGER.isLoggable(Level.DEBUG)) {
            LOGGER.log(Level.DEBUG, "Denying value '" + value + "'; no matching allow predicates are defined");
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
