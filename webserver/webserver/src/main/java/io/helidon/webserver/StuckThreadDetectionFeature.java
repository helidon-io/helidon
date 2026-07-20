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

package io.helidon.webserver;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.common.Weighted;
import io.helidon.config.Config;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Reports HTTP request threads that have been executing for longer than a configured threshold.
 * <p>
 * Detection covers ordinary HTTP routing and handler processing. It does not include post-routing transport cleanup, idle
 * HTTP/1 connections, connections handed to an upgraded protocol, HTTP/2 connection readers, or HTTP/2 subprotocol
 * processing such as gRPC streams. A long-running request is not necessarily stuck, so this feature reports diagnostics but
 * never interrupts a request thread.
 */
public final class StuckThreadDetectionFeature
        implements Weighted, ServerFeature, RuntimeType.Api<StuckThreadDetectionConfig> {
    /**
     * Default feature weight. The detector runs within request context and after concurrency admission, but before
     * access logging, security, observability, and application routing.
     */
    static final double WEIGHT = Weighted.DEFAULT_WEIGHT + 950;

    static final String FEATURE_ID = "stuck-thread-detection";

    private final StuckThreadDetectionConfig config;

    StuckThreadDetectionFeature(StuckThreadDetectionConfig config) {
        this.config = config;
    }

    /**
     * Create a stuck thread detection feature with default configuration.
     *
     * @return a new feature
     */
    public static StuckThreadDetectionFeature create() {
        return builder().build();
    }

    /**
     * Create a stuck thread detection feature from configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static StuckThreadDetectionFeature create(Config config) {
        return builder()
                .config(Objects.requireNonNull(config))
                .build();
    }

    /**
     * Create a stuck thread detection feature from its configuration prototype.
     *
     * @param config configuration prototype
     * @return a new feature
     */
    public static StuckThreadDetectionFeature create(StuckThreadDetectionConfig config) {
        return new StuckThreadDetectionFeature(Objects.requireNonNull(config));
    }

    /**
     * Create a stuck thread detection feature, customizing its configuration.
     *
     * @param builderConsumer consumer of the configuration builder
     * @return a new feature
     */
    public static StuckThreadDetectionFeature create(Consumer<StuckThreadDetectionConfig.Builder> builderConsumer) {
        return builder()
                .update(Objects.requireNonNull(builderConsumer))
                .build();
    }

    /**
     * Create a fluent configuration builder.
     *
     * @return a new builder
     */
    public static StuckThreadDetectionConfig.Builder builder() {
        return StuckThreadDetectionConfig.builder();
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

        for (String socket : sockets) {
            featureContext.socket(socket)
                    .httpRouting()
                    .addFilter(new StuckThreadDetectionFilter(config, socket));
        }
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return FEATURE_ID;
    }

    @Override
    public double weight() {
        return config.weight();
    }

    @Override
    public StuckThreadDetectionConfig prototype() {
        return config;
    }
}
