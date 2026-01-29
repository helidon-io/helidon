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
package io.helidon.tracing;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.configurable.Resource;

/**
 * Common settings for tracers including settings for span processors and secure client connections.
 */
@Prototype.Blueprint
@Prototype.Configured
interface ExtendedTracerConfigBlueprint {

    /**
     * Service name of the traced service.
     *
     * @return service name
     */
    @Option.Configured("service")
    String serviceName();

    /**
     * URI for the collector to which to send tracing data.
     *
     * @return tracing collector URI
     */
    @Option.Configured("uri")
    Optional<URI> collectorUri();

    /**
     * Protocol (such as {@code http} or {@code https}) used in connecting to the tracing collector.
     *
     * @return collector protocol
     */
    @Option.Configured("protocol")
    Optional<String> collectorProtocol();

    /**
     * Port used in connecting to the tracing collector.
     *
     * @return collector port number
     */
    @Option.Configured("port")
    Optional<Integer> collectorPort();

    /**
     * Host used in connecting to the tracing collector.
     *
     * @return collector host
     */
    @Option.Configured("host")
    Optional<String> collectorHost();

    /**
     * Path at the collector host and port used when sending trace data to the collector.
     *
     * @return collector path
     */
    @Option.Configured("path")
    Optional<String> collectorPath();

    /**
     * Tracer-level tags with {@code String} values added to all reported spans.
     *
     * @return tracer-level string-valued tags
     */
    @Option.Configured("tags")
    @Option.Singular
    Map<String, String> tracerTags();

    /**
     * Tracer level tags with integer values added to all reported spans.
     *
     * @return tracer-level integer-valued tags
     */
    @Option.Configured("int-tags")
    @Option.Singular
    Map<String, Integer> intTracerTags();

    /**
     * Tracer-level tags with boolean values added to all reported spans.
     *
     * @return tracer-level boolean-valued tags
     */
    @Option.Configured("boolean-tags")
    @Option.Singular
    Map<String, Boolean> booleanTracerTags();

    /**
     * Whether to enable tracing. That is, whether to use a fully-featured tracing implementation on the path vs.
     * a no-op implementation.
     *
     * @return whether tracing is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Whether to create and register a tracer as the global tracer.
     *
     * @return whether to register the configured tracer as global
     */
    @Option.Configured("global")
    @Option.DefaultBoolean(true)
    boolean registerGlobal();

    /**
     * Private key for connecting securely to the tracing collector.
     *
     * @return private key
     */
    @Option.Configured("private-key-pem")
    Optional<Resource> privateKey();

    /**
     * Client certificate for connecting securely to the tracing collector.
     *
     * @return client certificate
     */
    @Option.Configured("client-cert-pem")
    Optional<Resource> clientCertificate();

    /**
     * Trusted certificates for connecting to the tracing collector.
     *
     * @return trusted certificates
     */
    @Option.Configured("trusted-cert-pem")
    Optional<Resource> trustedCertificate();

    /**
     * Type of span processor for accumulating spans before transmission to the tracing collector.
     *
     * @return span processor type
     */
    @Option.Configured
    @Option.Default("BATCH")
    SpanProcessorType spanProcessorType();

    /**
     * Delay between consecutive transmissions to the tracing collector (batch processing).
     *
     * @return delay between consecutive transmissions
     */
    @Option.Configured
    @Option.Default("PT5S")
    Duration scheduleDelay();

    /**
     * Maximum number of spans retained before discarding any not sent to the tracing collector (batch processing).
     *
     * @return maximum number of spans kept for transmission
     */
    @Option.Configured
    @Option.DefaultInt(2048)
    int maxQueueSize();

    /**
     * Maximum number of spans grouped for transmission together; typically does not exceed {@link #maxQueueSize()}
     * (batch processing).
     *
     * @return maximum number of spans batched
     */
    @Option.Configured
    @Option.DefaultInt(512)
    int maxExportBatchSize();

    /**
     * Maximum time a transmission can be in progress before being cancelled.
     *
     * @return maximum transmission time
     */
    @Option.Configured
    @Option.Default("PT10S")
    Duration exportTimeout();

    /**
     * Type of sampler for collecting spans.
     *
     * @return sampler type
     */
    @Option.Configured
    @Option.Default("CONSTANT")
    SamplerType samplerType();

    /**
     * Parameter value used by the selected sampler; interpretation depends on the sampler type..
     *
     * @return sampler parameter value
     */
    @Option.Configured
    @Option.DefaultDouble(1.0d)
    double samplerParam();

}
