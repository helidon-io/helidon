/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

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
@Prototype.Blueprint
@Prototype.Configured
interface AllowListConfigBlueprint extends Prototype.Factory<AllowList> {
    /**
     * Allows all strings to match (subject to "deny" conditions). An {@code allow.all} setting of {@code false} does
     * not deny all strings but rather represents the absence of a universal match, meaning that other allow and deny settings
     * determine the matching outcomes.
     *
     * @return whether to allow all strings to match (subject to "deny" conditions)
     */
    @Option.Configured("allow.all")
    @Option.DefaultBoolean(false)
    boolean allowAll();

    /**
     * Exact strings to allow.
     *
     * @return exact strings to allow
     */
    @Option.Configured("allow.exact")
    @Option.Singular
    List<String> allowed();

    /**
     * Prefixes specifying strings to allow.
     *
     * @return prefixes which allow matching
     */
    @Option.Configured("allow.prefix")
    @Option.Singular("allowedPrefix")
    List<String> allowedPrefixes();

    /**
     * Suffixes specifying strings to allow.
     *
     * @return suffixes which allow matching
     */
    @Option.Configured("allow.suffix")
    @Option.Singular("allowedSuffix")
    List<String> allowedSuffixes();

    /**
     * {@link Pattern}s specifying strings to allow.
     *
     * @return patterns which allow matching
     */
    @Option.Configured("allow.pattern")
    @Option.Singular
    List<Pattern> allowedPatterns();

    /**
     * Exact strings to deny.
     *
     * @return exact strings to deny
     */
    @Option.Configured("deny.exact")
    @Option.Singular
    List<String> denied();

    /**
     * Prefixes specifying strings to deny.
     *
     * @return prefixes which deny matching
     */
    @Option.Configured("deny.prefix")
    @Option.Singular("deniedPrefix")
    List<String> deniedPrefixes();

    /**
     * Suffixes specifying strings to deny.
     *
     * @return suffixes which deny matching
     */
    @Option.Configured("deny.suffix")
    @Option.Singular("deniedSuffix")
    List<String> deniedSuffixes();

    /**
     * Patterns specifying strings to deny.
     *
     * @return patterns which deny matching
     */
    @Option.Configured("deny.pattern")
    @Option.Singular
    List<Pattern> deniedPatterns();

    /**
     * Allowed predicates.
     *
     * @return predicates to allow
     */
    @Option.Singular("allowed")
    List<Predicate<String>> allowedPredicates();

    /**
     * Deny predicates.
     *
     * @return predicates to deny
     */
    @Option.Singular("denied")
    List<Predicate<String>> deniedPredicates();
}
