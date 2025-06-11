/*
 * Copyright (c) 2022, 2025 Oracle and/or its affiliates.
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

package io.helidon.webserver.context;

import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Adds {@link io.helidon.common.context.Context} support to Helidon WebServer.
 * When added to the processing, further processing will be executed in a request specific context.
 */
@RuntimeType.PrototypedBy(ContextFeatureConfig.class)
public class ContextFeature implements Weighted, ServerFeature, RuntimeType.Api<ContextFeatureConfig> {
    /**
     * Default weight of the feature. It is quite high, as context is used by a lot of other features.
     */
    public static final double WEIGHT = Weighted.DEFAULT_WEIGHT + 1000;
    static final String CONTEXT_ID = "context";

    private final ContextFeatureConfig config;

    ContextFeature(ContextFeatureConfig config) {
        this.config = config;
    }

    /**
     * Fluent API builder to set up an instance.
     *
     * @return a new builder
     */
    public static ContextFeatureConfig.Builder builder() {
        return ContextFeatureConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static ContextFeature create(ContextFeatureConfig config) {
        return new ContextFeature(config);
    }


    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new feature
     */
    public static ContextFeature create(Consumer<ContextFeatureConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }


    /**
     * Create a new context feature with default setup.
     *
     * @return a new feature
     */
    public static ContextFeature create() {
        return builder().build();
    }

    /**
     * Create a new context feature with custom setup.
     *
     * @param config configuration
     * @return a new configured feature
     */
    public static ContextFeature create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        // all sockets
        Set<String> sockets = config.sockets();
        featureContext.socket(WebServer.DEFAULT_SOCKET_NAME)
                .httpRouting()
                .addFeature(new ContextRoutingFeature(config));
        for (String socket : sockets) {
            featureContext.socket(socket)
                    .httpRouting()
                    .addFeature(new ContextRoutingFeature(config));
        }
    }
    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return CONTEXT_ID;
    }

    @Override
    public ContextFeatureConfig prototype() {
        return config;
    }

    @Override
    public double weight() {
        return config.weight();
    }
}
