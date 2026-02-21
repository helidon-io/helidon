/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.observe.metrics;

import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.common.uri.UriPath;
import io.helidon.http.Method;

class AutoHttpMetricsConfigSupport {

    private static final List<AutoHttpMetricsPathConfig> MEASUREMENT_DISABLED_HELIDON_ENDPOINTS = List.of(
            disabled("/metrics/*"),
            disabled("/observe/*"),
            disabled("/health/*"),
            disabled("/openapi")
            );

    private AutoHttpMetricsConfigSupport() {
    }

    /**
     * Creates a metrics path config that disables automatic metrics collection for the specified exact path.
     *
     * @param path exact path to exclude
     * @return metrics path config disabling collection for the path
     */
    static AutoHttpMetricsPathConfig disabled(String path) {
        return AutoHttpMetricsPathConfig.builder()
                .enabled(false)
                .path(path)
                .build();
    }

    static class CustomMethods {

        /**
         * Decides whether the specified HTTP method and path should be measured.
         * <p>
         * Given a request, we search for a path config that matches the request's path and HTTP method.
         * We immediately return the {@code enabled} value for the first path config that the request matches.
         * <p>
         * If we find no match, we return true.
         * <p>
         * Helidon automatically prefixes the explicitly-configured path entries with implicit entries for Helidon-provided
         * endpoints with measurement disabled. Users can enable automatic metrics for such an endpoint
         * by adding explicit configuration for it with {@code enabled} set to {@code true}.
         *
         * @param config automatic metrics configuration
         * @param method HTTP method from the request
         * @param uriPath URI path from the request
         * @return whether the request should be measured, based on the configuration
         */
        @Prototype.PrototypeMethod
        static boolean isMeasured(AutoHttpMetricsConfig config, Method method, UriPath uriPath) {

            if (config.paths().isEmpty()) {
                return true;
            }

            for (AutoHttpMetricsPathConfig cfg : config.paths()) {
                if (cfg.matchesPath(uriPath) && cfg.matchesMethod(method)) {
                    return cfg.enabled();
                }
            }
            return true;
        }

        /**
         * Returns whether the configuration indicates if the user has opted to have specified attribute (tag) added to
         * meters for the specified meter name. Telemetry implementations can permit users to configure whether certain
         * attributes--typically ones that might be expensive to obtain--are to set on automatic meters.
         *
         * @param config {@link io.helidon.webserver.observe.metrics.AutoHttpMetricsConfig} to check for opt-in settings
         * @param meterName name of the meter to check for
         * @param attributeName name of the attribute to opt in
         * @return true if the attribute should be added to the meter; false otherwise
         */
        @Prototype.PrototypeMethod
        static boolean isOptedIn(AutoHttpMetricsConfig config, String meterName, String attributeName) {
            return config.optIn().stream()
                    .anyMatch(optIn -> optIn.equals(meterName) || optIn.equals(meterName + ":" + attributeName));
        }
    }

    static class BuilderDecorator implements Prototype.BuilderDecorator<AutoHttpMetricsConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(AutoHttpMetricsConfig.BuilderBase<?, ?> target) {
            target.paths().addAll(MEASUREMENT_DISABLED_HELIDON_ENDPOINTS);
        }
    }
}
