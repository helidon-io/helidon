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

import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.webserver.Handler;

/**
 * Allows services (including Helidon built-in services) to register CORS support easily.
 */
public class CorsEnabledServiceHelper {

    /**
     * Conventional configuration key for CORS set-up; used for built-in services.
     */
    public static final String CORS_CONFIG_KEY = "cors";

    private static final Handler NO_OP_HANDLER = (req, res) -> req.next();

    private static final Logger LOGGER = Logger.getLogger(CorsEnabledServiceHelper.class.getName());

    private final String serviceName;
    private final Config corsConfig;

    private CorsEnabledServiceHelper(String serviceName, Optional<Config> optCORSConfig) {
        this(serviceName, optCORSConfig.orElse(null));
    }

    private CorsEnabledServiceHelper(String serviceName, Config corsConfig) {
        this.serviceName = serviceName;
        this.corsConfig = corsConfig;
    }

    /**
     * Creates a new helper based on the provided config.
     *
     * @param serviceName name of the service (for logging)
     * @param corsConfig {@link CrossOriginConfig} containing CORS set-up
     * @return new helper initialized with the CORS configuration
     */
    public static CorsEnabledServiceHelper create(String serviceName, Config corsConfig) {
        Objects.requireNonNull(corsConfig,
                "CrossOriginConfig passed to CORS service helper for registering routing rule must be non-null");
        return new CorsEnabledServiceHelper(serviceName, Optional.of(corsConfig));
    }

    /**
     * Creates a new helper based on the provided config.
     *
     * @param serviceName name of the service (for logging)
     * @param optCORSConfig {@link Optional} of a {@link Config} node containing CORS set-up
     * @return new helper initialized with the CORS configuration
     */
    public static CorsEnabledServiceHelper create(String serviceName, Optional<Config> optCORSConfig) {
        Objects.requireNonNull(optCORSConfig,
                "config passed to CORS service helper for registering routing rule must be non-null");
        return new CorsEnabledServiceHelper(serviceName, optCORSConfig);
    }

    /**
     * Constructs a {@link Handler} for performing CORS processing, according to the previously-provided {@link Config}.
     *
     * @return {@code Handler} for CORS processing
     */
    public Handler processor() {
        CorsSupport.Builder builder = CorsSupport.builder().name(serviceName);
        CrossOriginConfig crossOriginConfig;
        if (corsConfig != null && corsConfig.exists()) {
            crossOriginConfig = corsConfig.as(CrossOriginConfig::create)
                    .get();
        } else {
            // The built-in services need to support only the "read-only" HTTP methods (a.k.a CORS "simple" methods).
            crossOriginConfig = CrossOriginConfig.builder()
                    .allowMethods("GET", "HEAD", "OPTIONS")
                    .build();
        }
        if (crossOriginConfig.isEnabled()) {
            builder.addCrossOrigin(crossOriginConfig).build();
            LOGGER.log(Level.CONFIG, String.format("CORS is configured for service %s with %s", serviceName,
                    crossOriginConfig));
        } else {
            // CORS is disabled for this service. Return the no-op handler.
            LOGGER.log(Level.CONFIG, () -> String.format("CORS is disabled for service %s", serviceName));
            return NO_OP_HANDLER;
        }
        return builder.build();
    }
}
