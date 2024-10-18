/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.webserver.concurrency.limits;

import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.common.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Server feature that adds limits as filters.
 * <p>
 * When using this feature, the limits operation is enforced within a filter, i.e. after the request
 * is accepted. This means it is used only for HTTP requests.
 */
@RuntimeType.PrototypedBy(LimitsFeatureConfig.class)
public class LimitsFeature implements ServerFeature, Weighted, RuntimeType.Api<LimitsFeatureConfig> {
    /**
     * Default weight of this feature. It is the first feature to be registered (above context and access log).
     * <p>
     * Context: 1100
     * <p>
     * Access Log: 1000
     * <p>
     * This feature: {@value}
     */
    public static final double WEIGHT = 2000;
    static final String ID = "limits";

    private final LimitsFeatureConfig config;

    private LimitsFeature(LimitsFeatureConfig config) {
        this.config = config;
    }

    /**
     * Fluent API builder to set up an instance.
     *
     * @return a new builder
     */
    public static LimitsFeatureConfig.Builder builder() {
        return LimitsFeatureConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static LimitsFeature create(LimitsFeatureConfig config) {
        return new LimitsFeature(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new feature
     */
    public static LimitsFeature create(Consumer<LimitsFeatureConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    /**
     * Create a new limits feature with default setup, but enabled.
     *
     * @return a new feature
     */
    public static LimitsFeature create() {
        return builder()
                .enabled(true)
                .build();
    }

    /**
     * Create a new context feature with custom setup.
     *
     * @param config configuration
     * @return a new configured feature
     */
    public static LimitsFeature create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        double featureWeight = config.weight();
        // all sockets
        Set<String> sockets = config.sockets();
        if (sockets.isEmpty()) {
            // configure on default only
            featureContext.socket(WebServer.DEFAULT_SOCKET_NAME)
                    .httpRouting()
                    .addFeature(new LimitsRoutingFeature(config, featureWeight));
        } else {
            // configure on all configured
            for (String socket : sockets) {
                featureContext.socket(socket)
                        .httpRouting()
                        .addFeature(new LimitsRoutingFeature(config, featureWeight));
            }
        }
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return ID;
    }

    @Override
    public double weight() {
        return config.weight();
    }

    @Override
    public LimitsFeatureConfig prototype() {
        return config;
    }
}
