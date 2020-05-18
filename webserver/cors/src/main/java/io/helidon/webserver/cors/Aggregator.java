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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.ConfigValue;
import io.helidon.webserver.PathMatcher;
import io.helidon.webserver.cors.LogHelper.MatcherChecks;

/**
 * Collects CORS set-up information from various sources and looks up the relevant CORS information given a request's path and
 * HTTP method.
 * <p>
 *    The caller builds the cross-config information over multiple invocations of the builder methods. The behavior is that
 *    of a {@link List}: when <em>matching</em> against a request's path and method, the aggregator checks the path matchers
 *    <em>in the order they were added</em> to the aggregator, whether by {@link Builder#mappedConfig} or
 *    {@link Builder#addCrossOrigin} or the {@link CorsSetter} methods.
 * </p>
 * <p>
 *     The {@code CorsSetter} methods affect a distinct "pathless" entry. Those methods have no explicit path, so we record
 *     their settings in an entry with path expression {@value #PATHLESS_KEY} which matches everything. The first time the
 *     caller invokes a {@code CorsSetter} method, the aggregator creates this distinct entry and adds it to the list, thus (as
 *     with any other entry) determining the order, relative to other entries, with which it will be checked.
 * </p>
 *
 */
class Aggregator {

    // Key value for the map corresponding to the cross-origin config managed by the {@link CorsSetter} methods
    static final String PATHLESS_KEY = "{+}";

    private static final Logger LOGGER = Logger.getLogger(Aggregator.class.getName());

    // Records paths and configs added via addCrossOriginConfig
    private final List<CrossOriginConfigMatchable> crossOriginConfigMatchables = new ArrayList<>();

    private boolean isEnabled = true;

    /**
     * Factory method.
     *
     * @return new CrossOriginConfigAggregator
     */
    static Aggregator create() {
        return builder().build();
    }

    static Builder builder() {
        return new Builder();
    }

    private Aggregator(Builder builder) {
        isEnabled = builder.isEnabled;
        crossOriginConfigMatchables.addAll(builder.crossOriginConfigMatchables);
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

    static class Builder implements io.helidon.common.Builder<Aggregator>, CorsSetter<Builder> {

        private final List<CrossOriginConfigMatchable> crossOriginConfigMatchables = new ArrayList<>();
        private boolean isEnabled = true;
        private boolean requestDefaultBehaviorIfNone = false;
        private BuildableCrossOriginConfigMatchable pathlessCrossOriginConfigMatchable;

        @Override
        public Aggregator build() {
            if (pathlessCrossOriginConfigMatchable != null) {
                addPathlessCrossOrigin(pathlessCrossOriginConfigMatchable.get());
            }
            if (requestDefaultBehaviorIfNone && crossOriginConfigMatchables.isEmpty()) {
                addPathlessCrossOrigin(CrossOriginConfig.builder().build());
            }
            return new Aggregator(this);
        }

        Builder config(Config config) {
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
        Builder mappedConfig(Config config) {

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
         * Adds cross origin information associated with a given pathPattern.
         *
         * @param pathPattern the pathPattern to which the cross origin information applies
         * @param crossOrigin the cross origin information
         * @return updated builder
         */
        Builder addCrossOrigin(String pathPattern, CrossOriginConfig crossOrigin) {
            crossOriginConfigMatchables.add(new FixedCrossOriginConfigMatchable(pathPattern, crossOrigin));
            return this;
        }

        Builder requestDefaultBehaviorIfNone() {
            requestDefaultBehaviorIfNone = true;
            return this;
        }

        /**
         * Adds cross origin information associated with the default path expression.
         *
         * @param crossOrigin the cross origin information
         * @return updated builder
         */
        Builder addPathlessCrossOrigin(CrossOriginConfig crossOrigin) {
            crossOriginConfigMatchables.add(new FixedCrossOriginConfigMatchable(PATHLESS_KEY, crossOrigin));
            return this;
        }

        @Override
        public Builder enabled(boolean value) {
            isEnabled = value;
            return this;
        }

        @Override
        public Builder allowOrigins(String... origins) {
            pathlessCrossOriginConfigBuilder().allowOrigins(origins);
            return this;
        }

        @Override
        public Builder allowHeaders(String... allowHeaders) {
            pathlessCrossOriginConfigBuilder().allowHeaders(allowHeaders);
            return this;
        }

        @Override
        public Builder exposeHeaders(String... exposeHeaders) {
            pathlessCrossOriginConfigBuilder().exposeHeaders(exposeHeaders);
            return this;
        }

        @Override
        public Builder allowMethods(String... allowMethods) {
            pathlessCrossOriginConfigBuilder().allowMethods(allowMethods);
            return this;
        }

        @Override
        public Builder allowCredentials(boolean allowCredentials) {
            pathlessCrossOriginConfigBuilder().allowCredentials(allowCredentials);
            return this;
        }

        @Override
        public Builder maxAgeSeconds(long maxAgeSeconds) {
            pathlessCrossOriginConfigBuilder().maxAgeSeconds(maxAgeSeconds);
            return this;
        }

        /**
         * Retrieves the {@code CrossOriginConfig.Builder} associated with the "pathless" config used by the methods defined by
         * {@code CorsSetter}.
         *
         * @return the builder, possibly newly created
         */
        private CrossOriginConfig.Builder pathlessCrossOriginConfigBuilder() {

            // Upon first use of a CorsSettable method, create the pathless matchable and add it to the matchables.
            if (pathlessCrossOriginConfigMatchable == null) {
                BuildableCrossOriginConfigMatchable newMatchable = new BuildableCrossOriginConfigMatchable(PATHLESS_KEY,
                        CrossOriginConfig.builder());
                pathlessCrossOriginConfigMatchable = newMatchable;
                crossOriginConfigMatchables.add(pathlessCrossOriginConfigMatchable);
            }

            return pathlessCrossOriginConfigMatchable.builder;
        }

    }

    /**
     * Looks for a matching CORS config entry for the specified path among the provided CORS configuration information, returning
     * an {@code Optional} of the matching {@code CrossOrigin} instance for the path, if any.
     *
     * @param path the unnormalized request path to check
     * @param secondaryLookup Supplier for CrossOrigin used if none found in config
     * @return Optional<CrossOrigin> for the matching config, or an empty Optional if none matched
     */
    Optional<CrossOriginConfig> lookupCrossOrigin(String path, String method,
            Supplier<Optional<CrossOriginConfig>> secondaryLookup) {

        Optional<CrossOriginConfig> result = findFirst(crossOriginConfigMatchables, path, method)
                .or(secondaryLookup);

        return result;
    }

    /**
     * Given a map from path expressions to matchables, finds the first map entry with a path matcher that accepts the provided
     * path and is enabled.
     *
     * @param matchables map from pathPatterns to matchables
     * @param normalizedPath unnormalized path (from the request) to be matched
     * @return Optional of the CrossOriginConfig
     */
    private static Optional<CrossOriginConfig> findFirst(List<CrossOriginConfigMatchable> matchables, String normalizedPath,
            String method) {
        MatcherChecks<CrossOriginConfigMatchable> checks = new MatcherChecks<>(LOGGER, CrossOriginConfigMatchable::get);
        Optional<CrossOriginConfig> result = matchables.stream()
                .peek(checks::put)
                .filter(matchable -> matchable.matches(normalizedPath, method))
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
     * A composite of a {@link CrossOriginConfig} with a {@link PathMatcher} that processes the path expression with which the
     * {@code CrossOriginConfig} was added.
     */
    private abstract static class CrossOriginConfigMatchable {
        private final PathMatcher matcher;

        CrossOriginConfigMatchable(String pathPattern) {
            this.matcher = PathMatcher.create(pathPattern);
        }

        boolean matches(String path, String method) {
            return matcher.match(path).matches() && get().matches(method);
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

        FixedCrossOriginConfigMatchable(String pathPattern, CrossOriginConfig crossOriginConfig) {
            super(pathPattern);
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

        BuildableCrossOriginConfigMatchable(String pathPattern, CrossOriginConfig.Builder builder) {
            super(pathPattern);
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
