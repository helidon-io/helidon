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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;

/**
 * Settings for an OTLP http/protobuf or grpc span exporter.
 */
public abstract class OtlpSpanExporterConfiguration extends SpanExporterConfiguration.Basic {

    protected OtlpSpanExporterConfiguration(Builder<?, ?> builder) {
        super(builder);
    }

    @Configured
    public static abstract class Builder<B extends Builder<B, T>, T extends OtlpSpanExporterConfiguration> extends SpanExporterConfiguration.Basic.Builder<B, T> {
        private final Map<String, String> headers = new HashMap<>();
        // Collector protocol (scheme)
        private String collectorProtocol = "http";
        private OtlpExporterProtocol otlpExporterProtocol = OtlpExporterProtocol.DEFAULT;
        private Integer exporterPort;
        private String exporterHost = "localhost";
        private String exporterPath;
        private Duration exporterTimeout;
        private String compression;
        private byte[] privateKey;
        private byte[] certificate;
        private byte[] trustedCertificates;

        public Builder(String defaultProtocol,
                       String defaultHost,
                       Integer defaultPort,
                       String defaultPath,
                       String defaultCompression,
                       Duration defaultTimeout,
                       String defaultCollectorProtocol) {
            super(defaultProtocol, defaultHost, defaultPort, defaultPath, defaultCompression, defaultTimeout);
            this.collectorProtocol = defaultCollectorProtocol;
        }

//        /**
//         * Applies the builder's values to create a new OTLP {@link io.opentelemetry.sdk.trace.export.SpanExporter};
//         *
//         * @return new {@code SpanExporter} according to the assigned settings
//         */
//        public T build() {
//            if (exporterPort == null) {
//                exporterPort = exporterProtocol.defaultPort();
//            }
//            // The different exporter implementations do not share a common superclass or interface so use method refs to avoid
//            // replicating too much code.
//            return switch (exporterProtocol) {
//                case GRPC -> {
//                    var builder = OtlpGrpcSpanExporter.builder()
//                            .setEndpoint(collectorProtocol + "://" + exporterHost + ":" + exporterPort
//                                                 + (
//                                    exporterPath == null
//                                            ? ""
//                                            : (
//                                                    exporterPath.charAt(0) != '/'
//                                                            ? "/"
//                                                            : "")
//                                                    + exporterPath));
//                    apply(builder::setCompression,
//                          builder::setTimeout,
//                          builder::addHeader,
//                          builder::setClientTls,
//                          builder::setTrustedCertificates);
//
//                    yield builder.build();
//                }
//                case HTTP_PROTOBUF -> {
//                    var builder = OtlpHttpSpanExporter.builder()
//                            .setEndpoint(collectorProtocol + "://" + exporterHost + ":" + exporterPort
//                                                 + (exporterPath == null ? "" : exporterPath));
//
//                    apply(builder::setCompression,
//                          builder::setTimeout,
//                          builder::addHeader,
//                          builder::setClientTls,
//                          builder::setTrustedCertificates);
//
//                    yield builder.build();
//                }
//            };
//        }

        /**
         * Apply the specified OTLP span exporter config to the builder.
         *
         * @param otlpSpanExporterConfig config node representing OTLP span exporter settings.
         * @return updated builder
         */
        public B config(Config otlpSpanExporterConfig) {
            otlpSpanExporterConfig.get("headers").asMap().ifPresent(this::headers);
            otlpSpanExporterConfig.get("protocol").asString().ifPresent(this::collectorProtocol);
            otlpSpanExporterConfig.get("host").asString().ifPresent(this::collectorHost);
            otlpSpanExporterConfig.get("port").asInt().ifPresent(this::collectorPort);
            otlpSpanExporterConfig.get("path").asString().ifPresent(this::collectorPath);

            otlpSpanExporterConfig.get("private-key-pem").map(io.helidon.common.configurable.Resource::create)
                    .ifPresent(this::privateKey);
            otlpSpanExporterConfig.get("client-cert-pem").map(io.helidon.common.configurable.Resource::create)
                    .ifPresent(this::clientCertificate);
            otlpSpanExporterConfig.get("trusted-cert-pem").map(io.helidon.common.configurable.Resource::create)
                    .ifPresent(this::trustedCertificate);

            otlpSpanExporterConfig.get("compression").asString().ifPresent(this::compression);

            return identity();
        }

        /**
         * Name/value pairs for headers to send with all transmitted traces.
         *
         * @param headers headers to send
         * @return updated builder
         */
        @ConfiguredOption
        public B headers(Map<String, String> headers) {
            Objects.requireNonNull(headers, "headers must not be null");
            this.headers.clear();
            this.headers.putAll(headers);
            return identity();
        }

        /**
         * Adds a name/value pair as a header to send with all transmitted traces.
         *
         * @param name  header name
         * @param value header value
         * @return updated builder
         */
        public B addHeader(String name, String value) {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(value, "value must not be null");
            headers.put(name, value);
            return identity();
        }

        /**
         * Protocol to use (such as {@code http} or {@code https}) to connect to tracing collector.
         * Default is defined by each tracing integration.
         *
         * @param protocol protocol to use
         * @return updated builder instance
         */
        @ConfiguredOption(key = "protocol")
        public B collectorProtocol(String protocol) {
            Objects.requireNonNull(protocol, "protocol must not be null");
            this.collectorProtocol = protocol;
            return identity();
        }

        /**
         * Port to use to connect to tracing collector.
         * Default is defined by each tracing integration.
         *
         * @param port port to use
         * @return updated builder instance
         */
        @ConfiguredOption(key = "port")
        public B collectorPort(int port) {
            exporterPort = port;
            return identity();
        }

        /**
         * Host to use to connect to tracing collector.
         * Default is defined by each tracing integration.
         *
         * @param host host to use
         * @return updated builder instance
         */
        public B collectorHost(String host) {
            Objects.requireNonNull(host, "host must not be null");
            exporterHost = host;
            return identity();
        }

        /**
         * Path on the collector host to use when sending data to tracing collector.
         * Default is defined by each tracing integration.
         *
         * @param path path to use
         * @return updated builder instance
         */
        public B collectorPath(String path) {
            Objects.requireNonNull(path, "path must not be null");
            exporterPath = path;
            return identity();
        }

        /**
         * Maximum time the exporter will wait for completion of data transmission.
         *
         * @param exporterTimeout maximum time for exporting a batch
         * @return updated builder
         */
        @ConfiguredOption
        public B exporterTimeout(Duration exporterTimeout) {
            this.exporterTimeout = exporterTimeout;
            return identity();
        }

        /**
         * Compression type for exporting data.
         *
         * @param compression type of compression to use for exporting data
         * @return updated builder
         */
        @ConfiguredOption
        public B compression(String compression) {
            Objects.requireNonNull(compression, "compression must not be null");
            this.compression = compression;
            return identity();
        }

        /**
         * Protocol (e.g., {@code grpc} vs. {@code http/protobuf}) for the exporter.
         *
         * @param otlpExporterProtocol {@link OtlpExporterProtocol}
         *                         to use in connecting to the back end.
         * @return updated builder
         * @see #collectorProtocol(String) for specifying http/https
         */
        @ConfiguredOption
        public B exporterProtocol(OtlpExporterProtocol otlpExporterProtocol) {
            Objects.requireNonNull(otlpExporterProtocol, "exporterProtocol must not be null");
            this.otlpExporterProtocol = otlpExporterProtocol;
            return identity();
        }

        /**
         * Private key in PEM format.
         *
         * @param privateKey key privateKey
         * @return updated builder
         */
        @ConfiguredOption(key = "private-key-pem")
        public B privateKey(io.helidon.common.configurable.Resource privateKey) {
            Objects.requireNonNull(privateKey, "privateKey must not be null");
            return privateKey(privateKey.bytes());
        }

        /**
         * Private key.
         *
         * @param privateKey private key as byte array
         * @return updated builder
         */
        public B privateKey(byte[] privateKey) {
            Objects.requireNonNull(privateKey, "privateKey must not be null");
            this.privateKey = privateKey;
            return identity();
        }

        /**
         * Certificate of client in PEM format.
         *
         * @param clientCert certificate client cert
         * @return updated builder
         */
        @ConfiguredOption(key = "client-cert-pem")
        public B clientCertificate(io.helidon.common.configurable.Resource clientCert) {
            Objects.requireNonNull(clientCert, "clientCert must not be null");
            return clientCertificate(clientCert.bytes());
        }

        /**
         * Certificate of client.
         * @param clientCert cert as byte array
         * @return updated builder
         */
        public B clientCertificate(byte[] clientCert) {
            Objects.requireNonNull(clientCert, "clientCert must not be null");
            this.certificate = clientCert;
            return identity();
        }

        /**
         * Trusted certificates in PEM format.
         *
         * @param trustedCert trusted certificates trusted cert
         * @return updated builder
         */
        @ConfiguredOption(key = "trusted-cert-pem")
        public B trustedCertificate(io.helidon.common.configurable.Resource trustedCert) {
            Objects.requireNonNull(trustedCert, "trustedCert must not be null");
            return trustedCertificate(trustedCert.bytes());
        }

        /**
         * Trusted cert.
         *
         * @param trustedCert trusted certificate as byte array
         * @return updated builder
         */
        public B trustedCertificate(byte[] trustedCert) {
            Objects.requireNonNull(trustedCert, "trustedCert must not be null");
            this.trustedCertificates = trustedCert;
            return identity();
        }

        protected void apply(Consumer<String> doEndpoint,
                             Consumer<String> doCompression,
                           Consumer<Duration> doTimeout,
                           BiConsumer<String, String> addHeader,
                           BiConsumer<byte[], byte[]> doClientTls,
                           Consumer<byte[]> doTrustedCertificates) {
            super.apply(doEndpoint, doCompression, doTimeout);
            if (privateKey != null && certificate != null) {
                doClientTls.accept(certificate, privateKey);
            }
            if (trustedCertificates != null) {
                doTrustedCertificates.accept(trustedCertificates);
            }
            if (!headers.isEmpty()) {
                headers.forEach(addHeader);
            }
        }

    }
}
