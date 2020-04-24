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

import java.util.logging.Level;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.webserver.Handler;

/**
 * Allows services (including Helidon built-in services) to register CORS support easily.
 * <p>
 *     Callers use either {@link #create(String)} or {@link #create(String, CrossOriginConfig)} to initialize the helper for a
 *     service. The helper uses the {@link CrossOriginConfig} instance to set up CORS behavior for the service. If the caller
 *     passes a null {@code CrossOriginConfig} or invokes the other variant of {@code create} then the sets up CORS using a
 *     default configuration:
 * </p>
 *     <pre>
 *     enabled: true
 *     allow-origins: ["*"]
 *     allow-methods: ["GET", "HEAD", "OPTIONS"]
 *     allow-headers: ["*"]
 *     allow-credentials: false
 *     max-age: 3600
 *     </pre>
 *     All of those settings except for {@code allow-methods} are the defaults for {@code CrossOriginConfig}.
 */
public class CorsEnabledServiceHelper {

    /**
     * Conventional configuration key for CORS set-up; used for built-in services.
     */
    public static final String CORS_CONFIG_KEY = "cors";

    private static final Handler NO_OP_HANDLER = (req, res) -> req.next();

    private static final Logger LOGGER = Logger.getLogger(CorsEnabledServiceHelper.class.getName());

    private final String serviceName;
    private final CrossOriginConfig crossOriginConfig;

    private CorsEnabledServiceHelper(String serviceName, CrossOriginConfig crossOriginConfig) {
        this.serviceName = serviceName;
        this.crossOriginConfig = crossOriginConfig;
    }

    /**
     * Creates a new helper based on the provided config.
     *
     * @param serviceName name of the service (for logging)
     * @param crossOriginConfig {@link CrossOriginConfig} containing CORS set-up; if null, a default is used
     * @return new helper initialized with the CORS configuration
     */
    public static CorsEnabledServiceHelper create(String serviceName, CrossOriginConfig crossOriginConfig) {
        if (crossOriginConfig == null) {
            crossOriginConfig = defaultCrossOriginConfig();
        }
        return new CorsEnabledServiceHelper(serviceName, crossOriginConfig);
    }

    /**
     * Creates a new helper based on a default CORS config for services.
     *
     * @param serviceName name of the service (for logging)
     * @return new helper initialized with a default CORS configuration
     */
    public static CorsEnabledServiceHelper create(String serviceName) {
        return new CorsEnabledServiceHelper(serviceName, defaultCrossOriginConfig());
    }

    private static CrossOriginConfig defaultCrossOriginConfig() {
        return CrossOriginConfig.builder()
                .allowMethods("GET", "HEAD", "OPTIONS")
                .build();
    }

    /**
     * Constructs a {@link Handler} for performing CORS processing, according to the previously-provided {@link Config}.
     *
     * @return {@code Handler} for CORS processing
     */
    public Handler processor() {
        CorsSupport.Builder builder = CorsSupport.builder().name(serviceName);
        if (crossOriginConfig.isEnabled()) {
            builder.addCrossOrigin(crossOriginConfig);
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
