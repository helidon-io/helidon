/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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
import io.helidon.common.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Adds CORS support to Helidon WebServer.
 */
@RuntimeType.PrototypedBy(CorsConfig.class)
public class CorsFeature implements ServerFeature, RuntimeType.Api<CorsConfig> {
    /**
     * Default weight of the feature.
     */
    public static final double WEIGHT = 950;
    static final String CORS_ID = "cors";
    private final CorsConfig config;

    CorsFeature(CorsConfig config) {
        this.config = config;
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

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (!config.enabled()) {
            return;
        }

        Set<String> sockets = new HashSet<>(config.sockets());
        if (sockets.isEmpty()) {
            sockets.addAll(featureContext.sockets());
            sockets.add(WebServer.DEFAULT_SOCKET_NAME);
        }

        // now register for each socket that is required (or to all of them)
        // we may improve this approach to only register paths that are valid for that socket (through detailed configuration)
        // for now this is copying the cors to all sockets
        Config corsConfig = config.config().orElseGet(Config::empty).root().get("cors");
        for (String socket : sockets) {
            featureContext.socket(socket)
                    .httpRouting()
                    .addFeature(new CorsHttpFeature(config.weight(),
                                                    CorsSupport.builder()
                                                            .config(corsConfig)
                                                            .name("cors-" + socket)
                                                            .build()));
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
}
