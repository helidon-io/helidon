/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.webserver.cors;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.webserver.PathMatcher;
import io.helidon.webserver.cors.LogHelper.MatcherChecks;

import static io.helidon.webserver.cors.CorsSupportHelper.normalize;

/**
 * Collects CORS set-up information from various sources and looks up the relevant CORS information given a request's path.
 * <p>
 *    The caller can build up the cross-config information over multiple invocations of the exposed methods. The behavior is that
 *    of a {@link LinkedHashMap}:
 *    <ul>
 *        <li>when <em>storing</em> cross-config information, the <em>latest</em> invocation that specifies the same path
 *        expression overwrites any preceding settings for the same path expression, and</li>
 *        <li>when <em>matching</em> against a request's path, the code checks the path matchers <em>in the order
 *        they were added</em> to the aggregator, whether by {@link #mappedConfig} or {@link #addCrossOrigin} or the {@link CorsSetter}
 *        methods.
 *    </ul>
 * </p>
 * <p>
 *     The {@code CorsSetter} methods affect the so-called "pathless" entry. Those methods have no explicit path, so we record
 *     their settings in an entry with path expression {@value #PATHLESS_KEY} which matches everything.
 * </p>
 * <p>
 *     If the developer uses the {@link #mappedConfig} or {@link #addCrossOrigin} methods <em>along with</em> the {@code CorsSetter}
 *     methods, the results are predictable but might be confusing. The {@code config} and {@code addCrossOrigin} methods
 *     <em>overwrite</em> any entry with the same path expression, whereas the {@code CorsSetter} methods <em>update</em> an existing
 *     entry with path {@value #PATHLESS_KEY}, creating one if needed. So, if the config or an {@code addCrossOrigin}
 *     invocation sets values for that same path expression then results can be surprising.
 *     path
 * </p>
 *
 */
class Aggregator implements CorsSetter<Aggregator> {

    // Key value for the map corresponding to the cross-origin config managed by the {@link CorsSetter} methods
    static final String PATHLESS_KEY = "{+}";

    private static final Logger LOGGER = Logger.getLogger(Aggregator.class.getName());

    // Records paths and configs added via addCrossOriginConfig
    private final Map<String, CrossOriginConfigMatchable> crossOriginConfigMatchables = new LinkedHashMap<>();

    private boolean isEnabled = true;

    /**
     * Factory method.
     *
     * @return new CrossOriginConfigAggregatpr
     */
    static Aggregator create() {
        return new Aggregator();
    }

    private Aggregator() {
    }

    /**
     * Reports whether the sources of CORS information have left CORS active or not. This is a combination of any explicit
     * setting of {@code enabled} with whether any {@code CrossOriginConfig} instances were added -- either explicitly or using
     * config. If not, then the aggregator will never find a match among the matchables so it is as good as inactive.
     *
     * @return if this aggregator will contribute to CORS processing
     */
    public boolean isActive() {
        return isEnabled && !crossOriginConfigMatchables.isEmpty();
    }

    Aggregator config(Config config) {
        if (config.exists()) {
            ConfigValue<CrossOriginConfig.Builder> configValue = config.as(CrossOriginConfig::builder);
            if (configValue.isPresent()) {
                CrossOriginConfig crossOriginConfig = configValue.get().build();
                addPathlessCrossOrigin(crossOriginConfig);
            }
        }
        return this;
    }

    /**
     * Add mapped cross-origin information from a {@link Config} node.
     *
     * @param config {@code Config} node containing mapped {@code CrossOriginConfig} data
     * @return updated builder
     */
    Aggregator mappedConfig(Config config) {

        if (config.exists()) {
            ConfigValue<MappedCrossOriginConfig.Builder> mappedConfigValue = config.as(MappedCrossOriginConfig::builder);
            if (mappedConfigValue.isPresent()) {
                MappedCrossOriginConfig mapped = mappedConfigValue.get().build();
                /*
                 * Merge the newly-provided config with what we've assembled so far. We do not merge the config for a given path;
                 * we add paths that are not already present and override paths that are there.
                 */
                AtomicBoolean foundCrossOrigin = new AtomicBoolean();
                mapped.forEach((k, v) -> {
                    addCrossOrigin(k, v);
                    foundCrossOrigin.set(true);
                });

                isEnabled = mapped.isEnabled();
                /*
                 * If the config just set enabled to true without specifying any cross-origin set-up, create a wildcarded
                 * default one.
                 */
                if (!foundCrossOrigin.get()) {
                    addPathlessCrossOrigin(CrossOriginConfig.builder().build());
                }
            }
        }
        return this;
    }

    /**
     * Adds cross origin information associated with a given pathExpr.
     *
     * @param pathExpr the pathExpr to which the cross origin information applies
     * @param crossOrigin the cross origin information
     * @return updated builder
     */
    Aggregator addCrossOrigin(String pathExpr, CrossOriginConfig crossOrigin) {
        crossOriginConfigMatchables.put(normalize(pathExpr), new FixedCrossOriginConfigMatchable(pathExpr, crossOrigin));
        return this;
    }

    /**
     * Adds cross origin information associated with the default path expression.
     *
     * @param crossOrigin the cross origin information
     * @return updated builder
     */
    Aggregator addPathlessCrossOrigin(CrossOriginConfig crossOrigin) {
        crossOriginConfigMatchables.put(PATHLESS_KEY, new FixedCrossOriginConfigMatchable(PATHLESS_KEY, crossOrigin));
        return this;
    }

    @Override
    public Aggregator enabled(boolean value) {
        isEnabled = value;
        return this;
    }

    @Override
    public Aggregator allowOrigins(String... origins) {
        pathlessCrossOriginConfigBuilder().allowOrigins(origins);
        return this;
    }

    @Override
    public Aggregator allowHeaders(String... allowHeaders) {
        pathlessCrossOriginConfigBuilder().allowHeaders(allowHeaders);
        return this;
    }

    @Override
    public Aggregator exposeHeaders(String... exposeHeaders) {
        pathlessCrossOriginConfigBuilder().exposeHeaders(exposeHeaders);
        return this;
    }

    @Override
    public Aggregator allowMethods(String... allowMethods) {
        pathlessCrossOriginConfigBuilder().allowMethods(allowMethods);
        return this;
    }

    @Override
    public Aggregator allowCredentials(boolean allowCredentials) {
        pathlessCrossOriginConfigBuilder().allowCredentials(allowCredentials);
        return this;
    }

    @Override
    public Aggregator maxAgeSeconds(long maxAgeSeconds) {
        pathlessCrossOriginConfigBuilder().maxAgeSeconds(maxAgeSeconds);
        return this;
    }

    /**
     * Looks for a matching CORS config entry for the specified path among the provided CORS configuration information, returning
     * an {@code Optional} of the matching {@code CrossOrigin} instance for the path, if any.
     *
     * @param path the unnormalized request path to check
     * @param secondaryLookup Supplier for CrossOrigin used if none found in config
     * @return Optional<CrossOrigin> for the matching config, or an empty Optional if none matched
     */
    Optional<CrossOriginConfig> lookupCrossOrigin(String path,
            Supplier<Optional<CrossOriginConfig>> secondaryLookup) {

        Optional<CrossOriginConfig> result = findFirst(crossOriginConfigMatchables, normalize(path))
                .or(secondaryLookup);

        return result;
    }

    /**
     * Given a map from path expressions to matchables, finds the first map entry with a path matcher that accepts the provided
     * path and is enabled.
     *
     * @param matchables map from pathExpressions to matchables
     * @param normalizedPath unnormalized path (from the request) to be matched
     * @return Optional of the CrossOriginConfig
     */
    private static Optional<CrossOriginConfig> findFirst(Map<String, CrossOriginConfigMatchable> matchables,
            String normalizedPath) {
        MatcherChecks<CrossOriginConfigMatchable> checks = new MatcherChecks<>(LOGGER, CrossOriginConfigMatchable::get);
        Optional<CrossOriginConfig> result = matchables.values().stream()
                .peek(checks::put)
                .filter(matchable -> matchable.matches(normalizedPath))
                .peek(checks::matched)
                .map(CrossOriginConfigMatchable::get)
                .filter(CrossOriginConfig::isEnabled)
                .peek(checks::enabled)
                .findFirst();

        checks.log();
        return result;
    }

    @Override
    public String toString() {
        return "Aggregator{"
                + "crossOriginConfigMatchables=" + crossOriginConfigMatchables
                + ", isActive=" + isActive()
                + '}';
    }

    /**
     * Retrieves the {@code CrossOriginConfig.Builder} associated with the "pathless" config.
     *
     * @return the builder, possibly newly created
     */
    private CrossOriginConfig.Builder pathlessCrossOriginConfigBuilder() {

        CrossOriginConfigMatchable matchable = crossOriginConfigMatchables.get(PATHLESS_KEY);
        CrossOriginConfig.Builder newBuilder;

        if (matchable != null) {
            if (matchable instanceof BuildableCrossOriginConfigMatchable) {
                return ((BuildableCrossOriginConfigMatchable) matchable).builder;
            } else {
                // Convert the existing entry that has a fixed cross-origin config to a pre-initialized builder.
                newBuilder = CrossOriginConfig.builder(matchable.get());
            }
        } else {
            // No existing entry.
            newBuilder = CrossOriginConfig.builder();
        }
        crossOriginConfigMatchables.put(PATHLESS_KEY, new BuildableCrossOriginConfigMatchable(PATHLESS_KEY, newBuilder));

        return newBuilder;
    }

    /**
     * A composite of a {@link CrossOriginConfig} with a {@link PathMatcher} that processes the path expression with which the
     * {@code CrossOriginConfig} was added.
     */
    private abstract static class CrossOriginConfigMatchable {
        private final PathMatcher matcher;

        CrossOriginConfigMatchable(String pathExpr) {
            this.matcher = PathMatcher.create(pathExpr);
        }

        boolean matches(String unnormalizedPath) {
            return matcher.match(unnormalizedPath).matches();
        }

        PathMatcher matcher() {
            return matcher;
        }

        abstract CrossOriginConfig get();
    }

    /**
     * Based on a fixed {@code CrossOriginConfig} object.
     */
    private static class FixedCrossOriginConfigMatchable extends CrossOriginConfigMatchable {
        private final CrossOriginConfig crossOriginConfig;

        FixedCrossOriginConfigMatchable(String pathExpr, CrossOriginConfig crossOriginConfig) {
            super(pathExpr);
            this.crossOriginConfig = crossOriginConfig;
        }

        CrossOriginConfig get() {
            return crossOriginConfig;
        }

        @Override
        public String toString() {
            return String.format("FixedCrossOriginConfigMatchable{matcher=%s, crossOriginConfig=%s}",
                    matcher(), crossOriginConfig);
        }
    }

    /**
     * Based on a {@code CrossOriginConfig.Builder}, primarily for supporting the "pathless" entry that can be updated by
     * separate invocations of the {@link CorsSetter} methods.
     */
    private static class BuildableCrossOriginConfigMatchable extends CrossOriginConfigMatchable {

        private final CrossOriginConfig.Builder builder;
        private CrossOriginConfig config = null;

        BuildableCrossOriginConfigMatchable(String pathExpr, CrossOriginConfig.Builder builder) {
            super(pathExpr);
            this.builder = builder;
        }

        CrossOriginConfig get() {
            if (config == null) {
                config = builder.build();
            }
            return config;
        }

        @Override
        public String toString() {
            return String.format("BuildableCrossOriginConfigMatchable{matcher=%s, builder=%s, config=%s}",
                    matcher(), builder, config);
        }
    }
}
