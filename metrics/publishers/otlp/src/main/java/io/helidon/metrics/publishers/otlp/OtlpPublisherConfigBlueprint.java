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

package io.helidon.metrics.publishers.otlp;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.common.Size;
import io.helidon.metrics.api.MetricsPublisherConfig;
import io.helidon.metrics.spi.MetricsPublisherProvider;

/**
 * Configuration of an OTLP HTTP/JSON publisher for Helidon metrics.
 */
@Api.Preview
@Prototype.Configured(value = OtlpPublisherProvider.TYPE, root = false)
@Prototype.Blueprint
@Prototype.Provides(MetricsPublisherProvider.class)
interface OtlpPublisherConfigBlueprint extends MetricsPublisherConfig, Prototype.Factory<OtlpPublisher> {
    /**
     * Name of this publisher instance. Defaults to {@code otlp} when absent.
     *
     * @return publisher instance name
     */
    @Override
    @Option.Configured
    Optional<String> name();

    /**
     * Whether this publisher is enabled.
     *
     * @return whether to publish metrics
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Complete HTTP or HTTPS endpoint for metrics, including its path.
     * The publisher does not append {@code /v1/metrics}.
     *
     * @return metrics endpoint
     */
    @Option.Configured
    @Option.Default("http://localhost:4318/v1/metrics")
    URI endpoint();

    /**
     * Delay between successive exports. The first export follows this delay.
     * Must be positive.
     *
     * @return export interval
     */
    @Option.Configured
    @Option.Default("PT60S")
    Duration interval();

    /**
     * Time allowed for an export, including retries. Also bounds the final export during shutdown.
     * Must be positive.
     *
     * @return export timeout
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration timeout();

    /**
     * Maximum size of an uncompressed JSON export request, measured in UTF-8 encoded bytes.
     * Must be positive and no greater than {@link Integer#MAX_VALUE} bytes.
     * Requests larger than this limit are discarded with a warning before sending an HTTP request.
     * This limit does not bound memory used to collect metrics.
     *
     * @return maximum request body size
     */
    @Option.Configured
    @Option.Default("64 MiB")
    Size maxRequestSize();

    /**
     * Service name to use when the resource attributes do not contain {@code service.name}.
     *
     * @return default service name
     */
    @Option.Configured
    @Option.Default("unknown_service")
    String serviceName();

    /**
     * String-valued resource attributes attached to each export.
     *
     * @return resource attributes
     */
    @Option.Configured
    Map<String, String> resourceAttributes();

    /**
     * Additional HTTP request headers, such as authentication headers.
     * The publisher supplies the JSON content type and controls HTTP message framing.
     *
     * @return request headers
     */
    @Option.Configured
    Map<String, String> headers();
}
