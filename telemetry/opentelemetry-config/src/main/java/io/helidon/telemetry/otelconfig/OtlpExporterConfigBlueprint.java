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

package io.helidon.telemetry.otelconfig;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.Resource;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.export.RetryPolicy;

/**
 * Settings for OpenTelemetry OTLP exporters.
 *
 * @see io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder
 * @see io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder
 */
@Prototype.Configured
@Prototype.Blueprint
@Prototype.CustomMethods(OtlpExporterConfigSupport.CustomMethods.class)
interface OtlpExporterConfigBlueprint {

    /**
     * Exporter timeout.
     *
     * @return exporter timeout
     */
    @Option.Configured
    Optional<Duration> timeout();

    /**
     * Endpoint of the collector to which the exporter should transmit.
     *
     * @return collector endpoint
     */
    @Option.Configured
    Optional<URI> endpoint();

    /**
     * Compression the exporter uses.
     *
     * @return compression type
     */
    @Option.Configured
    Optional<CompressionType> compression();

    /**
     * Headers added to each export message.
     *
     * @return added headers
     */
    @Option.Configured
    Map<String, String> headers();

    /**
     * TLS client key.
     *
     * @return TLS client key
     */
    @Option.Configured("client.key")
    Optional<Resource> clientTlsPrivateKeyPem();

    /**
     * TLS certificate.
     *
     * @return TLS certificate
     */
    @Option.Configured("client.certificate")
    Optional<Resource> clientTlsCertificatePem();

    /**
     * Trusted certificates.
     *
     * @return trusted certificates
     */
    @Option.Configured("certificate")
    Optional<Resource> trustedCertificatesPem();

    /**
     * Retry policy.
     *
     * @return retry policy
     */
    @Option.Configured
    Optional<RetryPolicy> retryPolicy();

    /**
     * Exporter protocol type.
     *
     * @return exporter protocol type
     */
    @Option.Configured
    @Option.Default("DEFAULT")
    Optional<OtlpExporterProtocolType> protocol();

    /**
     * SSL context for the exporter.
     *
     * @return SSL context
     */
    Optional<SSLContext> sslContext();

    /**
     * X509 trust manager for the exporter.
     *
     * @return X509 trust manager
     */
    Optional<X509TrustManager> trustManager();

    /**
     * Meter provider for the exporter.
     *
     * @return meter provider
     */
    Optional<MeterProvider> meterProvider();

}
