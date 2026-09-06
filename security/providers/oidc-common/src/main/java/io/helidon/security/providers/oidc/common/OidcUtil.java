/*
 * Copyright (c) 2021, 2026 Oracle and/or its affiliates.
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

package io.helidon.security.providers.oidc.common;

import java.lang.System.Logger.Level;
import java.net.URI;

import io.helidon.common.Errors;
import io.helidon.faulttolerance.CircuitBreaker;
import io.helidon.faulttolerance.CircuitBreakerConfig;
import io.helidon.faulttolerance.ResilientValue;
import io.helidon.faulttolerance.Retry;
import io.helidon.faulttolerance.RetryConfig;

final class OidcUtil {
    private static final System.Logger LOGGER = System.getLogger(OidcUtil.class.getName());

    private OidcUtil() {
    }

    static String fixServerType(String serverType) {
        if (serverType != null) {
            // explicit server type
            if (!"idcs".equals(serverType) && !OidcConfig.Builder.DEFAULT_SERVER_TYPE.equals(serverType)) {
                LOGGER.log(Level.WARNING, "OIDC server-type is configured to " + serverType + ", currently only \"idcs\", and"
                                       + " \"" + OidcConfig.Builder.DEFAULT_SERVER_TYPE + "\" are supported");
                return OidcConfig.Builder.DEFAULT_SERVER_TYPE;
            }
        } else {
            return OidcConfig.Builder.DEFAULT_SERVER_TYPE;
        }
        return serverType;
    }

    static void validateExists(Errors.Collector collector, Object value, String name, String configKey) {
        // validate
        if (value == null) {
            collector.fatal(name + " must be configured (\"" + configKey + "\" key in config)");
        }
    }

    static String resolveMetaKey(String metaKey, String serverType, URI identityUri) {
        if ("idcs".equals(serverType)
                && identityUri != null
                && identityUri.toString().contains(".secure.")) {
            return "secure_" + metaKey;
        }
        return metaKey;
    }

    static URI validateHttpEndpoint(String value, String description) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(description + " must be a valid URI", e);
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute()) {
            throw new IllegalArgumentException(description + " must be an absolute URI");
        }
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException(description + " must use HTTP or HTTPS");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException(description + " HTTP URI must include a host");
        }
        return uri;
    }

    static Retry jwkRetry(String description, TenantConfig tenantConfig) {
        return RetryConfig.builder(tenantConfig.jwkRetryConfig())
                .clearApplyOn()
                .addApplyOn(ResilientValue.UnavailableException.class)
                .clearSkipOn()
                .name(description + "-retry")
                .build();
    }

    static CircuitBreaker jwkCircuitBreaker(String description, TenantConfig tenantConfig) {
        return CircuitBreakerConfig.builder(tenantConfig.jwkCircuitBreakerConfig())
                .clearApplyOn()
                .addApplyOn(ResilientValue.UnavailableException.class)
                .clearSkipOn()
                .name(description + "-circuit-breaker")
                .build();
    }
}
