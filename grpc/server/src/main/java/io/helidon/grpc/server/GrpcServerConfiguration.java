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
package io.helidon.grpc.server;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import io.helidon.common.context.Context;
import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.grpc.core.GrpcTlsDescriptor;
import io.helidon.tracing.Tracer;

/**
 * The configuration for a gRPC server.
 */
public interface GrpcServerConfiguration {
    /**
     * The default server name.
     */
    String DEFAULT_NAME = "grpc.server";

    /**
     * The default grpc port.
     */
    int DEFAULT_PORT = 1408;

    /**
     * The default number of worker threads that will be used if not explicitly set.
     */
    int DEFAULT_WORKER_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * Get the server name.
     *
     * @return the server name
     */
    String name();

    /**
     * Get the server port.
     *
     * @return the server port
     */
    int port();

    /**
     * The top level {@link io.helidon.common.context.Context} to be used by the server.
     * @return a context instance with registered application scoped instances
     */
    Context context();

    /**
     * Determine whether use native transport if possible.
     * <p>
     * If native transport support is enabled, gRPC server will use epoll on
     * Linux, or kqueue on OS X. Otherwise, the standard NIO transport will
     * be used.
     *
     * @return {@code true} if native transport should be used
     */
    boolean useNativeTransport();

    /**
     * Returns a {@link io.helidon.tracing.Tracer}. Default is {@link io.helidon.tracing.Tracer#global()}.
     *
     * @return a tracer to use - never {@code null}
     */
    Tracer tracer();

    /**
     * Returns tracing configuration.
     *
     * @return a tracing configuration.
     */
    GrpcTracingConfig tracingConfig();

    /**
     * Returns a count of threads in s pool used to process gRPC requests.
     * <p>
     * Default value is {@code CPU_COUNT * 2}.
     *
     * @return a workers count
     */
    int workers();

    /**
     * Returns a SslConfiguration to use with the server socket. If not {@code null} then
     * the server enforces an SSL communication.
     *
     * @return a TLS configuration to use
     */
    GrpcTlsDescriptor tlsConfig();

    /**
     * Returns the period for counting rapid resets (stream RST sent by client before any data have been sent by server).
     *
     * @return the period for counting rapid resets
     */
    Duration rapidResetCheckPeriod();

    /**
     * Returns the maximum allowed number of rapid resets (stream RST sent by client before any data have been sent by server).
     * When reached within {@link #rapidResetCheckPeriod()}, GOAWAY is sent to client and connection is closed.
     *
     * @return the maximum allowed number of rapid resets
     */
    int maxRapidResets();

    /**
     * Creates new instance with default values for all configuration properties.
     *
     * @return a new instance
     */
    static GrpcServerConfiguration create() {
        return builder().build();
    }

    /**
     * Creates new instance with values from external configuration.
     *
     * @param config the externalized configuration
     * @return a new instance
     */
    static GrpcServerConfiguration create(Config config) {
        return builder(config).build();
    }

    /**
     * Creates new instance of a {@link Builder server configuration builder}.
     *
     * @return a new builder instance
     */
    static GrpcServerConfiguration.Builder builder() {
        return new Builder();
    }

    /**
     * Creates new instance of a {@link Builder server configuration builder} with defaults from external configuration source.
     *
     * @param config the externalized configuration
     * @return a new builder instance
     */
    static Builder builder(Config config) {
        return new Builder().config(config);
    }

    /**
     * A {@link GrpcServerConfiguration} builder.
     */
    @Configured
    final class Builder implements io.helidon.common.Builder<Builder, GrpcServerConfiguration> {
        private static final AtomicInteger GRPC_SERVER_COUNTER = new AtomicInteger(1);

        private String name = DEFAULT_NAME;

        private int port = DEFAULT_PORT;

        private boolean useNativeTransport;

        private Tracer tracer;

        private GrpcTracingConfig tracingConfig;

        private int workers;

        private GrpcTlsDescriptor tlsConfig = null;

        private Context context;

        private int maxRapidResets = 200;

        private Duration rapidResetCheckPeriod = Duration.ofSeconds(30);

        private Builder() {
        }

        /**
         * Update the builder from configuration.
         *
         * @param config configuration instance
         * @return updated builder
         */
        @ConfiguredOption(key = "native",
                type = Boolean.class,
                value = "false",
                description = "Specify if native transport should be used.")
        public Builder config(Config config) {
            if (config == null) {
                return this;
            }

            name = config.get("name").asString().orElse(DEFAULT_NAME);
            port = config.get("port").asInt().orElse(DEFAULT_PORT);
            maxRapidResets = config.get("max-rapid-resets").asInt().orElse(200);
            rapidResetCheckPeriod = config.get("rapid-reset-check-period").as(Duration.class).orElse(Duration.ofSeconds(30));
            useNativeTransport = config.get("native").asBoolean().orElse(false);
            config.get("workers").asInt().ifPresent(this::workersCount);

            return this;
        }

        /**
         * Set the name of the gRPC server.
         * <p>
         * Configuration key: {@code name}
         *
         * @param name  the name of the gRPC server
         *
         * @return an updated builder
         */
        @ConfiguredOption(key = "name", value = DEFAULT_NAME)
        public Builder name(String name) {
            this.name = name == null ? null : name.trim();
            return this;
        }

        /**
         * Sets server port. If port is {@code 0} or less then any available ephemeral port will be used.
         * <p>
         * Configuration key: {@code port}
         *
         * @param port the server port
         * @return an updated builder
         */
        @ConfiguredOption(value = "" + DEFAULT_PORT)
        public Builder port(int port) {
            this.port = port < 0 ? 0 : port;
            return this;
        }

        /**
         * Period for counting rapid resets(stream RST sent by client before any data have been sent by server).
         * Default value is {@code PT30S}.
         *
         * @param rapidResetCheckPeriod duration
         * @return updated builder
         * @see <a href="https://en.wikipedia.org/wiki/ISO_8601#Durations">ISO_8601 Durations</a>
         * @see #maxRapidResets()
         */
         @ConfiguredOption("PT30S")
        public Builder rapidResetCheckPeriod(Duration rapidResetCheckPeriod) {
            Objects.requireNonNull(rapidResetCheckPeriod);
            this.rapidResetCheckPeriod = rapidResetCheckPeriod;
            return this;
        }

        /**
         * Maximum number of rapid resets(stream RST sent by client before any data have been sent by server).
         * When reached within {@link #rapidResetCheckPeriod()}, GOAWAY is sent to client and connection is closed.
         * Default value is {@code 200}.
         *
         * @param maxRapidResets maximum number of rapid resets
         * @return updated builder
         * @see #rapidResetCheckPeriod()
         */
         @ConfiguredOption("200")
        public Builder maxRapidResets(int maxRapidResets) {
            this.maxRapidResets = maxRapidResets;
            return this;
        }

        /**
         * Configure the application scoped context to be used as a parent for webserver request contexts.
         * @param context top level context
         * @return an updated builder
         */
        public Builder context(Context context) {
            this.context = context;
            return this;
        }

        /**
         * Sets a tracer.
         *
         * @param tracer a tracer to set
         * @return an updated builder
         */
        public Builder tracer(Tracer tracer) {
            Objects.requireNonNull(tracer);
            this.tracer = tracer;
            return this;
        }

        /**
         * Sets a Tracer.
         *
         * @param tracerBuilder a tracer builder to set; will be built as a first step of this method execution
         * @return updated builder
         */
        public Builder tracer(Supplier<? extends Tracer> tracerBuilder) {
            Objects.requireNonNull(tracerBuilder);
            this.tracer = tracerBuilder.get();
            return this;
        }

        /**
         * Set trace configuration.
         *
         * @param tracingConfig the tracing configuration to set
         * @return an updated builder
         */
        public Builder tracingConfig(GrpcTracingConfig tracingConfig) {
            this.tracingConfig = tracingConfig;
            return this;
        }

        /**
         * Sets a count of threads in pool used to process HTTP requests.
         * Default value is {@code CPU_COUNT * 2}.
         * <p>
         * Configuration key: {@code workers}
         *
         * @param workers a workers count
         * @return an updated builder
         */
        @ConfiguredOption(key = "workers", value = "Number of processors available to the JVM")
        public Builder workersCount(int workers) {
            this.workers = workers;
            return this;
        }

        /**
         * Configures TLS configuration to use with the server socket. If not {@code null} then
         * the server enforces an TLS communication.
         *
         * @param tlsConfig a TLS configuration to use
         * @return this builder
         */
        public Builder tlsConfig(GrpcTlsDescriptor tlsConfig) {
            this.tlsConfig = tlsConfig;
            return this;
        }

        String name() {
            return name;
        }

        int port() {
            return port;
        }

        /**
         * Current Helidon {@link io.helidon.common.context.Context}.
         *
         * @return current context
         */
        public Context context() {
            return context;
        }

        Tracer tracer() {
            return tracer;
        }

        GrpcTracingConfig tracingConfig() {
            return tracingConfig;
        }

        GrpcTlsDescriptor tlsConfig() {
            return tlsConfig;
        }

        boolean useNativeTransport() {
            return useNativeTransport;
        }

        int workers() {
            return workers;
        }

        int maxRapidResets() {
            return maxRapidResets;
        }

        Duration rapidResetCheckPeriod() {
            return rapidResetCheckPeriod;
        }

        @Override
        public GrpcServerConfiguration build() {
            if (name == null || name.isEmpty()) {
                name = DEFAULT_NAME;
            }

            if (port < 0) {
                port = 0;
            }

            if (context == null) {
                context = Context.builder()
                        .id("grpc-" + GRPC_SERVER_COUNTER.getAndIncrement())
                        .build();
            }

            if (tracer == null) {
                tracer = Tracer.global();
            }

            if (tracingConfig == null) {
                tracingConfig = GrpcTracingConfig.create();
            }

            if (!context.get(Tracer.class).isPresent()) {
                context.register(tracer);
            }

            if (workers <= 0) {
                workers = DEFAULT_WORKER_COUNT;
            }

            return GrpcServerBasicConfig.create(this);
        }
    }
}
