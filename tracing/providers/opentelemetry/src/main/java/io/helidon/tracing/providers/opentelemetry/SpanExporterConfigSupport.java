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

import io.helidon.builder.api.Prototype;
import io.helidon.common.config.Config;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;

class SpanExporterConfigSupport {

//    static SpanExporter spanExporter(SpanExporterConfig spanExporterConfig) {
//        return switch (spanExporterConfig.exporterType()) {
//            case OTLP -> otlpSpanExporter(spanExporterConfig);
//            case ZIPKIN -> zipkinSpanExporter(spanExporterConfig);
//            case CONSOLE -> consoleSpanExporter(spanExporterConfig);
//            case LOGGING_OTLP -> loggingOtlpSpanExporter(spanExporterConfig);
//        };
//    }
//
//    static SpanExporter otlpSpanExporter(SpanExporterConfig spanExporterConfig) {
//        return switch (spanExporterConfig.exporterProtocol()) {
//            case GRPC -> {
//                var builder = OtlpGrpcSpanExporter.builder();
//
//                applyIfNonNull(spanExporterConfig.compression(), builder::setCompression);
//                applyIfNonNull(spanExporterConfig.exporterTimeout(), builder::setTimeout);
//
//                spanExporterConfig.headers().forEach(builder::addHeader);
//
//                if (spanExporterConfig.clientCertificate() != null && spanExporterConfig.privateKey() != null) {
//                    builder.setClientTls(spanExporterConfig.clientCertificate().bytes(),
//                                         spanExporterConfig.privateKey().bytes());
//                }
//                applyIfNonNull(spanExporterConfig.trustedCertificate(), cert -> builder.setTrustedCertificates(cert.bytes()));
//                yield builder.build();
//            }
//            case HTTP_PROTOBUF -> {
//                var builder = OtlpHttpSpanExporter.builder();
//
//
//            }
//        }
//    }

//    protected void apply(Consumer<String> doEndpoint,
//                         Consumer<String> doCompression,
//                         Consumer<Duration> doTimeout,
//                         BiConsumer<String, String> addHeader,
//                         BiConsumer<byte[], byte[]> doClientTls,
//                         Consumer<byte[]> doTrustedCertificates) {
//        super.apply(doEndpoint, doCompression, doTimeout);
//        if (privateKey != null && certificate != null) {
//            doClientTls.accept(certificate, privateKey);
//        }
//        if (trustedCertificates != null) {
//            doTrustedCertificates.accept(trustedCertificates);
//        }
//        if (!headers.isEmpty()) {
//            headers.forEach(addHeader);
//        }
//    }

    private static <T> void applyIfNonNull(T value, Consumer<T> operation) {
        if (value != null) {
            operation.accept(value);
        }
    }
}
