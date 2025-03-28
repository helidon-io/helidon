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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.helidon.common.config.Config;
import io.helidon.config.metadata.Configured;
import io.helidon.config.metadata.ConfiguredOption;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.TracerBuilder;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

/**
 * Prepares OpenTelemetry using the Helidon {@code tracing} configuration abstractions.
 * <p>
 * OpenTelemetry comprises multiple technologies (e.g., tracing, logging, metrics). The settings which this class manages apply
 * primarily to tracing, although the {@link io.opentelemetry.api.OpenTelemetry} object applies implicitly to all OTel
 * technologies in the JVM. Also, OTel applies propagators on an OTel-wide basis, which means this class sets the propagators on
 * an OTel-wide basis even though the settings as represented in Helidon config are nested under {@code tracing}.
 * <p>
 * For OpenTelemetry tracing, Helidon supports the following OpenTelemetry elements via configuration.
 * <p>
 * <ul>
 *     <li>Sampler
 *     <p>
 *     OpenTelemetry supports one sampler instance for tracing. Helidon builds a sampler from configuration using
 *     the {@code sampler-type} and {@code sampler-param} settings (defaulting as needed).
 *     </li>
 *     <li>Span exporters
 *     <p>
 *     Helidon creates an OpenTelemetry span exporter for each one configured in the {@code span-exporters} named list. Helidon
 *     also builds a convenience span exporter with name {@code @default}, by default of type {@code otlp}, based on span exporter
 *     settings such as {@code host}, {@code port}, {@code exporterProtocol}, etc. if at least one of those
 *     settings appears at the top level of the {@code tracing} config node.
 *     <p>
 *     (Note that the neutral Helidon API uses the term "collector" in method names that assign OpenTelemetry exporter settings.)
 *     </li>
 *     <li>Span processors
 *     <p>
 *     OpenTelemetry allows multiple span processors to be active concurrently. Each span processor configured in the
 *     {@code span-processors} list specifies by name which configured span exporter it uses. Helidon also builds a
 *     convenience span processor based on span processor settings such as {@code span-processor-type}, {@code schedule-delay},
 *     {@code max-queue-size}, and {@code max-export-batch-size}, if at least one of those settings appears at the top level of
 *     the {@code tracing} config node.
 *     <p>
 *     In OpenTelemetry each span processor uses exactly one span exporter. If there is a top-level convenience span processor it
 *     uses the span exporter with name {@code @default}. If configuration does not specify a default exporter
 *     explicitly Helidon automatically provides an {@code otlp} span exporter with default settings and assigns that for use
 *     by the default span processor.
 *     </li>
 *     <li>Propagators
 *     <p>
 *     OpenTelemetry can propagate span information to other processes in many ways and permits multiple of them to be
 *     active. By default, Helidon prepares propagation with the W3C headers using the {@code tracecontext} and {@code baggage}
 *     propagation formats. The application can override the default using configuration or invoking the
 *     {@link #addPropagation(ContextPropagation)} method.
 *     Note that invoking {@code addPropagation} causes Helidon to discard the defaults rather than adding to them.
 *     (This is the same way propagation config works with the Jaeger tracing integration.)
 *     </li>
 * </ul>
 * <h3>Top-level convenience exporter and processor</h3>
 * <p>
 * Helidon creates the top-level convenience span exporter if:
 * <ul>
 *     <li>any top-level setting related to the exporter has been assigned via config or programmatically
 *     (e.g., {@code exporter-type} or {@code exporter-protocol}), or </li>
 *     <li>no span exporters were declared under {@code span-exporters} or added programmatically using {@code addSpanExporter}.
 * </ul>
 * Helidon applies suitable defaults for unassigned settings.
 * <p>
 * Similarly, Helidon creates the top-level convenience span processor if:
 * <ul>
 *     <li>any top-level setting related to the processor has been assigned via config or programmatically
 *     (e.g., processor-type </li>
 * </ul>
 * As a result, if the user specifies <em>no</em> information related to the span exporter or span processor, Helidon creates
 * a default instance for each and uses them in preparing the {@code OpenTelemetry} instance.
 * <h3>Overriding the Configured OpenTelemetry Instance</h3>
 * <p>
 * Applications can also prepare the {@code OpenTelemetry} object themselves and invoke
 * the builder's {@link #openTelemetry(io.opentelemetry.api.OpenTelemetry)} method.
 * <p>
 * See <a href="https://opentelemetry.io/docs/languages/java/configuration/">OpenTelemetry Configuration</a> for more
 * information.
 * <h3>Interaction with OpenTelemetry Autoconfiguration</h3>
 * <p>
 * For backward compatibility, this builder functions as a no-op if the OpenTelemetry autoconfiguration property
 * {@code otel.java.global-autoconfigure.enabled} is set, in which case OpenTelemetry itself users the OTel system properties
 * or environment variables for configuration settings.
 */
@Configured(prefix = "tracing", root = true, description = "OpenTelemetry tracer configuration.")
public class OpenTelemetryTracerBuilder implements TracerBuilder<OpenTelemetryTracerBuilder> {

    static final boolean DEFAULT_ENABLED = true;
    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryTracerBuilder.class.getName());

    private final Map<String, SpanExporterConfiguration> spanExporterConfigs = new HashMap<>();
    private final Map<String, SpanExporter> spanExporters = new HashMap<>();

    private final List<SpanProcessorConfiguration> spanProcessorConfigs = new ArrayList<>();
    private final List<SpanProcessor> spanProcessors = new ArrayList<>();

    private final Map<String, String> tags = new HashMap<>();

    // Propagation formats explicitly set on the builder by the app or inferred from config
    private final EnumSet<ContextPropagation> contextPropagations = EnumSet.copyOf(ContextPropagation.DEFAULT);

    private boolean global = true;
    private boolean enabled = DEFAULT_ENABLED;
    private OpenTelemetry ot;
    private String serviceName;

    private SamplerType samplerType;
    private Number samplerParam;

    // Settings related to the possible top-level span exporter.
    private final Map<String, String> headers = new HashMap<>();

    private ExporterType topLevelExporterType;

    // At most one of the following two fields will be active. If the app invokes spanExporterConfig then
    // that clears the builder. Similarly, if the build method detects any individual top-level span exporter setting then it
    // uses an ad hoc builder for the top-level exporter and clears any previously-assigned top-level span exporter config.
    private SpanExporterConfiguration.Builder<?, ?> topLevelSpanExporterConfigBuilder;
    private SpanExporterConfiguration topLevelSpanExporterConfig;

    private byte[] privateKey;
    private byte[] certificate;
    private byte[] trustedCertificate;

    // Collector protocol (scheme)
    private String collectorProtocol;

    private OtlpExporterProtocol otlpExporterProtocol = OtlpExporterProtocol.DEFAULT;
    private Integer exporterPort;
    private String exporterHost;
    private String exporterPath;
    private Duration exporterTimeout;
    private String compression;

    // End of settings for the optional top-level span exporter.

    // Settings for the optional top-level span processor.
    private SpanProcessorType topLevelSpanProcessorType = SpanProcessorType.DEFAULT;

    private Duration scheduleDelay;
    private Integer maxQueueSize;
    private Integer maxExportBatchSize;

    // End of settings for top-level span processor.

    // We need to track if the app changes the propagations so we know whether to use the default value or not.
    private boolean isPropagationFormatsDefaulted = true;

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

                prepareSpanExporters();

                prepareSpanProcessors();

                AttributesBuilder attributesBuilder = Attributes.builder()
                        .put(ResourceAttributes.SERVICE_NAME, serviceName);

                tags.forEach(attributesBuilder::put);
                Resource otelResource = Resource.create(attributesBuilder.build());

                SdkTracerProviderBuilder sdkTracerProviderBuilder = SdkTracerProvider.builder()
                        .setSampler(configuredSampler())
                        .setResource(otelResource);
                spanProcessors.forEach(sdkTracerProviderBuilder::addSpanProcessor);

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
     * their own to the builder, and Helidon uses that to prepare the {@code OpenTelemetry}
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

    /**
     * Configured span exporters, indexed by name.
     *
     * @param spanExporters name-indexed span exporters
     * @return updated builder
     */
    @ConfiguredOption(kind = ConfiguredOption.Kind.MAP)
    public OpenTelemetryTracerBuilder spanExporters(Map<String, SpanExporterConfiguration> spanExporters) {
        this.spanExporterConfigs.clear();
        this.spanExporterConfigs.putAll(spanExporters);
        return this;
    }

    /**
     * Records a fully prepared {@link io.opentelemetry.sdk.trace.export.SpanExporter} for Helidon to add to OpenTelemetry.
     *
     * @param exporterName name by which the span exporter is identified
     * @param spanExporter span exported to record
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder addSpanExporter(String exporterName, SpanExporter spanExporter) {
        verifyUniqueSpanExporterName(exporterName);
        spanExporters.put(exporterName, spanExporter);
        return this;
    }

    /**
     * Records settings for a span exporter for Helidon to build and add to OpenTelemetry.
     *
     * @param exporterName       local name for the exporter
     * @param spanExporterConfig the span exporter
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder addSpanExporter(String exporterName, SpanExporterConfiguration spanExporterConfig) {
        verifyUniqueSpanExporterName(exporterName);
        spanExporterConfigs.put(exporterName, spanExporterConfig);
        return this;
    }

    /**
     * Settings for span processors for Helidon to build and add to OpenTelemetry.
     *
     * @param spanProcessorConfigs span processor configurations
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder spanProcessors(List<SpanProcessorConfiguration> spanProcessorConfigs) {
        this.spanProcessorConfigs.clear();
        this.spanProcessorConfigs.addAll(spanProcessorConfigs);
        return this;
    }

    /**
     * Records a fully-prepared {@link io.opentelemetry.sdk.trace.SpanProcessor} for Helidon to add to OpenTelemetry.
     *
     * @param spanProcessor span processor to add
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder addSpanProcessor(SpanProcessor spanProcessor) {
        spanProcessors.add(spanProcessor);
        return this;
    }

    /**
     * Adds settings for a span processor for Helidon to build and add to OpenTelemetry.
     *
     * @param spanProcessorConfig span processor builder to add
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder addSpanProcessor(SpanProcessorConfiguration spanProcessorConfig) {
        spanProcessorConfigs.add(spanProcessorConfig);
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
     * @param otlpExporterProtocol {@link OtlpExporterProtocol} to use in connecting to the back end
     * @return updated builder
     * @see #collectorProtocol(String) for specifying http/https in the connection URL
     */
    public OpenTelemetryTracerBuilder exporterProtocol(OtlpExporterProtocol otlpExporterProtocol) {
        this.otlpExporterProtocol = otlpExporterProtocol;
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

        // Related to OpenTelemetry in general (not just tracing).
        tracingConfig.get("enabled").asBoolean().ifPresent(this::enabled);
        tracingConfig.get("service").asString().ifPresent(this::serviceName);
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

        // Top-level convenience span exporter settings.
        tracingConfig.get("span-exporter-type").as(ExporterType.class).ifPresent(this::spanExporterType);
        tracingConfig.get("headers").asMap().ifPresent(this::headers);
        tracingConfig.get("protocol").asString().ifPresent(this::collectorProtocol);
        tracingConfig.get("host").asString().ifPresent(this::collectorHost);
        tracingConfig.get("port").asInt().ifPresent(this::collectorPort);
        tracingConfig.get("path").asString().ifPresent(this::collectorPath);
        tracingConfig.get("exporter-timeout").as(Duration.class).ifPresent(this::exporterTimeout);
        tracingConfig.get("compression").asString().ifPresent(this::compression);
        tracingConfig.get("exporter-protocol").map(OtlpExporterProtocol::from).ifPresent(this::exporterProtocol);
        tracingConfig.get("private-key-pem").map(io.helidon.common.configurable.Resource::create).ifPresent(this::privateKey);
        tracingConfig.get("client-cert-pem").map(io.helidon.common.configurable.Resource::create)
                .ifPresent(this::clientCertificate);
        tracingConfig.get("trusted-cert-pem").map(io.helidon.common.configurable.Resource::create)
                .ifPresent(this::trustedCertificates);

        // Explicit span exporters.
        tracingConfig.get("span-exporters").asNodeList().ifPresent(this::addSpanExporters);

        // Top-level convenience span processor settings.
        tracingConfig.get("span-processor-type").asString().as(SpanProcessorType::from).ifPresent(this::spanProcessorType);
        tracingConfig.get("schedule-delay").as(Duration.class).ifPresent(this::scheduleDelay);
        tracingConfig.get("max-queue-size").asInt().ifPresent(this::maxQueueSize);
        tracingConfig.get("max-export-batch-size").asInt().ifPresent(this::maxExportBatchSize);

        // Explicit span processors.
        tracingConfig.get("span-processors").asNodeList().ifPresent(this::addSpanProcessors);

        // Sampler type.
        tracingConfig.get("sampler-type").asString().as(SamplerType::from).ifPresent(this::samplerType);
        tracingConfig.get("sampler-param").as(Number.class).ifPresent(this::samplerParam);

        // Propagations
        tracingConfig.get("propagation").asNodeList().ifPresent(nodes -> nodes.stream()
                .map(ContextPropagation::from)
                .forEach(this::addPropagation));

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
     * @param format {@link ContextPropagation} to add
     * @return updated builder
     */
    @ConfiguredOption(key = "propagation",
                      kind = ConfiguredOption.Kind.LIST,
                      type = ContextPropagation.class,
                      value = ContextPropagation.DEFAULT_STRING)
    public OpenTelemetryTracerBuilder addPropagation(ContextPropagation format) {
        Objects.requireNonNull(format);
        if (isPropagationFormatsDefaulted) {
            isPropagationFormatsDefaulted = false;
            contextPropagations.clear();
        }
        contextPropagations.add(format);
        return this;
    }

    /**
     * Exporter type for the implicit top-level span exporter.
     *
     * @param exporterType the {@link ExporterType} for the implicit top-level exporter
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder spanExporterType(ExporterType exporterType) {
        Objects.requireNonNull(exporterType, "span-exporter-type");
        this.topLevelExporterType = exporterType;
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
     * @param samplerType {@link SamplerType} to use
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
        this.trustedCertificate = resource.bytes();
        return this;
    }

    /**
     * Type of {@link io.opentelemetry.sdk.trace.SpanProcessor} Helidon should create automatically. Allowed values also
     * include the OpenTelemetry-style values ({@code }
     *
     * @param spanProcessorType {@link SpanProcessorType}
     * @return updated builder
     */
    @ConfiguredOption(SpanProcessorType.DEFAULT_NAME)
    public OpenTelemetryTracerBuilder spanProcessorType(SpanProcessorType spanProcessorType) {
        this.topLevelSpanProcessorType = spanProcessorType;
        return this;
    }

    @Override
    public <B> B unwrap(Class<B> builderClass) {
        if (builderClass.isAssignableFrom(getClass())) {
            return builderClass.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap " + builderClass + " from OpenTelmetry tracer builder.");
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

    private void prepareSpanExporters() {
        // Add to any programmatically-provided span exporters those for which we have configurations.
        spanExporterConfigs.forEach((name,
                                     config) -> spanExporters.put(name, config.spanExporter()));

        // Add any top-level span exporter that is configured as the convenience exporter or if we have no
        // other exporter set up.
        if (anyTopLevelExporterSettingsAssigned() || spanExporters.isEmpty()) {
            spanExporters.put("@default", convenienceSpanExporterConfig().spanExporter());
        }
    }

    private void prepareSpanProcessors() {
        // Build configured span processors, filling in the referenced span exporter by name if it was not explicitly set.
        spanProcessorConfigs.forEach(spanProcessorConfig -> {
            var spanExporter = spanExporters.get(spanProcessorConfig.exporterName());
            if (spanExporter == null) {
                var exporterNameToUse = Objects.requireNonNullElse(spanProcessorConfig.exporterName(), "@#default");
                if (!spanExporters.containsKey(exporterNameToUse)) {
                    throw new IllegalArgumentException("Unknown exporter name: " + exporterNameToUse);
                }
            }
            spanProcessors.add(spanProcessorConfig.spanProcessor(spanExporter));
        });

        // Add the top-level convenience span processor if any top-level setting was assigned or if
        // no explicit processors were under span-processors.
        if (anyTopLevelProcessorSettingsAssigned() || spanProcessorConfigs.isEmpty()) {
            spanProcessorConfigs.add(convenienceSpanProcessorConfig());
        }
    }

    private SpanExporterConfiguration convenienceSpanExporterConfig() {
        ExporterType convenienceExporterType = Objects.requireNonNullElse(topLevelExporterType, ExporterType.DEFAULT);
        SpanExporterConfiguration.Builder<?, ?> builder = SpanExporterConfiguration.builder(convenienceExporterType);
        if (builder instanceof OtlpSpanExporterConfiguration.Builder<?, ?> otlpBuilder) {
            headers.forEach(otlpBuilder::addHeader);
            if (certificate != null) {
                otlpBuilder.clientCertificate(certificate);
            }
            if (trustedCertificate != null) {
                otlpBuilder.trustedCertificate(trustedCertificate);
            }
            if (privateKey != null) {
                otlpBuilder.privateKey(privateKey);
            }
            if (collectorProtocol != null) {
                otlpBuilder.collectorProtocol(collectorProtocol);
            }
            otlpBuilder.exporterProtocol(otlpExporterProtocol);
            if (exporterPort != null) {
                otlpBuilder.collectorPort(exporterPort);
            }
            if (exporterHost != null) {
                otlpBuilder.collectorHost(exporterHost);
            }
            if (exporterPath != null) {
                otlpBuilder.collectorPath(exporterPath);
            }
            if (exporterTimeout != null) {
                otlpBuilder.exporterTimeout(exporterTimeout);
            }
            if (compression != null) {
                otlpBuilder.compression(compression);
            }
        }
        return builder.build();
    }

    private SpanProcessorConfiguration convenienceSpanProcessorConfig() {
        SpanProcessorType convenienceExporterType = Objects.requireNonNullElse(topLevelSpanProcessorType,
                                                                               SpanProcessorType.DEFAULT);
        SpanProcessorConfiguration.Builder<?, ?> builder = SpanProcessorConfiguration.builder(convenienceExporterType);
        if (builder instanceof BatchSpanProcessorConfiguration.Builder batchBuilder) {
            if (scheduleDelay != null) {
                batchBuilder.scheduleDelay(scheduleDelay);
            }
            if (maxQueueSize != null) {
                batchBuilder.maxQueueSize(maxQueueSize);
            }
            if (maxExportBatchSize != null) {
                batchBuilder.maxExportBatchSize(maxExportBatchSize);
            }
            if (exporterTimeout != null) {
                batchBuilder.exporterTimeout(exporterTimeout);
            }
            batchBuilder.exporterName("@default");
        }
        return builder.build();
    }

   private boolean anyTopLevelExporterSettingsAssigned() {
        return exporterPort != null
                || exporterHost != null
                || exporterPath != null
                || exporterTimeout != null
                || !headers.isEmpty()
                || privateKey != null
                || certificate != null
                || trustedCertificate != null
                || collectorProtocol != null
                || compression != null;
    }

    private boolean anyTopLevelProcessorSettingsAssigned() {
        return scheduleDelay != null
                || maxQueueSize != null
                || maxExportBatchSize != null;
    }

    private void verifyUniqueSpanExporterName(String exporterName) {
        if (spanExporters.containsKey(exporterName)) {
            throw new IllegalArgumentException("Exporter " + exporterName + " already exists in programmatically-added "
                                                       + "exporters");
        }
        if (spanExporterConfigs.containsKey(exporterName)) {
            throw new IllegalArgumentException("Exporter " + exporterName + " already exists in the configured span exporters");
        }
    }

    private void addSpanExporters(List<Config> spanExporterConfigs) {
        spanExporterConfigs.forEach(spanExporterConfig -> {
            String exporterName = spanExporterConfig.get("name").asString().orElse("@default");
            addSpanExporter(exporterName,
                            SpanExporterConfiguration.create(spanExporterConfig));
        });
    }

    private void addSpanProcessors(List<Config> spanProcessorConfigs) {
        spanProcessorConfigs.forEach(spanProcessorConfig -> {
            addSpanProcessor(SpanProcessorConfiguration.builder(spanProcessorConfig).build());
        });
    }

    private List<String> reasonsForIgnoringHelidonConfig(Config rootConfig) {
        List<String> reasons = otelReasonsForUsingAutoConfig();
        if (HelidonOpenTelemetry.AgentDetector.isAgentPresent(rootConfig)) {
            reasons.add("OpenTelemetry agent is detected");
        }

        return reasons;
    }

    private Iterable<TextMapPropagator> propagators() {
        return contextPropagations.stream()
                .map(ContextPropagation::propagator)
                .toList();
    }

    private Sampler configuredSampler() {
        if (samplerType == null) {
            samplerType = SamplerType.DEFAULT;
        }
        if (samplerParam == null) {
            samplerParam = 1D;
        }

        return switch (samplerType) {
            case ALWAYS_ON -> Sampler.alwaysOn();
            case ALWAYS_OFF -> Sampler.alwaysOff();
            case TRACE_ID_RATIO -> Sampler.traceIdRatioBased(samplerParam.doubleValue());
            case PARENT_BASED_ALWAYS_OFF -> Sampler.parentBased(Sampler.alwaysOff());
            case PARENT_BASED_ALWAYS_ON -> Sampler.parentBased(Sampler.alwaysOn());
            case PARENT_BASED_TRACE_ID_RATIO -> Sampler.parentBased(Sampler.traceIdRatioBased(samplerParam.doubleValue()));
        };
    }

}
