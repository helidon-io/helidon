/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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

package io.helidon.tracing.providers.opentelemetry;

import java.time.Duration;
import java.util.function.Consumer;

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

import io.opentelemetry.sdk.trace.export.SpanExporter;

/**
 * Provides a config-based description of an OpenTelemetry span exporter and construction of the corresponding span
 * exporter based on that configuration.
 */
public abstract class SpanExporterConfiguration {

    protected SpanExporterConfiguration(Builder<?, ?> builder) {
    }

    /**
     * Creates a span exporter config builder for the specified exporter type.
     *
     * @return new builder
     */
    public static Builder<?, ?> builder(ExporterType type) {
        return switch (type) {
            case CONSOLE -> ConsoleSpanExporterConfig.builder();
            case ZIPKIN -> ZipkinSpanExporterConfiguration.builder();
            case LOGGING_OTLP -> LoggingOtlpSpanExporterConfig.builder();
            case OTLP -> throw new IllegalArgumentException("OTLP span exporter requires exporter protocol");
        };
    }

    /**
     * Creates a span exporter config builder based on the provided tracing config, using the {@code type} and,
     * if the type is {@code otlp}, the {@code exporter-protocol} to determine which specific type of builder to return.
     *
     * @param tracingConfig config node containing tracing settings
     * @return builder corresponding to the configuration
     */
    public static Builder<?, ?> builder(Config tracingConfig) {
        ExporterType exporterType = tracingConfig.get("type")
                .as(ExporterType.class)
                .orElse(ExporterType.OTLP);
        if (exporterType != ExporterType.OTLP) {
            return builder(exporterType);
        }
        OtlpExporterProtocol otlpExporterProtocol = tracingConfig.get("protocol")
                .asString().as(OtlpExporterProtocol::from)
                .orElse(OtlpExporterProtocol.GRPC);
        return switch (otlpExporterProtocol) {
            case GRPC -> OtlpGrpcSpanExporterConfig.builder();
            case HTTP_PROTOBUF -> HttpProtobufOtlpSpanExporterConfig.builder();
        };
    }

    /**
     * Creates a {@code SpanExporterConfig} based on the supplied configuration.
     *
     * @param tracingConfig config node containing settings for a span exporter
     * @return {@code SpanExporterConfig} based on the configuration
     */
    public static SpanExporterConfiguration create(Config tracingConfig) {
        return builder(tracingConfig).build();
    }

    /**
     * Returns the {@link io.opentelemetry.sdk.trace.export.SpanExporter} constructed from the declarative description.
     *
     * @return {@code SpanExporter} based on the configuration
     */
    public abstract SpanExporter spanExporter();

    /**
     * Configuration common to span exporters which transmit tracing data to another process and support compression and timeouts.
     */
    static abstract class Basic extends SpanExporterConfiguration {

        protected Basic(Builder<?, ?> builder) {
            super(builder);
        }

        @Configured(description = "Settings for a span exporter which transmits data to another process")
        static abstract class Builder<B extends Builder<B, T>, T extends Basic> extends SpanExporterConfiguration.Builder<B, T> {

            private String protocol;
            private String host;
            private Integer port;
            private String path;
            private String compression;
            private Duration timeout;

            protected Builder(String defaultProtocol,
                              String defaultHost,
                              Integer defaultPort,
                              String defaultPath,
                              String defaultCompression,
                              Duration defaultTimeout) {
                protocol = defaultProtocol;
                host = defaultHost;
                port = defaultPort;
                path = defaultPath;
                compression = defaultCompression;
                timeout = defaultTimeout;
            }

            /**
             * Apply the specified span exporter config node to the builder.
             *
             * @param config config node representing span exporter settings
             * @return updated builder
             */
            public B config(Config config) {
                super.config(config);
                config.get("protocol").asString().ifPresent(this::protocol);
                config.get("host").asString().ifPresent(this::host);
                config.get("port").asInt().ifPresent(this::port);
                config.get("path").asString().ifPresent(this::path);
                config.get("compression").asString().ifPresent(this::compression);
                config.get("timeout").as(Duration.class).ifPresent(this::exporterTimeout);
                return identity();
            }

            /**
             * Protocol (http vs. https) to use in transmitting trace data.
             *
             * @param protocol protocol to use
             * @return updated builder
             */
            @ConfiguredOption
            public B protocol(String protocol) {
                this.protocol = protocol;
                return identity();
            }

            /**
             * Host to which to transmit trace data.
             *
             * @param host host to which to send trace data
             * @return updated builder
             */
            @ConfiguredOption
            public B host(String host) {
                this.host = host;
                return identity();
            }

            /**
             * Port on host to which to connect to send trace data.
             *
             * @param port target port for sending trace data
             * @return updated builder
             */
            @ConfiguredOption
            public B port(Integer port) {
                this.port = port;
                return identity();
            }

            /**
             * Path to which to send trace data.
             *
             * @param path path for sending trace data
             * @return updated builder
             */
            @ConfiguredOption
            public B path(String path) {
                this.path = path;
                return identity();
            }

            @ConfiguredOption
            public B compression(String compression) {
                this.compression = compression;
                return identity();
            }

            @ConfiguredOption
            public B exporterTimeout(Duration timeout) {
                this.timeout = timeout;
                return identity();
            }

            protected String endpoint() {
                return protocol + "://" + host + ":" + port + (
                        path == null ? "" : (
                                path.charAt(0) != '/' ? "/" : "") + path);
            }

            protected void apply(Consumer<String> doEndpoint, Consumer<String> doCompression, Consumer<Duration> doTimeout) {
                doEndpoint.accept(endpoint());
                if (compression != null) {
                    doCompression.accept(compression);
                }
                if (timeout != null) {
                    doTimeout.accept(timeout);
                }
            }
        }
    }

    @Configured(description = "Span exporter settings")
    public static abstract class Builder<B extends Builder<B, T>, T extends SpanExporterConfiguration>
            implements io.helidon.common.Builder<B, T> {

        private ExporterType exporterType;

        /**
         * Exporter type for the span exporter.
         *
         * @param exporterType {@link ExporterType} of
         *                     the span exporter
         * @return updated builder
         */
        @ConfiguredOption(value = "OTLP")
        public B type(ExporterType exporterType) {
            this.exporterType = exporterType;
            return identity();
        }

        /**
         * Apply the specified OTLP span exporter config to the builder.
         *
         * @param spanExporterConfig config node representing OTLP span exporter settings.
         * @return updated builder
         */
        public B config(Config spanExporterConfig) {
            spanExporterConfig.get("type").as(ExporterType.class).ifPresent(this::type);
            return identity();
        }

        protected ExporterType type() {
            return exporterType;
        }
    }

}
