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

package io.helidon.tracing.exporter.zipkin;

import java.net.InetAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.api.metrics.MeterProvider;
import io.opentelemetry.sdk.common.InternalTelemetryVersion;
import zipkin2.Span;
import zipkin2.reporter.BytesEncoder;
import zipkin2.reporter.BytesMessageSender;
import zipkin2.reporter.SpanBytesEncoder;

/**
 * Configuration for the transitional Zipkin HTTP exporter.
 *
 * @deprecated Migrate to an OTLP exporter.
 */
@Deprecated(since = "4.5.7", forRemoval = true)
@Prototype.Blueprint(decorator = ZipkinExporterSupport.Decorator.class)
@Prototype.Configured
@Prototype.CustomMethods(ZipkinExporterSupport.CustomMethods.class)
@Prototype.Annotated("java.lang.Deprecated(since = \"4.5.7\", forRemoval = true)")
interface ZipkinSpanExporterConfigBlueprint extends Prototype.Factory<ZipkinSpanExporter> {
    /**
     * Collector HTTP or HTTPS endpoint.
     *
     * @return collector endpoint
     */
    @Option.Configured
    @Option.Default("http://localhost:9411/api/v2/spans")
    @Option.Confidential
    String endpoint();

    /**
     * Socket read timeout. Zero is normalized to {@link Integer#MAX_VALUE} milliseconds,
     * matching the former OpenTelemetry exporter. Ignored by application-supplied senders.
     *
     * @return read timeout
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration timeout();

    /**
     * Payload compression, either {@code gzip} or {@code none}. Ignored by application-supplied senders.
     *
     * @return compression method
     */
    @Option.Configured
    @Option.Default("gzip")
    String compression();

    /**
     * Span encoder. Its encoding determines batch framing and HTTP content type for the default sender.
     *
     * @return span encoder
     */
    @Option.Configured("encoder")
    @Option.Default("JSON_V2")
    SpanBytesEncoder encoding();

    /**
     * Custom encoder overriding the configured encoding. Arbitrary encoder instances require programmatic configuration.
     *
     * @return custom encoder
     */
    Optional<BytesEncoder<Span>> encoder();

    /**
     * Custom sender. On successful exporter construction, ownership transfers to the exporter,
     * which closes the sender during shutdown. Endpoint, timeout, and compression do not configure this sender.
     *
     * @return custom sender
     */
    @Option.Confidential
    Optional<BytesMessageSender> sender();

    /**
     * Local address supplier. The inherited Zipkin contract permits the supplier to return {@code null}
     * when no local address is available; in that case the endpoint has no IP address.
     *
     * @return local address supplier
     */
    Optional<Supplier<InetAddress>> localIpAddressSupplier();

    /**
     * Meter provider. If absent, the global OpenTelemetry meter provider is obtained lazily.
     *
     * @return meter provider
     */
    Optional<MeterProvider> meterProvider();

    /**
     * Exporter self-observability metric version.
     *
     * @return metric version
     */
    @Option.Configured
    @Option.Default("LEGACY")
    InternalTelemetryVersion internalTelemetryVersion();
}
