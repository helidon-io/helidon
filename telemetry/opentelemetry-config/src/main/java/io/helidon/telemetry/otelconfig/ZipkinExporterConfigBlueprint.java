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

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

import io.opentelemetry.api.metrics.MeterProvider;
import zipkin2.codec.SpanBytesEncoder;
import zipkin2.reporter.Sender;

@Prototype.Configured
@Prototype.Blueprint(decorator = ZipkinExporterConfigSupport.BuilderDecorator.class)
interface  ZipkinExporterConfigBlueprint {

    /**
     * Collector endpoint to which this exporter should transmit.
     *
     * @return collector endpoint
     */
    @Option.Configured
    Optional<URI> endpoint();

    /**
     * Encoder type.
     *
     * @return encoder type.
     */
    @Option.Configured
    Optional<SpanBytesEncoder> encoder();

    /**
     * Compression type.
     *
     * @return compression type
     */
    @Option.Configured
    Optional<CompressionType> compression();

    /**
     * Exporter timeout.
     *
     * @return exporter timeout
     */
    @Option.Configured
    Optional<Duration> timeout();

    /**
     * Zipkin sender.
     *
     * @return Zipkin sender
     */
    Optional<Sender> sender();

    /**
     * Supplier of a local IP address.
     *
     * @return supplier of a local IP address
     */
    Optional<Supplier<InetAddress>> localIpAddressSupplier();

    /**
     * Meter provider.
     *
     * @return meter provider
     */
    Optional<MeterProvider> meterProvider();


}
