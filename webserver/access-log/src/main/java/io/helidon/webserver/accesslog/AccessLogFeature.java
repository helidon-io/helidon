/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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
package io.helidon.webserver.accesslog;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.helidon.builder.api.RuntimeType;
import io.helidon.config.Config;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.spi.ServerFeature;

/**
 * Service that adds support for Access logging to Server.
 */
@RuntimeType.PrototypedBy(AccessLogConfig.class)
public final class AccessLogFeature implements ServerFeature, RuntimeType.Api<AccessLogConfig> {
    /**
     * Name of the {@link System#getLogger(String)} used to log access log records.
     * The message logged contains all information, so the format should be modified
     * to only log the message.
     *
     * @see AccessLogHandler
     */
    public static final String DEFAULT_LOGGER_NAME = "io.helidon.webserver.AccessLog";
    static final String ACCESS_LOG_ID = "access-log";
    static final double WEIGHT = 1000;

    private final List<AccessLogEntry> logFormat;
    private final boolean enabled;
    private final Clock clock;
    private final double weight;
    private final AccessLogConfig config;
    private final String loggerName;

    private AccessLogFeature(AccessLogConfig config) {
        this.config = config;
        this.enabled = config.enabled();
        this.logFormat = config.entries();
        this.clock = config.clock();
        this.loggerName = config.loggerName();
        this.weight = config.weight();
    }

    /**
     * Create Access log support with default configuration.
     *
     * @return a new access log support to be registered with WebServer routing
     */
    public static AccessLogFeature create() {
        return builder().build();
    }

    /**
     * Create Access log support configured from {@link io.helidon.config.Config}.
     *
     * @param config to configure a new access log support instance
     * @return a new access log support to be registered with WebServer routing
     */
    public static AccessLogFeature create(Config config) {
        return builder()
                .config(config)
                .build();
    }

    /**
     * A new fluent API builder to create Access log support instance.
     *
     * @return a new builder
     */
    public static AccessLogConfig.Builder builder() {
        return AccessLogConfig.builder();
    }

    /**
     * Create a new instance from its configuration.
     *
     * @param config configuration
     * @return a new feature
     */
    public static AccessLogFeature create(AccessLogConfig config) {
        return new AccessLogFeature(config);
    }

    /**
     * Create a new instance customizing its configuration.
     *
     * @param builderConsumer consumer of configuration
     * @return a new feature
     */
    public static AccessLogFeature create(Consumer<AccessLogConfig.Builder> builderConsumer) {
        return builder()
                .update(builderConsumer)
                .build();
    }

    @Override
    public void setup(ServerFeatureContext featureContext) {
        if (!enabled) {
            return;
        }

        Set<String> sockets = new HashSet<>(config.sockets());
        if (sockets.isEmpty()) {
            sockets.addAll(featureContext.sockets());
            sockets.add(WebServer.DEFAULT_SOCKET_NAME);
        }

        for (String socket : sockets) {
            HttpRouting.Builder httpRouting = featureContext.socket(socket).httpRouting();
            httpRouting.addFeature(httpFeature(socket));
        }
    }

    @Override
    public AccessLogConfig prototype() {
        return config;
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String type() {
        return ACCESS_LOG_ID;
    }

    AccessLogHttpFeature httpFeature(String socketName) {
        return new AccessLogHttpFeature(weight, clock, logFormat, loggerName, socketName);
    }
}
