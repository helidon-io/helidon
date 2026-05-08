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

package io.helidon.webserver.hsts;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Adds HTTP Strict Transport Security support to Helidon WebServer.
 */
public class HstsFeature implements Weighted, ServerFeature, RuntimeType.Api<HstsFeatureConfig> {
    /**
     * Default weight of the feature.
     */
    public static final double WEIGHT = 875;
    static final String HSTS_ID = "hsts";

    private final HstsFeatureConfig config;

    HstsFeature(HstsFeatureConfig config) {
        this.config = config;
    }

    /**
     * Fluent API builder to set up an instance.
     *
     * @return a new builder
     */
    public static HstsFeatureConfig.Builder builder() {
        return HstsFeatureConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static HstsFeature create(HstsFeatureConfig config) {
        return new HstsFeature(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new feature
     */
    public static HstsFeature create(Consumer<HstsFeatureConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    /**
     * Create a new feature with default setup.
     *
     * @return a new feature
     */
    public static HstsFeature create() {
        return builder().build();
    }

    /**
     * Create a new feature with custom setup.
     *
     * @param config configuration
     * @return a new configured feature
     */
    public static HstsFeature create(Config config) {
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

        HstsRoutingFeature hstsRoutingFeature = new HstsRoutingFeature(config);
        for (String socket : sockets) {
            featureContext.socket(socket)
                    .httpRouting()
                    .addFeature(hstsRoutingFeature);
        }
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return HSTS_ID;
    }

    @Override
    public HstsFeatureConfig prototype() {
        return config;
    }

    @Override
    public double weight() {
        return config.weight();
    }
}
