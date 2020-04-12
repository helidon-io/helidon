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
import java.util.function.Supplier;

import io.helidon.config.Config;
import io.helidon.webserver.PathMatcher;

import static io.helidon.webserver.cors.CrossOriginHelper.normalize;

/**
 * <em>Not for developer use.</em> Collects CORS set-up information from various sources and looks up the relevant CORS
 * information given a request's path.
 */
class CrossOriginConfigAggregator implements Setter<CrossOriginConfigAggregator> {

    // Records paths and configs added via addCrossOriginConfig
    private final Map<String, CrossOriginConfigMatchable> crossOriginConfigMatchables = new LinkedHashMap<>();

    // Records the merged paths and configs added via the config method
    private final Map<String, CrossOriginConfigMatchable> crossOriginConfigsAssembledFromConfigs = new LinkedHashMap<>();

    // Accumulates config via the setter methods from CrossOriginConfig
    private Optional<CrossOriginConfig.Builder> crossOriginConfigBuilderOpt = Optional.empty();

    // To be enabled, there must be a "cors" config node.
    private Optional<Boolean> isEnabledFromConfig = Optional.empty();

    private boolean isEnabledFromAPI = true;

    /**
     * Factory method.
     *
     * @return new CrossOriginConfigAggregatpr
     */
    static CrossOriginConfigAggregator create() {
        return new CrossOriginConfigAggregator();
    }

    private CrossOriginConfigAggregator() {
    }

    /**
     * Reports whether the sources of CORS information have left CORS enabled or not.
     *
     * @return if CORS processing should be done
     */
    public boolean isEnabled() {
        return isEnabledFromConfig.orElse(isEnabledFromAPI);
    }

    /**
     * Add cross-origin information from a {@link Config} node.
     *
     * @param config {@code Config} node containing
     * @return updated builder
     */
    public CrossOriginConfigAggregator config(Config config) {
        /*
         * Merge the newly-provided config with what we've assembled so far. We do not merge the config for a given path;
         * we add paths that are not already present and override paths that are there.
         */
        if (config.exists()) {
            Config isEnabledNode = config.get("enabled");
            if (isEnabledNode.exists()) {
                // Last config setting wins.
                isEnabledFromConfig = Optional.of(isEnabledNode.asBoolean().get());
            } else {
                // If config exists but enabled is missing, default enabled is true.
                isEnabledFromConfig = Optional.of(Boolean.TRUE);
            }
            Config pathsNode = config.get(CrossOriginConfig.CORS_PATHS_CONFIG_KEY);
            if (pathsNode.exists()) {
                pathsNode.as(new CrossOriginConfig.CrossOriginConfigMapper())
                        .get()
                        .entrySet()
                        .stream()
                        .forEach(entry -> crossOriginConfigsAssembledFromConfigs.put(entry.getKey(),
                                new CrossOriginConfigMatchable(entry.getKey(), entry.getValue())));

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
    public CrossOriginConfigAggregator addCrossOrigin(String pathExpr, CrossOriginConfig crossOrigin) {
        crossOriginConfigMatchables.put(normalize(pathExpr), new CrossOriginConfigMatchable(pathExpr, crossOrigin));
        return this;
    }

    /**
     * Sets whether the app wants to enable CORS.
     *
     * @param value whether CORS should be enabled
     * @return updated builder
     */
    public CrossOriginConfigAggregator enabled(boolean value) {
        isEnabledFromAPI = value;
        return this;
    }

    @Override
    public CrossOriginConfigAggregator allowOrigins(String... origins) {
        crossOriginConfigBuilder().allowOrigins(origins);
        return this;
    }

    @Override
    public CrossOriginConfigAggregator allowHeaders(String... allowHeaders) {
        crossOriginConfigBuilder().allowHeaders(allowHeaders);
        return this;
    }

    @Override
    public CrossOriginConfigAggregator exposeHeaders(String... exposeHeaders) {
        crossOriginConfigBuilder().exposeHeaders(exposeHeaders);
        return this;
    }

    @Override
    public CrossOriginConfigAggregator allowMethods(String... allowMethods) {
        crossOriginConfigBuilder().allowMethods(allowMethods);
        return this;
    }

    @Override
    public CrossOriginConfigAggregator allowCredentials(boolean allowCredentials) {
        crossOriginConfigBuilder().allowCredentials(allowCredentials);
        return this;
    }

    @Override
    public CrossOriginConfigAggregator maxAge(long maxAge) {
        crossOriginConfigBuilder().maxAge(maxAge);
        return this;
    }

    /**
     * Looks for a matching CORS config entry for the specified path among the provided CORS configuration information, returning
     * an {@code Optional} of the matching {@code CrossOrigin} instance for the path, if any.
     *
     * @param path the possibly unnormalized request path to check
     * @param secondaryLookup Supplier for CrossOrigin used if none found in config
     * @return Optional<CrossOrigin> for the matching config, or an empty Optional if none matched
     */
    Optional<CrossOriginConfig> lookupCrossOrigin(String path, Supplier<Optional<CrossOriginConfig>> secondaryLookup) {

        Optional<CrossOriginConfig> result = Optional.empty();
        String normalizedPath = normalize(path);

        result = findFirst(crossOriginConfigsAssembledFromConfigs, normalizedPath)
                .or(() -> crossOriginConfigBuilderOpt
                            .map(CrossOriginConfig.Builder::build))
                .or(() -> findFirst(crossOriginConfigMatchables, normalizedPath))
                .or(secondaryLookup);

        return result;


    }

    /**
     * Given a map from path expressions to matchables, finds the first map entry with a path matcher that accepts the provided
     * path.
     *
     * @param matchables map from pathExpressions to matchables
     * @param normalizedPath normalized path (from the request) to be matched
     * @return Optional of the CrossOriginConfig
     */
    private static Optional<CrossOriginConfig> findFirst(Map<String, CrossOriginConfigMatchable> matchables,
            String normalizedPath) {
        return matchables.values().stream()
                .filter(matchable -> matchable.matches(normalizedPath))
                .map(CrossOriginConfigMatchable::get)
                .findFirst();

    }

    @Override
    public String toString() {
        return "CrossOriginConfigAggregator{"
                + "crossOriginConfigsAssembledFromConfigs=" + crossOriginConfigsAssembledFromConfigs
                + ", crossOriginConfigBuilder=" + crossOriginConfigBuilderOpt.map(CrossOriginConfig.Builder::toString).orElse(
                        "-empty-")
                + ", isEnabledFromConfig=" + isEnabledFromConfig
                + ", isEnabledFromAPI=" + isEnabledFromAPI
                + '}';
    }

    private CrossOriginConfig.Builder crossOriginConfigBuilder() {
        if (crossOriginConfigBuilderOpt.isEmpty()) {
            crossOriginConfigBuilderOpt = Optional.of(CrossOriginConfig.builder());
        }
        return crossOriginConfigBuilderOpt.get();
    }

    /**
     * A composite of a {@code CrossOriginConfig} with a {@link PathMatcher} that processes the path expression with which the
     * {@code CrossOriginConfig} was added.
     */
    private static class CrossOriginConfigMatchable {
        private final CrossOriginConfig crossOriginConfig;
        private final PathMatcher matcher;

        CrossOriginConfigMatchable(String pathExpr, CrossOriginConfig crossOriginConfig) {
            this.crossOriginConfig = crossOriginConfig;
            matcher = PathMatcher.create(pathExpr);
        }

        boolean matches(String path) {
            return matcher.match(path).matches();
        }

        CrossOriginConfig get() {
            return crossOriginConfig;
        }
    }

}
