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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.otlp.OtlpJsonLoggingSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.exporter.zipkin.ZipkinSpanExporterBuilder;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class OtlpExporterConfigSupport {

    private OtlpExporterConfigSupport() {
    }

    static class CustomMethods {

        private CustomMethods() {
        }

        @Prototype.FactoryMethod
        static CompressionType createCompression(Config config) {
            return CompressionType.from(config);
        }

        @Prototype.FactoryMethod
        static OtlpExporterProtocolType createProtocol(Config config) {
            return OtlpExporterProtocolType.from(config);
        }

        @Prototype.FactoryMethod
        static SpanExporter createSpanExporter(Config config) {
            SpanExporterConfig exporterConfig = SpanExporterConfig.create(config);

            return switch (exporterConfig.type()) {
                case ExporterType.ZIPKIN -> createZipkinSpanExporter(config);
                case ExporterType.CONSOLE -> LoggingSpanExporter.create();
                case ExporterType.LOGGING_OTLP -> OtlpJsonLoggingSpanExporter.create();
                case ExporterType.OTLP -> createOtlpSpanExporter(config);
            };
        }

        static SpanExporter createOtlpSpanExporter(Config config) {
            OtlpExporterConfig exporterConfig = OtlpExporterConfig.create(config);
            OtlpExporterProtocolType protocolType = exporterConfig.protocol().orElse(OtlpExporterProtocolType.DEFAULT);
            return switch (protocolType) {
                case HTTP_PROTO -> createHttpProtobufSpanExporter(exporterConfig);
                case GRPC -> createGrpcSpanExporter(exporterConfig);
            };
        }

        static ZipkinSpanExporter createZipkinSpanExporter(Config config) {
            ZipkinSpanExporterBuilder builder = ZipkinSpanExporter.builder();

            var zipkinConfig = ZipkinExporterConfig.create(config);

            zipkinConfig.compression().map(CompressionType::value).ifPresent(builder::setCompression);
            zipkinConfig.endpoint().map(URI::toASCIIString).ifPresent(builder::setEndpoint);
            zipkinConfig.timeout().ifPresent(builder::setReadTimeout);
            zipkinConfig.sender().ifPresent(builder::setSender);
            zipkinConfig.localIpAddressSupplier().ifPresent(builder::setLocalIpAddressSupplier);
            zipkinConfig.meterProvider().ifPresent(builder::setMeterProvider);

            return builder.build();
        }

        @SuppressWarnings("checkstyle:ParameterNumber") // we need all of them
        static SpanExporter createHttpProtobufSpanExporter(OtlpExporterConfig exporterConfig) {
            var builder = OtlpHttpSpanExporter.builder();
            apply(exporterConfig,
                  builder::setEndpoint,
                  builder::setCompression,
                  builder::setTimeout,
                  builder::addHeader,
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
                  builder::addHeader,
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
                          BiConsumer<String, String> addHeader,
                          BiConsumer<byte[], byte[]> doClientTls,
                          Consumer<byte[]> doTrustedCertificates,
                          BiConsumer<SSLContext, X509TrustManager> doSslContext,
                          Consumer<MeterProvider> doMeterProvider) {

            target.compression()
                    .map(CompressionType::value)
                    .ifPresent(doCompression);

            target.endpoint().map(URI::toASCIIString).ifPresent(doEndpoint);

            target.headers().forEach(addHeader);
            target.timeout().ifPresent(doTimeout);

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
