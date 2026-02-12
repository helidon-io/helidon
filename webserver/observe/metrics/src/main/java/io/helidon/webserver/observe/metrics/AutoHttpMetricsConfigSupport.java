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

import java.util.ArrayList;
import java.util.List;

import io.helidon.builder.api.Prototype;
import io.helidon.common.uri.UriPath;
import io.helidon.http.Method;

class AutoHttpMetricsConfigSupport {

    private static final List<AutoHttpMetricsPathConfig> MEASUREMENT_DISABLED_HELIDON_ENDPOINTS = List.of(
            AutoHttpMetricsPathConfigBlueprint.disabled("/metrics"),
            AutoHttpMetricsPathConfigBlueprint.disabled("/observe/metrics"),
            AutoHttpMetricsPathConfigBlueprint.disabled("/health"),
            AutoHttpMetricsPathConfigBlueprint.disabled("/observe/health"),
            AutoHttpMetricsPathConfigBlueprint.disabled("/openapi"),
            AutoHttpMetricsPathConfigBlueprint.disabled("/observe/openapi")
            );

    private AutoHttpMetricsConfigSupport() {
    }

    static class CustomMethods {

        /**
         * Decides whether the specified HTTP method and path should be measured.
         * <p>
         * Given a request (actually, its method and path pair), Helidon searches for a matching config element
         * based on path matching. Each time Helidon finds a path match, it then checks the HTTP methods associated with
         * that entry. If the request's method appears among the config entry's methods(or if there are no methods explicitly
         * configured for that path element), then Helidon considers the request a match to that entry. Helidon then saves
         * that entry's {@code enabled} settings as the latest result for the request and goes on to check the next
         * configured path entry.
         * <p>
         * A given request might match any number of entries, and the last match wins.
         * <p>
         * If a request's path matches no configured entry then the request is measured, subject to the next note.
         * <p>
         * Helidon automatically prefixes the explicitly-configured path entries with implicit entries for Helidon-provided
         * endpoints with measurement disabled. Users can enable automatic metrics for sucb an endpoint
         * by adding explicit configuration for it with {@code enabled} set to {@code true}.
         *
         * @param config automatic metrics configuration
         * @param method HTTP method from the request
         * @param uriPath URI path from the request
         * @return whether the request should be measured, based on the configuration
         */
        @Prototype.PrototypeMethod
        static boolean isMeasured(AutoHttpMetricsConfig config, Method method, UriPath uriPath) {

            if (config.effectivePathConfigs().isEmpty()) {
                return true;
            }

            boolean latestResult = true;

            for (AutoHttpMetricsPathConfig cfg : config.effectivePathConfigs()) {
                if (cfg.matchesPath(uriPath)) {
                    latestResult = cfg.matchesMethod(method) == cfg.enabled();
                }
            }
            return latestResult;
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

            var fullList = new ArrayList<>(target.autoHttpMetricsPathConfigs());
            if (target.useDefaultRules()) {
                fullList.addAll(MEASUREMENT_DISABLED_HELIDON_ENDPOINTS);
            }
            target.effectivePathConfigs(fullList);
        }
    }
}
