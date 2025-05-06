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
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.helidon.common.config.Config;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporterBuilder;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class OtlpSpanExporterConfigSupport {

    static SpanExporter createGrpcSpanExporter(Config spanExporterConfigNode) {

        OtlpSpanExporterConfig exporterConfig = OtlpSpanExporterConfig.builder().config(spanExporterConfigNode).build();
        OtlpGrpcSpanExporterBuilder builder = OtlpGrpcSpanExporter.builder();
        apply(exporterConfig,
              OtlpExporterProtocol.GRPC,
              builder::setEndpoint,
              builder::setCompression,
              builder::setTimeout,
              builder::addHeader,
              builder::setClientTls,
              builder::setTrustedCertificates);
        return builder.build();
    }

    static SpanExporter createHttpProtobufSpanExporter(Config spanExporterConfigNode) {
        OtlpSpanExporterConfig exporterConfigBuilder = OtlpSpanExporterConfig.builder().config(spanExporterConfigNode).build();
        OtlpHttpSpanExporterBuilder builder = OtlpHttpSpanExporter.builder();
        apply(exporterConfigBuilder,
              OtlpExporterProtocol.HTTP_PROTOBUF,
              builder::setEndpoint,
              builder::setCompression,
              builder::setTimeout,
              builder::addHeader,
              builder::setClientTls,
              builder::setTrustedCertificates);
        return builder.build();
    }

    static void apply(OtlpSpanExporterConfig config,
                      OtlpExporterProtocol otlpExporterProtocol,
                      Consumer<String> doEndpoint,
                      Consumer<String> doCompression,
                      Consumer<Duration> doTimeout,
                      BiConsumer<String, String> addHeader,
                      BiConsumer<byte[], byte[]> doClientTls,
                      Consumer<byte[]> doTrustedCertificates) {

        BasicSpanExporterConfigSupport.apply(config,
                                             otlpExporterProtocol.defaultProtocol(),
                                             otlpExporterProtocol.defaultHost(),
                                             otlpExporterProtocol.defaultPort(),
                                             otlpExporterProtocol.defaultPath(),
                                             doEndpoint,
                                             doCompression,
                                             doTimeout);

        config.privateKey()
                .ifPresent(privateKey -> config.clientCertificate()
                        .ifPresent(clientCert -> doClientTls.accept(clientCert.bytes(), privateKey.bytes())));
        config.trustedCertificate().ifPresent(trustedCert -> doTrustedCertificates.accept(trustedCert.bytes()));
        config.headers().forEach(addHeader);
    }
}
