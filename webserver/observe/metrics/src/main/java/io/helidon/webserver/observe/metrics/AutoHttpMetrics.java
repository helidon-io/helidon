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

import io.helidon.common.uri.UriPath;
import io.helidon.http.Method;

/**
 * Utility methods related to automatic HTTP metrics configuration.
 */
public class AutoHttpMetrics {

    private AutoHttpMetrics() {
    }

    /**
     * Determines if a request with the specified {@link io.helidon.http.Method} and {@link io.helidon.common.uri.UriPath}
     * should be measured, according to the configuration.
     *
     * @param config auto HTTP metrics configuration
     * @param method HTTP method to be checked
     * @param uriPath path to be checked
     * @return true if the configuration indicates the path/method combination should be measured; false otherwise
     */
    public static boolean isMeasured(AutoHttpMetricsConfig config, Method method, UriPath uriPath) {

        if (config.autoHttpMetricsPathConfigs().isEmpty()) {
            return true;
        }

        for (AutoHttpMetricsPathConfig cfg : config.autoHttpMetricsPathConfigs()) {
            if (matchesPath(cfg, uriPath) && matchesMethod(cfg, method)) {
                return cfg.enabled();
            }
        }
        return true;
    }

    static boolean matchesPath(AutoHttpMetricsPathConfig config, UriPath uriPath) {
        return config.pathMatcher().match(uriPath).accepted();
    }

    static boolean matchesMethod(AutoHttpMetricsPathConfig config, Method method) {
        return config.methodPredicate().test(method);
    }
}
