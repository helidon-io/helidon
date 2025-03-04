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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.extension.trace.propagation.B3Propagator;
import io.opentelemetry.extension.trace.propagation.JaegerPropagator;
import io.opentelemetry.extension.trace.propagation.OtTracePropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

/**
 * Prepares OpenTelemetry using the Helidon tracing config abstractions.
 * <p>
 * OpenTelemetry comprises multiple technologies (e.g., tracing, logging, metrics). The settings which this class manages apply
 * primarily to tracing, although the {@link io.opentelemetry.api.OpenTelemetry} object applies implicitly to all OTel
 * technologies in the JVM. Also, OTel applies propagators on an OTel-wide basis, which means this class sets the propagators on
 * an OTel-wide basis even though the settings as represented in Helidon config are nested under {@code tracing}.
 * <p>
 * For OpenTelemetry tracing, Helidon supports the following OpenTelemetry elements.
 * <p>
 * <ul>
 *     <li>Sampler
 *     <p>
 *     OpenTelemetry supports one sampler instance for tracing. Helidon builds a sampler from configuration using
 *     the {@code sampler-type} and {@code sampler-param} settings.
 *     </li>
 *     <li>Span processors
 *     <p>
 *     OpenTelemetry allows multiple span processors to be active concurrently. Helidon builds a single span processor based on
 *     configuration.
 *     </li>
 *     <li>Span exporter
 *     <p>
 *     In OpenTelemetry each span processor uses exactly one span exporter. Helidon builds one span exporter (currently only
 *     of type {@code otlp}) from configuration to use with the span processor it builds from config as described as above.
 *     Note that the neutral Helidon API uses the term "collector" in method names that assign OpenTelemetry exporter settings.
 *     </li>
 *     <li>Propagators
 *     <p>
 *     OpenTelemetry can propagate span information to other processes in many ways and permits multiple of them to be
 *     active. By default, Helidon prepares propagation with the W3C headers using the {@code tracecontext} and {@code baggage}
 *     propagation formats. The application can override the default using configuration or invoking the
 *     {@link #addPropagation(io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.PropagationFormat)} method.
 *     Note that invoking {@code addPropagation} causes Helidon to discard the defaults rather than adding to them.
 *     (This is the same way propagation config works with the Jaeger tracing integration.)
 *     </li>
 * </ul>
 * <p>
 * Applications that need multiple span processors or exporters should prepare the {@code OpenTelemetry} themselves and invoke
 * the builder's {@link #openTelemetry(io.opentelemetry.api.OpenTelemetry)} method.
 * <p>
 * See <a href="https://opentelemetry.io/docs/languages/java/configuration/">OpenTelemetry Configuration</a> for more
 * information.
 * <p>
 * For backward compatibility, this builder functions as a no-op if the OpenTelemetry auto-configuration property
 * {@code otel.java.global-autoconfigure.enabled} is set, in which case OpenTelemetry itself refers to the OTel system properties
 * or environment variables for configuration settings.
 */
@Configured(prefix = "tracing", root = true, description = "OpenTelemetry tracer configuration.")
public class OpenTelemetryTracerBuilder implements TracerBuilder<OpenTelemetryTracerBuilder> {

    static final boolean DEFAULT_ENABLED = true;
    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryTracerBuilder.class.getName());

    private final Map<String, String> headers = new HashMap<>();
    private final EnumSet<PropagationFormat> propagationFormats = EnumSet.copyOf(PropagationFormat.DEFAULT);
    private final Map<String, String> tags = new HashMap<>();
    private final List<SpanExporter> adHocExporters = new ArrayList<>(); // primarily for testing
    private boolean global = true;
    private boolean enabled = DEFAULT_ENABLED;
    private OpenTelemetry ot;
    private String serviceName;

    private byte[] privateKey;
    private byte[] certificate;
    private byte[] trustedCertificates;

    // Collector protocol (scheme)
    private String collectorProtocol = "http";

    private ExporterProtocol exporterProtocol = ExporterProtocol.create(ExporterProtocol.DEFAULT_STRING);
    private Integer exporterPort = exporterProtocol.defaultPort;
    private String exporterHost = "localhost";
    private String exporterPath = "v1/traces";
    private Duration exporterTimeout;
    private SpanProcessorType spanProcessorType = SpanProcessorType.create(SpanProcessorType.DEFAULT_STRING);
    private SamplerType samplerType = SamplerType.create(SamplerType.DEFAULT_STRING);
    private Number samplerParam;

    private Duration scheduleDelay;
    private Integer maxQueueSize;
    private Integer maxExportBatchSize;

    // We need to track if the app changes the propagations so we know whether to use the default value or not.
    private boolean isPropagationFormatsDefaulted = true;

    private String compression;

    private Config tracingConfig;

    @Override
    public Tracer build() {
        Tracer result;

        List<String> reasonsForIgnoringConfig = reasonsForIgnoringHelidonConfig(tracingConfig);
        if (!reasonsForIgnoringConfig.isEmpty()) {

            LOGGER.log(System.Logger.Level.INFO, getClass().getSimpleName() + "#build() invoked but is ignored: {}",
                       reasonsForIgnoringConfig);
            result = OpenTelemetryTracerProvider.globalTracer();

        } else {
            if (enabled) {
                if (serviceName == null) {
                    throw new IllegalArgumentException(
                            "Tracing configuration must at least contain the 'service' key ('tracing.service` in MP) with "
                                    + "service name");
                }

                AttributesBuilder attributesBuilder = Attributes.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName);

                tags.forEach(attributesBuilder::put);
                Resource otelResource = Resource.create(attributesBuilder.build());

                var configuredSpanExporter = configuredSpanExporter();

                SdkTracerProviderBuilder sdkTracerProviderBuilder = SdkTracerProvider.builder()
                        .setSampler(configuredSampler())
                        .setResource(otelResource)
                        .addSpanProcessor(configuredSpanProcessor(configuredSpanExporter));

                // Create an additional copy of the config-based span processor for each ad hoc exporter.
                adHocExporters.stream()
                        .map(this::configuredSpanProcessor)
                        .forEach(sdkTracerProviderBuilder::addSpanProcessor);

                if (ot == null) {
                    ot = OpenTelemetrySdk.builder()
                            .setTracerProvider(sdkTracerProviderBuilder.build())
                            .setPropagators(ContextPropagators.create(TextMapPropagator.composite(propagators())))
                            .build();
                }

                result = HelidonOpenTelemetry.create(ot, ot.getTracer(this.serviceName), Map.of());

                if (global) {
                    GlobalOpenTelemetry.set(ot);
                }

                LOGGER.log(System.Logger.Level.INFO,
                           () -> "Creating OpenTelemetry tracer for '"
                                   + this.serviceName
                                   + "' configured with "
                                   + collectorProtocol
                                   + "://" + exporterHost
                                   + ":" + exporterPort);

            } else {
                LOGGER.log(System.Logger.Level.INFO, "OpenTelemetry Tracer is explicitly disabled.");
                result = Tracer.noOp();
            }

            if (global) {
                OpenTelemetryTracerProvider.globalTracer(result);
            }
        }

        return result;
    }

    /**
     * {@link OpenTelemetry} instance to use in preparing OpenTelemetry
     * <p>
     * Developers who want more control over the {@link io.opentelemetry.api.OpenTelemetry} instance can provide
     * their own to the builder, and Helidon uses that instead of configuration to prepare the {@code OpenTelemetry}
     * object. Supplying an explicit {@code OpenTelemetry} object also causes Helidon to ignore configuration that it would
     * otherwise use to create the span processor(s), propagators, and sampler.
     *
     * @param ot the {@code OpenTelemetry} instance to use
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder openTelemetry(OpenTelemetry ot) {
        this.ot = ot;
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder serviceName(String name) {
        this.serviceName = name;
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder collectorProtocol(String protocol) {
        this.collectorProtocol = protocol;
        return this;
    }

    /**
     * Protocol (e.g., {@code grpc} vs. {@code http/protobuf}) for the exporter.
     *
     * @param exporterProtocol {@link io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.ExporterProtocol}
     *                         to use in connecting to the back end.
     * @return updated builder
     * @see #collectorProtocol(String) for specifying http/https
     */
    public OpenTelemetryTracerBuilder exporterProtocol(ExporterProtocol exporterProtocol) {
        this.exporterProtocol = exporterProtocol;
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder collectorPort(int port) {
        exporterPort = port;
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder collectorHost(String host) {
        exporterHost = host;
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder collectorPath(String path) {
        exporterPath = path;
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder addTracerTag(String key, String value) {
        tags.put(key, value);
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder addTracerTag(String key, Number value) {
        tags.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder addTracerTag(String key, boolean value) {
        tags.put(key, String.valueOf(value));
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder config(Config tracingConfig) {
        this.tracingConfig = tracingConfig;
        tracingConfig.get("enabled").asBoolean().ifPresent(this::enabled);
        tracingConfig.get("service").asString().ifPresent(this::serviceName);
        tracingConfig.get("protocol").asString().ifPresent(this::collectorProtocol);
        tracingConfig.get("host").asString().ifPresent(this::collectorHost);
        tracingConfig.get("port").asInt().ifPresent(this::collectorPort);
        tracingConfig.get("path").asString().ifPresent(this::collectorPath);
        tracingConfig.get("sampler-type").asString().as(SamplerType::create).ifPresent(this::samplerType);
        tracingConfig.get("sampler-param").as(Number.class).ifPresent(this::samplerParam);
        tracingConfig.get("private-key-pem").map(io.helidon.common.configurable.Resource::create).ifPresent(this::privateKey);
        tracingConfig.get("client-cert-pem").map(io.helidon.common.configurable.Resource::create)
                .ifPresent(this::clientCertificate);
        tracingConfig.get("trusted-cert-pem").map(io.helidon.common.configurable.Resource::create)
                .ifPresent(this::trustedCertificates);
        tracingConfig.get("propagation").asList(String.class)
                .ifPresent(strings -> strings.stream()
                        .map(PropagationFormat::create)
                        .forEach(this::addPropagation));

        tracingConfig.get("tags").detach()
                .asMap()
                .orElseGet(Map::of)
                .forEach(this::addTracerTag);

        tracingConfig.get("boolean-tags")
                .asNodeList()
                .ifPresent(nodes -> {
                    nodes.forEach(node -> {
                        this.addTracerTag(node.key().name(), node.asBoolean().get());
                    });
                });

        tracingConfig.get("int-tags")
                .asNodeList()
                .ifPresent(nodes -> {
                    nodes.forEach(node -> {
                        this.addTracerTag(node.key().name(), node.asInt().get());
                    });
                });
        tracingConfig.get("global").asBoolean().ifPresent(this::registerGlobal);

        tracingConfig.get("span-processor-type").asString().as(SpanProcessorType::create).ifPresent(this::spanProcessorType);
        tracingConfig.get("exporter-protocol").asString().as(ExporterProtocol::create).ifPresent(this::exporterProtocol);
        tracingConfig.get("exporter-timeout").as(Duration.class).ifPresent(this::exporterTimeout);
        tracingConfig.get("compression").asString().ifPresent(this::compression);
        tracingConfig.get("schedule-delay").as(Duration.class).ifPresent(this::scheduleDelay);
        tracingConfig.get("max-queue-size").asInt().ifPresent(this::maxQueueSize);
        tracingConfig.get("max-export-batch-size").asInt().ifPresent(this::maxExportBatchSize);

        tracingConfig.get("headers").asMap().ifPresent(this::headers);

        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder enabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder registerGlobal(boolean global) {
        this.global = global;
        return this;
    }

    /**
     * Propagation format to add.
     *
     * @param format {@link io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.PropagationFormat} to add
     * @return updated builder
     */
    @ConfiguredOption(key = "propagation",
                      kind = ConfiguredOption.Kind.LIST,
                      type = PropagationFormat.class,
                      value = PropagationFormat.DEFAULT_STRING)
    public OpenTelemetryTracerBuilder addPropagation(PropagationFormat format) {
        Objects.requireNonNull(format);
        if (isPropagationFormatsDefaulted) {
            isPropagationFormatsDefaulted = false;
            propagationFormats.clear();
        }
        propagationFormats.add(format);
        return this;
    }

    /**
     * Transmission timeout for the exporter.
     *
     * @param timeout time to wait before an outstanding transmission request is considered failed
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder exporterTimeout(Duration timeout) {
        this.exporterTimeout = timeout;
        return this;
    }

    /**
     * Compression type for exporting data.
     *
     * @param compression type of compression to use for exporting data
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder compression(String compression) {
        this.compression = compression;
        return this;
    }

    /**
     * Name/value pairs for headers to send with all transmitted traces.
     *
     * @param headers headers to send
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder headers(Map<String, String> headers) {
        this.headers.clear();
        this.headers.putAll(headers);
        return this;
    }

    /**
     * Adds a name/value pair as a header to send with all transmitted traces.
     *
     * @param name  header name
     * @param value header value
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder addHeader(String name, String value) {
        headers.put(name, value);
        return this;
    }

    /**
     * Sampler type to use for sampling spans.
     *
     * @param samplerType {@link io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.SamplerType} to use
     * @return updated builder
     */
    @ConfiguredOption(SamplerType.DEFAULT_STRING)
    public OpenTelemetryTracerBuilder samplerType(SamplerType samplerType) {
        this.samplerType = samplerType;
        return this;
    }

    /**
     * Sampler parameter for influencing the behavior of the selected sampler.
     *
     * @param samplerParam parameter value; meaning varies depending on the specific sample in use
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder samplerParam(Number samplerParam) {
        this.samplerParam = samplerParam;
        return this;
    }

    /**
     * Schedule delay for transmitting exporter data.
     *
     * @param scheduleDelay schedule delay
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder scheduleDelay(Duration scheduleDelay) {
        this.scheduleDelay = scheduleDelay;
        return this;
    }

    /**
     * Maximum queue size for exporting data.
     *
     * @param maxQueueSize maximum queue size for exporting data
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder maxQueueSize(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        return this;
    }

    /**
     * Maximum batch size for exporting data.
     *
     * @param maxExportBatchSize maximum batch size for exporting data
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder maxExportBatchSize(int maxExportBatchSize) {
        this.maxExportBatchSize = maxExportBatchSize;
        return this;
    }

    /**
     * Private key in PEM format.
     *
     * @param resource key resource
     * @return updated builder
     */
    @ConfiguredOption(key = "private-key-pem")
    public OpenTelemetryTracerBuilder privateKey(io.helidon.common.configurable.Resource resource) {
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
    public OpenTelemetryTracerBuilder clientCertificate(io.helidon.common.configurable.Resource resource) {
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
    public OpenTelemetryTracerBuilder trustedCertificates(io.helidon.common.configurable.Resource resource) {
        this.trustedCertificates = resource.bytes();
        return this;
    }

    /**
     * Type of {@link io.opentelemetry.sdk.trace.SpanProcessor} Helidon should create automatically.
     *
     * @param spanProcessorType {@link io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.SpanProcessorType}
     * @return updated builder
     */
    @ConfiguredOption(SpanProcessorType.DEFAULT_STRING)
    public OpenTelemetryTracerBuilder spanProcessorType(SpanProcessorType spanProcessorType) {
        this.spanProcessorType = spanProcessorType;
        return this;
    }

    @Override
    public <B> B unwrap(Class<B> builderClass) {
        if (builderClass.isAssignableFrom(getClass())) {
            return builderClass.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap " + builderClass + " from OpenTelmetry tracer builder.");
    }

    // Primarily for testing
    OpenTelemetryTracerBuilder addExporter(SpanExporter spanExporter) {
        adHocExporters.add(spanExporter);
        return this;
    }

    static List<String> otelReasonsForUsingAutoConfig() {
        List<String> reasons = new ArrayList<>();
        if (Boolean.getBoolean("otel.java.global-autoconfigure.enabled")) {
            reasons.add("OpenTelemetry global autoconfigure is enabled using otel.java.global-autoconfigure.enabled");
        }
        String envvar = System.getenv("OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED");
        if (envvar != null && envvar.equals("true")) {
            reasons.add("OpenTelemetry global autoconfigure is enabled using OTEL_JAVA_GLOBAL_AUTOCONFIGURE_ENABLED");
        }
        return reasons;
    }

    private List<String> reasonsForIgnoringHelidonConfig(Config rootConfig) {
        List<String> reasons = otelReasonsForUsingAutoConfig();
        if (HelidonOpenTelemetry.AgentDetector.isAgentPresent(rootConfig)) {
            reasons.add("OpenTelemetry agent is detected");
        }

        return reasons;
    }

    private SpanProcessor configuredSpanProcessor(SpanExporter exporter) {
        return switch (spanProcessorType) {
            case BATCH -> {
                var builder = BatchSpanProcessor.builder(exporter);
                if (scheduleDelay != null) {
                    builder.setScheduleDelay(scheduleDelay);
                }
                if (maxQueueSize != null) {
                    builder.setMaxQueueSize(maxQueueSize);
                }
                if (maxExportBatchSize != null) {
                    builder.setMaxExportBatchSize(maxExportBatchSize);
                }
                if (exporterTimeout != null) {
                    builder.setExporterTimeout(exporterTimeout);
                }
                yield builder.build();
            }
            case SIMPLE -> SimpleSpanProcessor.create(exporter);
        };
    }

    private SpanExporter configuredSpanExporter() {
        if (exporterPort == null) {
            exporterPort = exporterProtocol.defaultPort;
        }
        // The different exporter implementations do not share a common superclass or interface so we just replicate much of
        // the code.
        return switch (exporterProtocol) {
            case GRPC -> {
                var builder = OtlpGrpcSpanExporter.builder()
                        .setEndpoint(collectorProtocol + "://" + exporterHost + ":" + exporterPort
                                             + (exporterPath == null
                                                        ? ""
                                                        : (exporterPath.charAt(0) != '/'
                                                                   ? "/"
                                                                   : "")
                                                                + exporterPath));
                if (compression != null) {
                    builder.setCompression(compression);
                }
                if (exporterTimeout != null) {
                    builder.setTimeout(exporterTimeout);
                }
                headers.forEach(builder::addHeader);
                if (privateKey != null && certificate != null) {
                    builder.setClientTls(privateKey, certificate);
                }
                if (trustedCertificates != null) {
                    builder.setTrustedCertificates(trustedCertificates);
                }
                yield builder.build();
            }
            case HTTP_PROTOBUF -> {
                var builder = OtlpHttpSpanExporter.builder()
                        .setEndpoint(collectorProtocol + "://" + exporterHost + ":" + exporterPort
                                             + (exporterPath == null ? "" : exporterPath));
                if (compression != null) {
                    builder.setCompression(compression);
                }
                if (exporterTimeout != null) {
                    builder.setTimeout(exporterTimeout);
                }
                headers.forEach(builder::addHeader);
                if (privateKey != null && certificate != null) {
                    builder.setClientTls(privateKey, certificate);
                }
                if (trustedCertificates != null) {
                    builder.setTrustedCertificates(trustedCertificates);
                }
                yield builder.build();
            }
        };
    }

    private Iterable<TextMapPropagator> propagators() {
        return propagationFormats.stream()
                .map(PropagationFormat::propagator)
                .collect(Collectors.toList());
    }

    private Sampler configuredSampler() {
        return switch (samplerType) {
            case ALWAYS_ON -> Sampler.alwaysOn();
            case ALWAYS_OFF -> Sampler.alwaysOff();
            case TRACE_ID_RATIO -> Sampler.traceIdRatioBased(samplerParam.doubleValue());
            case PARENT_BASED_ALWAYS_OFF -> Sampler.parentBased(Sampler.alwaysOff());
            case PARENT_BASED_ALWAYS_ON -> Sampler.parentBased(Sampler.alwaysOn());
            case PARENT_BASED_TRACE_ID_RATIO -> Sampler.parentBased(Sampler.traceIdRatioBased(samplerParam.doubleValue()));
        };
    }

    /**
     * Valid values for OpenTelemetry exporter protocol.
     */
    public enum ExporterProtocol {
        /**
         * grpc OpenTelemetry protocol.
         */
        GRPC("grpc", 4317),

        /**
         * http/protobuf OpenTelemetry protocol.
         */
        HTTP_PROTOBUF("http/protobuf", 4318);

        static final String DEFAULT_STRING = "grpc";
        private final String protocol;
        private final int defaultPort;

        ExporterProtocol(String protocol, int defaultPort) {
            this.protocol = protocol;
            this.defaultPort = defaultPort;
        }

        static ExporterProtocol create(String protocol) {
            for (ExporterProtocol exporterProtocol : ExporterProtocol.values()) {
                if (exporterProtocol.protocol.equals(protocol) || exporterProtocol.name().equals(protocol)) {
                    return exporterProtocol;
                }
            }
            throw new IllegalArgumentException("Unknown exporter protocol: " + protocol + "; expected one of "
                                                       + Arrays.toString(ExporterProtocol.values()));
        }
    }

    /**
     * Known OpenTelemetry trace context propagation formats.
     */
    public enum PropagationFormat {

        /**
         * W3C trace context propagation.
         */
        TRACE_CONTEXT("tracecontext", W3CTraceContextPropagator::getInstance),

        /**
         * W3C baggage propagation.
         */
        BAGGAGE("baggage", W3CBaggagePropagator::getInstance),

        /**
         * Zipkin B3 trace context propagation using a single header.
         */
        B3("b3", B3Propagator::injectingSingleHeader),

        /**
         * Zipkin B3 trace context propagation using multiple headers.
         */
        B3_MULTI("b3multi", B3Propagator::injectingMultiHeaders),

        /**
         * Jaeger trace context propagation format.
         */
        JAEGER("jaeger", JaegerPropagator::getInstance),

        /**
         * OT trace format propagation.
         */
        OT_TRACE("ottrace", OtTracePropagator::getInstance);

        static final String DEFAULT_STRING = "tracecontext,baggage";
        static final EnumSet<PropagationFormat> DEFAULT = EnumSet.of(TRACE_CONTEXT, BAGGAGE);

        private final String format;
        private final Supplier<TextMapPropagator> propagatorSupplier;

        PropagationFormat(String format, Supplier<TextMapPropagator> propagatorSupplier) {
            this.format = format;
            this.propagatorSupplier = propagatorSupplier;
        }

        static PropagationFormat create(String value) {
            for (PropagationFormat propagationFormat : PropagationFormat.values()) {
                if (propagationFormat.format.equals(value) || propagationFormat.name().equals(value)) {
                    return propagationFormat;
                }
            }
            throw new IllegalArgumentException("Unknown propagation format: "
                                                       + value
                                                       + "; expected one or more of "
                                                       + Arrays.toString(PropagationFormat.values()));
        }

        TextMapPropagator propagator() {
            return propagatorSupplier.get();
        }
    }

    /**
     * Sampler types valid for OpenTelemetry tracing.
     * <p>
     * This enum intentionally omits {@code jaeger-remote} as that requires an additional library.
     * Users who want to use that sampler can add the dependency themselves and prepare the OpenTelemetry
     * objects explicitly rather than using this builder.
     * <p>
     * Helidon recognizes the string values as documented in the OpenTelemetry documentation
     * <a href="https://opentelemetry.io/docs/languages/java/configuration/#properties-traces">Properties: traces; Properties
     * for sampler</a>.
     */
    public enum SamplerType {
        /**
         * Always on sampler.
         */
        ALWAYS_ON("always_on"),

        /**
         * Always off sampler.
         */
        ALWAYS_OFF("always_off"),

        /**
         * Trace ID ratio-based sampler.
         */
        TRACE_ID_RATIO("traceidratio"),

        /**
         * Parent-based always-on sampler.
         */
        PARENT_BASED_ALWAYS_ON("parentbased_always_on"),

        /**
         * Parent-based always-off sampler.
         */
        PARENT_BASED_ALWAYS_OFF("parentbased_always_off"),

        /**
         * Parent-based trace ID ration-based sampler.
         */
        PARENT_BASED_TRACE_ID_RATIO("parentbased_traceidratio");

        static final String DEFAULT_STRING = "parentbased_always_on";
        private final String config;

        SamplerType(String config) {
            this.config = config;
        }

        static SamplerType create(String value) {
            for (SamplerType samplerType : SamplerType.values()) {
                if (samplerType.config.equals(value) || samplerType.name().equals(value)) {
                    return samplerType;
                }
            }
            throw new IllegalArgumentException("Unknown sample type: " + value + "; expected one of "
                                                       + Arrays.toString(SamplerType.values()));
        }
    }

    /**
     * Span Processor type. Batch is default for production.
     */
    public enum SpanProcessorType {
        /**
         * Simple Span Processor.
         */
        SIMPLE("simple"),
        /**
         * Batch Span Processor.
         */
        BATCH("batch");

        private final String processorType;

        SpanProcessorType(String processorType) {
            this.processorType = processorType;
        }

        static final String DEFAULT_STRING = "batch";
        static SpanProcessorType create(String value) {
            for (SpanProcessorType spanProcessorType : SpanProcessorType.values()) {
                if (spanProcessorType.processorType.equals(value) || spanProcessorType.name().equals(value)) {
                    return spanProcessorType;
                }
            }
            throw new IllegalArgumentException("Unknown span processor type: " + value + "; expected one of "
                                                       + Arrays.toString(SpanProcessorType.values()));

        }
    }
}
