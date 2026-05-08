/*
 * Copyright (c) 2025, 2026 Oracle and/or its affiliates.
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter;
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingLogRecordExporter;
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporterBuilder;
import io.opentelemetry.sdk.common.export.ProxyOptions;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class OtlpExporterConfigSupport {

    private OtlpExporterConfigSupport() {
    }

    static class CustomMethods {

        private CustomMethods() {
        }

        @Prototype.ConfigFactoryMethod("protocol")
        static OtlpExporterProtocolType createProtocol(Config config) {
            return OtlpExporterProtocolType.from(config);
        }

        @Prototype.ConfigFactoryMethod
        static SpanExporter createSpanExporter(Config config) {
            SpanExporterConfig exporterConfig = SpanExporterConfig.create(config);

            return switch (exporterConfig.type()) {
                case SpanExporterType.ZIPKIN -> createZipkinSpanExporter(config);
                case SpanExporterType.CONSOLE -> LoggingSpanExporter.create();
                case SpanExporterType.LOGGING_OTLP -> OtlpJsonLoggingSpanExporter.create();
                case SpanExporterType.OTLP -> createOtlpSpanExporter(config);
            };
        }

        @Prototype.ConfigFactoryMethod
        static LogRecordExporter createLogRecordExporter(Config config) {
            LogRecordExporterConfig exporterConfig = LogRecordExporterConfig.create(config);

            return switch (exporterConfig.type()) {
                case CONSOLE -> SystemOutLogRecordExporter.create();
                case LOGGING_OTLP -> OtlpJsonLoggingLogRecordExporter.create();
                case OTLP -> createOtlpLogRecordExporter(config);
            };
        }

        @Prototype.ConfigFactoryMethod("retryPolicy")
        static RetryPolicy createRetryPolicy(Config config) {

            RetryPolicy.RetryPolicyBuilder builder = RetryPolicy.builder();

            RetryPolicyConfig policyConfig = RetryPolicyConfig.builder().config(config).build();

            policyConfig.initialBackoff().ifPresent(builder::setInitialBackoff);
            policyConfig.maxBackoff().ifPresent(builder::setMaxBackoff);
            policyConfig.maxAttempts().ifPresent(builder::setMaxAttempts);
            policyConfig.maxBackoffMultiplier().ifPresent(builder::setBackoffMultiplier);

            return builder.build();
        }

        static SpanExporter createOtlpSpanExporter(Config config) {
            OtlpExporterConfig exporterConfig = OtlpExporterConfig.create(config);
            OtlpExporterProtocolType protocolType = exporterConfig.protocol().orElse(OtlpExporterProtocolType.DEFAULT);
            return switch (protocolType) {
                case HTTP_PROTO -> createHttpProtobufSpanExporter(OtlpHttpExporterConfig.create(config));
                case GRPC -> createGrpcSpanExporter(exporterConfig);
            };
        }

        static LogRecordExporter createOtlpLogRecordExporter(Config config) {
            OtlpExporterConfig exporterConfig = OtlpExporterConfig.create(config);
            OtlpExporterProtocolType protocolType = exporterConfig.protocol().orElse(OtlpExporterProtocolType.DEFAULT);
            return switch (protocolType) {
                case HTTP_PROTO -> createHttpProtobufLogRecordExporter(OtlpHttpExporterConfig.create(config));
                case GRPC -> createGrpcLogRecordExporter(exporterConfig);
            };
        }

        static ZipkinSpanExporter createZipkinSpanExporter(Config config) {
            ZipkinSpanExporterBuilder builder = ZipkinSpanExporter.builder();

            var zipkinConfig = ZipkinExporterConfig.create(config);

            zipkinConfig.compression().map(CompressionType::lowerCase).ifPresent(builder::setCompression);
            zipkinConfig.encoder().ifPresent(builder::setEncoder);
            zipkinConfig.endpoint().map(URI::toASCIIString).ifPresent(builder::setEndpoint);
            zipkinConfig.timeout().ifPresent(builder::setReadTimeout);
            zipkinConfig.sender().ifPresent(builder::setSender);
            zipkinConfig.localIpAddressSupplier().ifPresent(builder::setLocalIpAddressSupplier);
            zipkinConfig.meterProvider().ifPresent(builder::setMeterProvider);

            return builder.build();
        }

        @SuppressWarnings("checkstyle:ParameterNumber") // we need all of them
        static SpanExporter createHttpProtobufSpanExporter(OtlpHttpExporterConfig exporterConfig) {
            var builder = OtlpHttpSpanExporter.builder();

            apply(exporterConfig, builder::setProxy);

            apply(exporterConfig,
                  builder::setEndpoint,
                  builder::setCompression,
                  builder::setTimeout,
                  builder::setConnectTimeout,
                  builder::addHeader,
                  builder::setRetryPolicy,
                  builder::setClientTls,
                  builder::setTrustedCertificates,
                  builder::setSslContext,
                  builder::setMeterProvider);

            return builder.build();
        }

        static LogRecordExporter createHttpProtobufLogRecordExporter(OtlpHttpExporterConfig exporterConfig) {
            var builder = OtlpHttpLogRecordExporter.builder();

            apply(exporterConfig, builder::setProxyOptions);

            apply(exporterConfig,
                  builder::setEndpoint,
                  builder::setCompression,
                  builder::setTimeout,
                  builder::setConnectTimeout,
                  builder::addHeader,
                  builder::setRetryPolicy,
                  builder::setClientTls,
                  builder::setTrustedCertificates,
                  builder::setSslContext,
                  builder::setMeterProvider);

            return builder.build();
        }

        static LogRecordExporter createGrpcLogRecordExporter(OtlpExporterConfig exporterConfig) {
            var builder = OtlpGrpcLogRecordExporter.builder();

            apply(exporterConfig,
                  builder::setEndpoint,
                  builder::setCompression,
                  builder::setTimeout,
                  builder::setConnectTimeout,
                  builder::addHeader,
                  builder::setRetryPolicy,
                  builder::setClientTls,
                  builder::setTrustedCertificates,
                  builder::setSslContext,
                  builder::setMeterProvider);

            return builder.build();
        }

        @SuppressWarnings("checkstyle:ParameterNumber") // we need all of them
        static SpanExporter createGrpcSpanExporter(OtlpExporterConfig exporterConfig) {
            var builder = OtlpGrpcSpanExporter.builder();
            apply(exporterConfig,
                  builder::setEndpoint,
                  builder::setCompression,
                  builder::setTimeout,
                  builder::setConnectTimeout,
                  builder::addHeader,
                  builder::setRetryPolicy,
                  builder::setClientTls,
                  builder::setTrustedCertificates,
                  builder::setSslContext,
                  builder::setMeterProvider);

            return builder.build();
        }

        @SuppressWarnings("checkstyle:ParameterNumber") // we need all of them
        static void apply(OtlpExporterConfig target,
                          Consumer<String> doEndpoint,
                          Consumer<String> doCompression,
                          Consumer<Duration> doTimeout,
                          Consumer<Duration> doConnectTimeout,
                          BiConsumer<String, String> addHeader,
                          Consumer<RetryPolicy> doRetryPolicy,
                          BiConsumer<byte[], byte[]> doClientTls,
                          Consumer<byte[]> doTrustedCertificates,
                          BiConsumer<SSLContext, X509TrustManager> doSslContext,
                          Consumer<MeterProvider> doMeterProvider) {
            apply(target,
                  doEndpoint,
                  doCompression,
                  doTimeout,
                  doConnectTimeout,
                  addHeader,
                  doRetryPolicy,
                  doClientTls,
                  doTrustedCertificates,
                  doSslContext);

        }

        static void apply(OtlpHttpExporterConfig target,
                          Consumer<ProxyOptions> doProxyOptions) {
            target.proxyOptions().ifPresent(doProxyOptions);
        }

        @SuppressWarnings("checkstyle:ParameterNumber") // we need all of them
        static void apply(OtlpExporterConfig target,
                          Consumer<String> doEndpoint,
                          Consumer<String> doCompression,
                          Consumer<Duration> doTimeout,
                          Consumer<Duration> doConnectTimeout,
                          BiConsumer<String, String> addHeader,
                          Consumer<RetryPolicy> doRetryPolicy,
                          BiConsumer<byte[], byte[]> doClientTls,
                          Consumer<byte[]> doTrustedCertificates,
                          BiConsumer<SSLContext, X509TrustManager> doSslContext) {

            target.compression()
                    .map(CompressionType::lowerCase)
                    .ifPresent(doCompression);

            target.endpoint().map(URI::toASCIIString).ifPresent(doEndpoint);

            target.headers().forEach(addHeader);
            target.timeout().ifPresent(doTimeout);
            target.connectTimeout().ifPresent(doConnectTimeout);
            target.retryPolicy().ifPresent(doRetryPolicy);

            target.clientTlsPrivateKeyPem()
                    .ifPresent(privateKey -> target.clientTlsCertificatePem()
                            .ifPresent(certificatePem -> doClientTls.accept(certificatePem.bytes(),
                                                                            privateKey.bytes())));

            target.trustedCertificatesPem()
                    .ifPresent(certs -> doTrustedCertificates.accept(certs.bytes()));

            if (target.sslContext().isPresent() || target.trustManager().isPresent()) {
                doSslContext.accept(target.sslContext().orElse(null), target.trustManager().orElse(null));
            }
        }
    }

}
