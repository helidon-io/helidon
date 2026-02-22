/*
 * Copyright (c) 2022, 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver.cors;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weight;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.service.registry.Service;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpFeature;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Adds CORS support to Helidon WebServer.
 */
@Weight(CorsFeature.WEIGHT)
@Service.Singleton
public class CorsFeature implements Weighted, ServerFeature, RuntimeType.Api<CorsConfig> {
    /**
     * Default weight of the feature.
     *
     * @deprecated this is internal constant, and it will not be public in a future version of Helidon
     */
    @Deprecated(since = "4.4.0")
    public static final double WEIGHT = 850;
    static final String CORS_ID = "cors";
    private static final System.Logger LOGGER = System.getLogger(CorsFeature.class.getName());

    private final CorsConfig config;

    CorsFeature(CorsConfig config) {
        this.config = config;
    }

    @Service.Inject
    CorsFeature(ServiceRegistry registry, io.helidon.config.Config config) {
        this.config = CorsConfig.builder()
                .config(config.get(CORS_ID))
                .serviceRegistry(registry)
                .buildPrototype();
    }

    /**
     * Fluent API builder to set up an instance.
     *
     * @return a new builder
     */
    public static CorsConfig.Builder builder() {
        return CorsConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static CorsFeature create(CorsConfig config) {
        return new CorsFeature(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new feature
     */
    public static CorsFeature create(Consumer<CorsConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    /**
     * Create a new CORS feature with default setup.
     *
     * @return a new feature
     */
    public static CorsFeature create() {
        return builder().build();
    }

    /**
     * Create a new CORS feature with custom setup.
     *
     * @param config configuration
     * @return a new configured feature
     */
    public static CorsFeature create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * Create a new CORS feature with custom setup.
     *
     * @param config configuration
     * @return a new configured feature
     * @deprecated use {@link #create(io.helidon.config.Config)} instead
     */
    @Deprecated(forRemoval = true, since = "4.4.0")
    @SuppressWarnings("removal")
    public static CorsFeature create(io.helidon.common.config.Config config) {
        return builder()
                .config(config)
                .build();
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (!config.enabled()) {
            if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                LOGGER.log(System.Logger.Level.TRACE, "CorsServerFeature is disabled");
            }
            return;
        }

        Set<String> sockets = new HashSet<>(config.sockets());
        if (sockets.isEmpty()) {
            sockets.addAll(featureContext.sockets());
            sockets.add(WebServer.DEFAULT_SOCKET_NAME);
        }

        /*
        The new approach with CorsPathConfig is compatible with the original "paths" configuration key
        that we used to read manually. No need to get the configuration.
         */
        for (String socket : sockets) {
            featureContext.socket(socket)
                    .httpRouting()
                    .addFeature(new CorsHttpFeature(config,
                                                    socket))
                    .addFeature(new CorsOptionsHttpFeature(socket));
        }
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return CORS_ID;
    }

    @Override
    public CorsConfig prototype() {
        return config;
    }

    @Override
    public double weight() {
        return config.weight();
    }

    private static class CorsOptionsHttpFeature implements HttpFeature, Weighted {
        private final CorsPathValidator validator = CorsPathValidator.create(CorsPathConfig.builder()
                                                                                     .pathPattern("/*")
                                                                                     .clearAllowHeaders()
                                                                                     .clearAllowOrigins()
                                                                                     .build());
        private final String socket;

        CorsOptionsHttpFeature(String socket) {
            this.socket = socket;
        }

        @Override
        public void setup(HttpRouting.Builder routing) {
            routing.options("/*", this::handle);
        }

        private void handle(ServerRequest req, ServerResponse res) {
            if (CorsValidator.isPreflight(req)) {
                // this is for backward compatibility
                if (res.headers().contains(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN)) {
                    // already processed
                    res.status(Status.OK_200)
                            .send();
                    return;
                }
                if (validator.preFlight(req, res).shouldContinue()) {
                    res.status(Status.OK_200)
                            .send();
                }
                /*
                Uncomment this code once we remove deprecated code from CORS
                res.status(Status.OK_200)
                            .send();
                 */
            }
        }

        @Override
        public double weight() {
            // this should be the last route to send options for pre-flight requests if nobody else handles it
            return 1;
        }

        @Override
        public String toString() {
            return "CORS options handler for " + socket;
        }
    }
}
