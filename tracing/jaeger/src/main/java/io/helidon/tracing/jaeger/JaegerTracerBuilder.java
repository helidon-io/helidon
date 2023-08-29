/*
 * Copyright (c) 2019, 2023 Oracle and/or its affiliates.
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

package io.helidon.tracing.jaeger;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

import io.helidon.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;
import io.helidon.tracing.opentelemetry.HelidonOpenTelemetry;
import io.helidon.tracing.opentelemetry.OpenTelemetryTracerProvider;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.exporter.jaeger.JaegerGrpcSpanExporterBuilder;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

/**
 * The JaegerTracerBuilder is a convenience builder for {@link io.helidon.tracing.Tracer} to use with Jaeger.
 * <p>
 * <b>Unless You want to explicitly depend on Jaeger in Your code, please
 * use {@link io.helidon.tracing.TracerBuilder#create(String)} or
 * {@link io.helidon.tracing.TracerBuilder#create(io.helidon.config.Config)} that is abstracted.</b>
 * <p>
 * The Jaeger tracer uses environment variables and system properties to override the defaults.
 * Except for {@code protocol} and {@code service} these are honored, unless overridden in configuration
 * or through the builder methods.
 * See <a href="https://github.com/jaegertracing/jaeger-client-java/blob/master/jaeger-core/README.md">Jaeger documentation</a>
 * for details.
 * <p>
 * The following table lists jaeger specific defaults and configuration options.
 * <table class="config">
 *     <caption>Tracer Configuration Options</caption>
 *     <tr>
 *         <th>option</th>
 *         <th>default</th>
 *         <th>description</th>
 *     </tr>
 *     <tr>
 *         <td>{@code service}</td>
 *         <td>&nbsp;</td>
 *         <td>Service name</td>
 *     </tr>
 *     <tr>
 *         <td>{@code protocol}</td>
 *         <td>{@code http}</td>
 *         <td>The protocol to use. By default http is used.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code host}</td>
 *         <td>{@code 127.0.0.1}</td>
 *         <td>Host to use</td>
 *     </tr>
 *     <tr>
 *         <td>{@code port}</td>
 *         <td>{@value  #DEFAULT_HTTP_PORT}</td>
 *         <td>Port to be used</td>
 *     </tr>
 *     <tr>
 *         <td>{@code path}</td>
 *         <td>&nbsp;</td>
 *         <td>Path to be used.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code exporter-timeout}</td>
 *         <td>10 seconds</td>
 *         <td>Timeout of exporter</td>
 *     </tr>
 *     <tr>
 *         <td>{@code private-key-pem}</td>
 *         <td>&nbsp;</td>
 *         <td>Client private key in PEM format</td>
 *     </tr>
 *     <tr>
 *         <td>{@code client-cert-pem}</td>
 *         <td>&nbsp;</td>
 *         <td>Client certificate in PEM format</td>
 *     </tr>
 *     <tr>
 *         <td>{@code trusted-cert-pem}</td>
 *         <td>&nbsp;</td>
 *         <td>Trusted certificates in PEM format</td>
 *     </tr>
 *     <tr>
 *         <td>{@code sampler-type}</td>
 *         <td>{@code const} with param set to {@code 1}</td>
 *         <td>Sampler type {@code const} (0 to disable, 1 to always enabled),
 *              {@code ratio} (sample param contains the ratio as a double)</td>
 *     </tr>
 *     <tr>
 *         <td>{@code sampler-param}</td>
 *         <td>sampler type default</td>
 *         <td>Numeric parameter specifying details for the sampler type.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link io.helidon.tracing.TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code boolean-tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link io.helidon.tracing.TracerBuilder}</td>
 *     </tr>
 *     <tr>
 *         <td>{@code int-tags}</td>
 *         <td>&nbsp;</td>
 *         <td>see {@link io.helidon.tracing.TracerBuilder}</td>
 *     </tr>
 * </table>
 */
@Configured(prefix = "tracing", root = true, description = "Jaeger tracer configuration.")
public class JaegerTracerBuilder implements TracerBuilder<JaegerTracerBuilder> {
    static final Logger LOGGER = Logger.getLogger(JaegerTracerBuilder.class.getName());

    static final boolean DEFAULT_ENABLED = true;
    static final String DEFAULT_HTTP_HOST = "localhost";
    static final int DEFAULT_HTTP_PORT = 14250;

    static final long DEFAULT_SCHEDULE_DELAY = 30_000;
    static final int DEFAULT_MAX_QUEUE_SIZE = 2048;
    static final int DEFAULT_MAX_EXPORT_BATCH_SIZE = 512;
    private final Map<String, String> tags = new HashMap<>();
    // this is a backward incompatible change, but the correct choice is Jaeger, not B3
    private final Set<PropagationFormat> propagationFormats = EnumSet.noneOf(PropagationFormat.class);
    private String serviceName;
    private String protocol = "http";
    private String host = DEFAULT_HTTP_HOST;
    private int port = DEFAULT_HTTP_PORT;
    private SamplerType samplerType = SamplerType.CONSTANT;
    private Number samplerParam = 1;
    private boolean enabled = DEFAULT_ENABLED;
    private boolean global = true;
    private byte[] privateKey;
    private byte[] certificate;
    private byte[] trustedCertificates;
    private String path;
    private Config config;
    private Duration exporterTimeout = Duration.ofSeconds(10);
    private Duration scheduleDelay = Duration.ofMillis(DEFAULT_SCHEDULE_DELAY);
    private int maxQueueSize = DEFAULT_MAX_QUEUE_SIZE;
    private int maxExportBatchSize = DEFAULT_MAX_EXPORT_BATCH_SIZE;

    private SpanProcessorType spanProcessorType = SpanProcessorType.BATCH;

    /**
     * Default constructor, does not modify any state.
     */
    protected JaegerTracerBuilder() {
    }

    /**
     * Get a Jaeger {@link io.helidon.tracing.Tracer } builder for processing tracing data of a service with a given name.
     *
     * @param serviceName name of the service that will be using the tracer.
     * @return {@code Tracer} builder for Jaeger.
     */
    public static JaegerTracerBuilder forService(String serviceName) {
        return create()
                .serviceName(serviceName);
    }

    /**
     * Create a new builder based on values in configuration.
     * This requires at least a key "service" in the provided config.
     *
     * @param config configuration to load this builder from
     * @return a new builder instance.
     * @see io.helidon.tracing.jaeger.JaegerTracerBuilder#config(io.helidon.config.Config)
     */
    public static JaegerTracerBuilder create(Config config) {
        return create().config(config);
    }

    static JaegerTracerBuilder create() {
        return new JaegerTracerBuilder();
    }

    @Override
    public JaegerTracerBuilder serviceName(String name) {
        this.serviceName = name;
        return this;
    }

    @Override
    public JaegerTracerBuilder collectorProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    @Override
    public JaegerTracerBuilder collectorPort(int port) {
        this.port = port;
        return this;
    }

    @Override
    public JaegerTracerBuilder collectorPath(String path) {
        this.path = path;
        return this;
    }

    @Override
    public JaegerTracerBuilder collectorHost(String host) {
        this.host = host;
        return this;
    }

    @Override
    public JaegerTracerBuilder addTracerTag(String key, String value) {
        this.tags.put(key, value);
        return this;
    }

    @Override
    public JaegerTracerBuilder addTracerTag(String key, Number value) {
        this.tags.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public JaegerTracerBuilder addTracerTag(String key, boolean value) {
        this.tags.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public JaegerTracerBuilder config(Config config) {
        this.config = config;
        config.get("enabled").asBoolean().ifPresent(this::enabled);
        config.get("service").asString().ifPresent(this::serviceName);
        config.get("protocol").asString().ifPresent(this::collectorProtocol);
        config.get("host").asString().ifPresent(this::collectorHost);
        config.get("port").asInt().ifPresent(this::collectorPort);
        config.get("path").asString().ifPresent(this::collectorPath);
        config.get("sampler-type").asString().as(SamplerType::create).ifPresent(this::samplerType);
        config.get("sampler-param").asDouble().ifPresent(this::samplerParam);
        config.get("private-key-pem").as(io.helidon.common.configurable.Resource::create).ifPresent(this::privateKey);
        config.get("client-cert-pem").as(io.helidon.common.configurable.Resource::create).ifPresent(this::clientCertificate);
        config.get("trusted-cert-pem").as(io.helidon.common.configurable.Resource::create).ifPresent(this::trustedCertificates);
        config.get("propagation").asList(String.class)
                .ifPresent(propagationStrings -> {
                    propagationStrings.stream()
                            .map(String::toUpperCase)
                            .map(PropagationFormat::valueOf)
                            .forEach(this::addPropagation);
                });

        config.get("tags").detach()
                .asMap()
                .orElseGet(Map::of)
                .forEach(this::addTracerTag);

        config.get("boolean-tags")
                .asNodeList()
                .ifPresent(nodes -> {
                    nodes.forEach(node -> {
                        this.addTracerTag(node.key().name(), node.asBoolean().get());
                    });
                });

        config.get("int-tags")
                .asNodeList()
                .ifPresent(nodes -> {
                    nodes.forEach(node -> {
                        this.addTracerTag(node.key().name(), node.asInt().get());
                    });
                });

        config.get("global").asBoolean().ifPresent(this::registerGlobal);

        config.get("span-processor-type").asString()
                .ifPresent(it -> spanProcessorType(SpanProcessorType.valueOf(it.toUpperCase())));
        config.get("exporter-timeout").as(Duration.class).ifPresent(this::exporterTimeout);
        config.get("schedule-delay").as(Duration.class).ifPresent(this::scheduleDelay);
        config.get("max-queue-size").asInt().ifPresent(this::maxQueueSize);
        config.get("max-export-batch-size").asInt().ifPresent(this::maxExportBatchSize);

        return this;
    }

    /**
     * Private key in PEM format.
     *
     * @param resource key resource
     * @return updated builder
     */
    @ConfiguredOption(key = "private-key-pem")
    public JaegerTracerBuilder privateKey(io.helidon.common.configurable.Resource resource) {
        this.privateKey = resource.bytes();
        return this;
    }

    /**
     * Certificate of client in PEM format.
     *
     * @param resource certificate resource
     * @return updated builder
     */
    @ConfiguredOption(key = "client-cert-pem")
    public JaegerTracerBuilder clientCertificate(io.helidon.common.configurable.Resource resource) {
        this.certificate = resource.bytes();
        return this;
    }

    /**
     * Trusted certificates in PEM format.
     *
     * @param resource trusted certificates resource
     * @return updated builder
     */
    @ConfiguredOption(key = "trusted-cert-pem")
    public JaegerTracerBuilder trustedCertificates(io.helidon.common.configurable.Resource resource) {
        this.trustedCertificates = resource.bytes();
        return this;
    }

    /**
     * Span Processor type used.
     *
     * @param spanProcessorType to use
     * @return updated builder
     */
    @ConfiguredOption("batch")
    public JaegerTracerBuilder spanProcessorType(SpanProcessorType spanProcessorType) {
        this.spanProcessorType = spanProcessorType;
        return this;
    }


    /**
     * Timeout of exporter requests.
     *
     * @param exporterTimeout timeout to use
     * @return updated builder
     */
    @ConfiguredOption("PT10S")
    public JaegerTracerBuilder exporterTimeout(Duration exporterTimeout) {
        this.exporterTimeout = exporterTimeout;
        return this;
    }

    /**
     * Schedule Delay of exporter requests.
     *
     * @param scheduleDelay timeout to use
     * @return updated builder
     */
    @ConfiguredOption("PT5S")
    public JaegerTracerBuilder scheduleDelay(Duration scheduleDelay) {
        this.scheduleDelay = scheduleDelay;
        return this;
    }

    /**
     * Maximum Queue Size of exporter requests.
     *
     * @param maxQueueSize to use
     * @return updated builder
     */
    @ConfiguredOption("2048")
    public JaegerTracerBuilder maxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        return this;
    }

    /**
     * Maximum Export Batch Size of exporter requests.
     *
     * @param maxExportBatchSize to use
     * @return updated builder
     */
    @ConfiguredOption("512")
    public JaegerTracerBuilder maxExportBatchSize(int maxExportBatchSize) {
        this.maxExportBatchSize = maxExportBatchSize;
        return this;
    }

    @Override
    public JaegerTracerBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public JaegerTracerBuilder registerGlobal(boolean global) {
        this.global = global;
        return this;
    }

    @Override
    public <B> B unwrap(Class<B> builderClass) {
        if (builderClass.isAssignableFrom(getClass())) {
            return builderClass.cast(this);
        }
        throw new IllegalArgumentException("This builder is a Jaeger tracer builder, cannot be unwrapped to "
                                                   + builderClass.getName());
    }

    /**
     * The sampler parameter (number).
     *
     * @param samplerParam parameter of the sampler
     * @return updated builder instance
     */
    @ConfiguredOption("1")
    public JaegerTracerBuilder samplerParam(Number samplerParam) {
        this.samplerParam = samplerParam;
        return this;
    }

    /**
     * Sampler type.
     * <p>
     * See <a href="https://www.jaegertracing.io/docs/latest/sampling/#client-sampling-configuration">Sampler types</a>.
     *
     * @param samplerType type of the sampler
     * @return updated builder instance
     */
    @ConfiguredOption("CONSTANT")
    public JaegerTracerBuilder samplerType(SamplerType samplerType) {
        this.samplerType = samplerType;
        return this;
    }

    /**
     * Add propagation format to use.
     * Default propagation is {@code b3} and {@code jaeger}, to be compatible both with 3.0 (backward), and with
     * 4.x, which uses {@code jaeger} as default.
     * If any propagation is specified either in configuration or through this method, defaults will not be honored.
     *
     * @param propagationFormat propagation value
     * @return updated builder instance
     */
    @ConfiguredOption(key = "propagation", kind = ConfiguredOption.Kind.LIST, type = PropagationFormat.class, value = "B3,JAEGER")
    public JaegerTracerBuilder addPropagation(PropagationFormat propagationFormat) {
        Objects.requireNonNull(propagationFormat);
        this.propagationFormats.add(propagationFormat);
        return this;
    }

    /**
     * Builds the {@link io.helidon.tracing.Tracer} for Jaeger based on the configured parameters.
     *
     * @return the tracer
     */
    @Override
    public Tracer build() {
        Tracer result;

        if (HelidonOpenTelemetry.AgentDetector.isAgentPresent(config)) {
            return HelidonOpenTelemetry.create(GlobalOpenTelemetry.get(),
                                               GlobalOpenTelemetry.getTracer(this.serviceName),
                                               tags);
        }

        if (enabled) {
            if (serviceName == null) {
                throw new IllegalArgumentException(
                        "Configuration must at least contain the 'service' key ('tracing.service` in MP) with service name");
            }

            JaegerGrpcSpanExporterBuilder spanExporterBuilder = JaegerGrpcSpanExporter.builder()
                    .setEndpoint(protocol + "://" + host + ":" + port + (path == null ? "" : path))
                    .setTimeout(exporterTimeout);

            if (privateKey != null && certificate != null) {
                spanExporterBuilder.setClientTls(privateKey, certificate);
            }

            if (trustedCertificates != null) {
                spanExporterBuilder.setTrustedCertificates(trustedCertificates);
            }

            SpanExporter exporter = spanExporterBuilder.build();

            Sampler sampler = switch (samplerType) {
                case RATIO -> Sampler.traceIdRatioBased(samplerParam.doubleValue());
                case CONSTANT -> samplerParam.intValue() == 1
                        ? Sampler.alwaysOn()
                        : Sampler.alwaysOff();
            };

            AttributesBuilder attributesBuilder = Attributes.builder();
            attributesBuilder.put(ResourceAttributes.SERVICE_NAME, this.serviceName);
            tags.forEach(attributesBuilder::put);

            Resource serviceName = Resource.create(attributesBuilder.build());
            OpenTelemetry ot = OpenTelemetrySdk.builder()
                    .setTracerProvider(SdkTracerProvider.builder()
                            .addSpanProcessor(spanProcessor(exporter))
                            .setSampler(sampler)
                            .setResource(serviceName)
                            .build())
                    .setPropagators(ContextPropagators.create(TextMapPropagator.composite(createPropagators())))
                    .build();

            result = HelidonOpenTelemetry.create(ot, ot.getTracer(this.serviceName), Map.of());

            if (global) {
                GlobalOpenTelemetry.set(ot);
            }

            LOGGER.info(() -> "Creating Jaeger tracer for '" + this.serviceName + "' configured with " + protocol
                    + "://" + host + ":" + port);
        } else {
            LOGGER.info("Jaeger Tracer is explicitly disabled.");
            result = Tracer.noOp();
        }

        if (global) {
            OpenTelemetryTracerProvider.globalTracer(result);
        }

        return result;
    }

    String path() {
        return path;
    }

    Map<String, String> tags() {
        return tags;
    }

    String serviceName() {
        return serviceName;
    }

    String protocol() {
        return protocol;
    }

    String host() {
        return host;
    }

    Integer port() {
        return port;
    }

    SamplerType samplerType() {
        return samplerType;
    }

    Number samplerParam() {
        return samplerParam;
    }

    boolean isEnabled() {
        return enabled;
    }

    SpanProcessorType spanProcessorType() {
        return spanProcessorType;
    }

    Duration exporterTimeout() {
        return exporterTimeout;
    }

    Duration scheduleDelay() {
        return scheduleDelay;
    }

    int maxQueueSize() {
        return maxQueueSize;
    }

    int maxExportBatchSize() {
        return maxExportBatchSize;
    }

    List<TextMapPropagator> createPropagators() {
        if (propagationFormats.isEmpty()) {
            // for backward compatibility, we add B3 if nothing is defined
            propagationFormats.add(PropagationFormat.B3);
            // and to be compatible with 4.x, we add Jaeger, which is the default for the future
            propagationFormats.add(PropagationFormat.JAEGER);
        }
        return propagationFormats.stream()
                .map(JaegerTracerBuilder::mapFormatToPropagator)
                .toList();
    }

    private SpanProcessor spanProcessor(SpanExporter exporter) {
        return switch (spanProcessorType) {
            case BATCH -> BatchSpanProcessor.builder(exporter)
                    .setScheduleDelay(scheduleDelay)
                    .setMaxQueueSize(maxQueueSize)
                    .setMaxExportBatchSize(maxExportBatchSize)
                    .setExporterTimeout(exporterTimeout)
                    .build();
            case SIMPLE -> SimpleSpanProcessor.create(exporter);
        };
    }

    private static TextMapPropagator mapFormatToPropagator(PropagationFormat propagationFormat) {
        return switch (propagationFormat) {
            case B3 -> B3Propagator.injectingMultiHeaders();
            case B3_SINGLE -> B3Propagator.injectingSingleHeader();
            case W3C -> W3CBaggagePropagator.getInstance();
            // jaeger and unknown are jaeger
            default -> JaegerPropagator.getInstance();
        };
    }

    /**
     * Sampler type definition.
     * Available options are "const", "probabilistic", "ratelimiting" and "remote".
     */
    public enum SamplerType {
        /**
         * Constant sampler always makes the same decision for all traces.
         * It either samples all traces {@code 1} or none of them {@code 0}.
         */
        CONSTANT("const"),
        /**
         * Ratio of the requests to sample, double value.
         */
        RATIO("ratio");
        private final String config;

        SamplerType(String config) {
            this.config = config;
        }

        static SamplerType create(String value) {
            for (SamplerType sampler : SamplerType.values()) {
                if (sampler.config().equals(value)) {
                    return sampler;
                }
            }
            throw new IllegalStateException("SamplerType " + value + " is not supported");
        }

        String config() {
            return config;
        }
    }

    /**
     * Supported Jaeger trace context propagation formats.
     */
    public enum PropagationFormat {
        /**
         * The Zipkin B3 trace context propagation format using multiple headers.
         */
        B3,
        /**
         * B3 trace context propagation using a single header.
         */
        B3_SINGLE,
        /**
         * The Jaeger trace context propagation format.
         */
        JAEGER,
        /**
         * The W3C trace context propagation format.
         */
        W3C
    }


    /**
     * Span Processor type. Batch is default for production.
     */
    public enum SpanProcessorType {
        /**
         * Simple Span Processor.
         */
        SIMPLE,
        /**
         * Batch Span Processor.
         */
        BATCH
    }
}
