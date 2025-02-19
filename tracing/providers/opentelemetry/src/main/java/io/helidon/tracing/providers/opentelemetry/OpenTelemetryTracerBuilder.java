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
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;

/**
 * Prepares OpenTelemetry settings using the Helidon tracing config abstractions and developer-provided
 * OpenTelemetry objects.
 * <p>
 * OpenTelemetry comprises multiple technologies (tracing, logging, metrics). The settings described here apply primarily to
 * tracing, although the `OpenTelemetry` object which this class creates applies implicitly to all OTel technologies. Also, OTel
 * specifies propagators on an OTel-wide basis, which means this Helidon integration with OpenTelemetry tracing sets the
 * propagators on an OTel-wide basis even though the settings as represented in Helidon config are nested under `tracing`.
 * <p>
 * For tracing, Helidon supports the following OpenTelemetry elements which Helidon allows applications to determine.
 * <p>
 * <ul>
 *     <li>Sampler
 *     <p>
 *     OpenTelemetry supports one sampler instance for tracing. Helidon builds a sampler from configuration if the application
 *     <ul>
 *         <li>invokes {@link #config(io.helidon.common.config.Config)} (passing a tracing config object which sets
 *         {@code sampler-type} or {@code sampler-param}), or
 *         </li>
 *         <li>invokes either
 *     {@link #samplerType(io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.SamplerType)} or
 *     {@link #samplerParam(Number)}.
 *         </li>
 *     </ul>
 *     Alternatively, the application can invoke
 *     {@link #sampler(io.opentelemetry.sdk.trace.samplers.Sampler)} to directly assign the sampler Helidon should use,
 *     ignoring configuration.
 *     <p>
 *     Last wins; invoking {@code config} (with {@code sampler-type} or {@code sampler-param}) or invoking {@code samplerType}
 *     or {@code samplerParam} clears any previous assignment using the {@code sampler} method, and invoking {@code sampler}
 *     overrides any previously-assigned configuration, {@code sampler-type}, and {@code sampler-param} values.
 *     </li>
 *     <li>Span processors
 *     <p>
 *     OpenTelemetry allows multiple span processors to be active concurrently. Helidon builds one from config if the application
 *     invokes {@link #config(io.helidon.common.config.Config)} (with a config setting for {@code span-processor-type}) or
 *     {@link #spanProcessorType(io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.SpanProcessorType)}.
 *     Application code can add one or more span processors it prepares itself by invoking
 *     {@link #addSpanProcessor(io.opentelemetry.sdk.trace.SpanProcessor)}, or it can invoke
 *     {@link #spanProcessors(java.util.Collection)} to indicate exactly which span processors to use, overriding any already
 *     assigned, added, or derived from config.
 *     <p>
 *     Invoking {@code spanProcessors} effectively causes Helidon to ignore any previously-set configuration related to the
 *     span processor or any span processors previously added. Invoking {@code config} or {@code spanProcessorType}
 *     <em>does not</em> clear previously-assigned or added span processors.
 *     </li>
 *     <li>Span exporter
 *     <p>
 *     In OpenTelemetry each span processor uses exactly one span exporter. Helidon builds one span exporter (currently only
 *     of type {@code otlp}) to use with the span processor (if any) it builds from config as described as above. If the
 *     application invokes
 *     {@link #exporterType(io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.SpanExporterType)}
 *     (and related methods to further influence the behavior of the exporter) or invokes
 *     {@link #config(io.helidon.common.config.Config)} (with a tracing config objection setting {@code exporter-type}) then
 *     Helidon prepares a span exporter from those settings for use by the config-inspired span processor.
 *     <p>
 *     Alternatively, the application can invoke {@link #exporter(io.opentelemetry.sdk.trace.export.SpanExporter)} to specify
 *     exactly which span exporter Helidon should use for the config-inspired span processor.
 *     <p>
 *     Last wins; invoking {@code exporter} causes Helidon to ignore any previously-assigned configuration settings related to
 *     the span exporter, and invoking {@code config} or {@code exporterType} causes Helidon to disregard a previously-assigned
 *     span exporter using {@code exporter}.
 *     </li>
 * </ul>
 *
 * Because OpenTelemetry supports only one sampler, applications can either:
 * <ul>
 *     <li><em>describe</em> the sampler by invoking {@link #config(io.helidon.common.config.Config)} with a config object that
 *     contains settings for {@code sampler-type} and/or {@code sampler-param}, or by invoking the
 *     {@link #samplerType(io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.SamplerType)} and/or
 *     {@link #samplerParam(Number)} methods, or
 *     </li>
 *     <li><em>assign</em> the sampler by invoking {@link #sampler(io.opentelemetry.sdk.trace.samplers.Sampler)}.
 *     </li>
 * </ul>
 *
 * See <a href="https://opentelemetry.io/docs/languages/java/configuration/">OpenTelemetry Configuration</a> for more
 * information.
 */
@Configured(prefix = "tracing", root = true, description = "OpenTelemetry tracer configuration.")
public class OpenTelemetryTracerBuilder implements TracerBuilder<OpenTelemetryTracerBuilder> {
    private final Map<String, String> headers = new HashMap<>();
    private final Set<PropagationFormat> propagationFormats = PropagationFormat.DEFAULT;
    private final Map<String, String> tags = new HashMap<>();
    private final List<SpanProcessor> developerSuppliedSpanProcessors = new ArrayList<>();

    private OpenTelemetry ot;
    private String serviceName = "helidon-service";
    private boolean registerGlobal;
    private byte[] privateKey;
    private byte[] certificate;
    private byte[] trustedCertificates;
    private ExporterProtocol exporterProtocol;
    private int exporterPort;
    private String exporterHost;
    private String exporterPath;
    private Duration timeout;
    private SpanExporterType spanExporterType;
    private SpanProcessorType spanProcessorType;
    private SamplerType samplerType;
    private Number samplerParam;
    private
    private boolean isPropagationFormatsDefaulted = true;
    private String compression = "gzip";
    private SpanExporter developerSuppliedExporter;

    private Sampler developerSuppliedSampler;

    @Override
    public Tracer build() {
        if (ot == null) {
            ot = GlobalOpenTelemetry.get();
        }
        io.opentelemetry.api.trace.Tracer tracer = ot.getTracer(serviceName);
        Tracer result = new OpenTelemetryTracer(ot, tracer, Map.of());
        if (registerGlobal) {
            Tracer.global(result);
        }

        AttributesBuilder attributesBuilder = Attributes.builder()
                .put(ResourceAttributes.SERVICE_NAME, serviceName);

        tags.forEach(attributesBuilder::put);
        Resource otelResource = Resource.create(attributesBuilder.build());
        SdkTracerProviderBuilder sdkTracerProviderBuilder = SdkTracerProvider.builder()
                .setSampler(developerSuppliedSampler != null ? developerSuppliedSampler : configInspiredSampler())
                .setResource(otelResource);
        developerSuppliedExporters.stream()
                .map(this::spanProcessor)
                .forEach(sdkTracerProviderBuilder::addSpanProcessor);

        if (spanProcessorType != null) {
            sdkTracerProviderBuilder.addSpanProcessor(configInspiredSpanProcessor());
        }
        developerSuppliedSpanProcessors.forEach(sdkTracerProviderBuilder::addSpanProcessor);

        OpenTelemetry ot = OpenTelemetrySdk.builder()
                .setTracerProvider(sdkTracerProviderBuilder.build())
                .setPropagators(ContextPropagators.create(TextMapPropagator.composite(propagators())))
                .build();

        result = HelidonOpenTelemetry.create(ot, ot.getTracer(this.serviceName), Map.of());

        if (global) {
            GlobalOpenTelemetry.set(ot);
        }

        LOGGER.log(System.Logger.Level.INFO,
                   () -> "Creating Jaeger tracer for '" + this.serviceName + "' configured with " + protocol
                           + "://" + host + ":" + port);

    }

    OpenTelemetryTracerBuilder openTelemetry(OpenTelemetry ot) {
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
        exporterProtocol = ExporterProtocol.create(protocol);
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
        prepareSpanProcessorFromConfig = true;
        tracingConfig.get("enabled").asBoolean().ifPresent(this::enabled);
        tracingConfig.get("service").asString().ifPresent(this::serviceName);
        tracingConfig.get("protocol").asString().ifPresent(this::collectorProtocol);
        tracingConfig.get("host").asString().ifPresent(this::collectorHost);
        tracingConfig.get("port").asInt().ifPresent(this::collectorPort);
        tracingConfig.get("path").asString().ifPresent(this::collectorPath);
        tracingConfig.get("exporter-type").asString().as(SpanExporterType::create).ifPresent(this::exporterType);
        tracingConfig.get("exporter-timeout").as(Duration.class).ifPresent(this::exporterTimeout);
        tracingConfig.get("sampler-type").asString().as(SamplerType::create).ifPresent(this::samplerType);
        tracingConfig.get("sampler-param").as(Number.class).ifPresent(this::samplerParam);
        tracingConfig.get("private-key-pem").map(io.helidon.common.configurable.Resource::create).ifPresent(this::privateKey);
        tracingConfig.get("client-cert-pem").map(io.helidon.common.configurable.Resource::create)
                .ifPresent(this::clientCertificate);
        tracingConfig.get("trusted-cert-pem").map(io.helidon.common.configurable.Resource::create)
                .ifPresent(this::trustedCertificates);
        tracingConfig.get("headers").asMap().ifPresent(this::headers);
        tracingConfig.get("exporter-protocol").asString().as(ExporterProtocol::create).ifPresent(this::exporterProtocol);
        tracingConfig.get("span-processor-type").asString().as(SpanProcessorType::create).ifPresent(this::spanProcessorType);

        tracingConfig.get("propagation").asList(String.class)
                .ifPresent(strings -> strings.stream()
                        .map(PropagationFormat::create)
                        .forEach(this::addPropagation));

        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder enabled(boolean enabled) {
        return this;
    }

    @Override
    public OpenTelemetryTracerBuilder registerGlobal(boolean global) {
        this.registerGlobal = global;
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
     * Span exporter type to use.
     *
     * @param spanExporterType {@link io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.SpanExporterType}
     * to use
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder exporterType(SpanExporterType spanExporterType) {
        this.spanExporterType = spanExporterType;
        developerSuppliedExporter = null;
        return this;
    }

    /**
     * Wire protocol to use with the exporter.
     *
     * @param exporterProtocol {@link io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.ExporterProtocol}
     * to use with "otlp" exporter.
     * @return updated builder
     */
    @ConfiguredOption(ExporterProtocol.DEFAULT_STRING)
    public OpenTelemetryTracerBuilder exporterProtocol(ExporterProtocol exporterProtocol) {
        this.exporterProtocol = exporterProtocol;
        return this;
    }

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
    @ConfiguredOption("otlp.headers")
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
     * Timeout value for outgoing traces.
     *
     * @param timeout OpenTelemetry timeout for outgoing traces
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder exporterTimeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Sampler type to use for sampling spans. Invoking this method clears any sampler previously set by
     * {@link #sampler(io.opentelemetry.sdk.trace.samplers.Sampler).}
     *
     * @param samplerType {@link io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerBuilder.SamplerType} to use
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder samplerType(SamplerType samplerType) {
        this.samplerType = samplerType;
        developerSuppliedSampler = null;
        return this;
    }

    /**
     * Sampler parameter for influencing the behavior of the selected sampler. Invoking this method clears any sampler previously
     * set by {@link #sampler(io.opentelemetry.sdk.trace.samplers.Sampler).}
     *
     * @param samplerParam parameter value; meaning varies depending on the specific sample in use
     * @return updated builder
     */
    @ConfiguredOption
    public OpenTelemetryTracerBuilder samplerParam(Number samplerParam) {
        this.samplerParam = samplerParam;
        developerSuppliedSampler = null;
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
    @ConfiguredOption
    public OpenTelemetryTracerBuilder spanProcessorType(SpanProcessorType spanProcessorType) {
        this.spanProcessorType = spanProcessorType;
        return this;
    }

    /**
     * Assign the span processors to use, overriding any span processors previously added using
     * {@link #addSpanProcessor(io.opentelemetry.sdk.trace.SpanProcessor)} or implied by an earlier invocation of
     * {@link #config(io.helidon.common.config.Config)}.
     *
     * @param spanProcessors {@link io.opentelemetry.sdk.trace.SpanProcessor} objects to use
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder spanProcessors(Collection<SpanProcessor> spanProcessors) {
        spanProcessors.clear();
        spanProcessorType = null;
        this.developerSuppliedSpanProcessors.addAll(spanProcessors);
        return this;
    }

    /**
     * Add a span processor to use.
     *
     * @param spanProcessor {@link io.opentelemetry.sdk.trace.SpanProcessor} to add
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder addSpanProcessor(SpanProcessor spanProcessor) {
        developerSuppliedSpanProcessors.add(spanProcessor);
        return this;
    }

    /**
     * Assign the sampler to use; if assigned after invoking {@link #config(io.helidon.common.config.Config)} this value
     * overrides the {@link io.opentelemetry.sdk.trace.samplers.Sampler} derived from configuration for {@code sampler-type}
     * and {@code sampler-param}.
     *
     * @param sampler {@code Sampler} to use
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder sampler(Sampler sampler) {
        this.developerSuppliedSampler = sampler;
        samplerType = null;
        samplerParam = null;
        return this;
    }

    @Override
    public <B> B unwrap(Class<B> builderClass) {
        if (builderClass.isAssignableFrom(getClass())) {
            return builderClass.cast(this);
        }
        throw new IllegalArgumentException("Cannot unwrap " + builderClass + " from Opentelmetry tracer builder.");
    }

    /**
     * Adds a {@link io.opentelemetry.sdk.trace.export.SpanExporter} to the span exporters Helidon uses.
     *
     * @param spanExporter {@code SpanExporter} to add
     * @return updated builder
     */
    public OpenTelemetryTracerBuilder addExporter(SpanExporter spanExporter) {
        developerSuppliedExporters.add(spanExporter);
        return this;
    }

    /**
     * Specifies the {@link io.opentelemetry.sdk.trace.export.SpanExporter} object Helidon uses for the span processor Helidon
     * derives from configuration, overriding the span exporter Helidon would otherwise derive from config.
     *
     * @param spanExporter {@code SpanExporter} object to use
     * @return updated builders
     */
    public OpenTelemetryTracerBuilder exporter(SpanExporter spanExporter) {
        developerSuppliedExporter = spanExporter;
        spanExporterType = null;
        return this;
    }

    private Iterable<TextMapPropagator> propagators() {
        return propagationFormats.stream()
                .map(PropagationFormat::propagator)
                .collect(Collectors.toList());
    }

    private Sampler configInspiredSampler() {
        return switch (samplerType) {
            case ALWAYS_ON -> Sampler.alwaysOn();
            case ALWAYS_OFF -> Sampler.alwaysOff();
            case TRACE_ID_RATIO -> Sampler.traceIdRatioBased(samplerParam.doubleValue());
            case PARENT_BASED_ALWAYS_OFF -> Sampler.parentBased(Sampler.alwaysOff());
            case PARENT_BASED_ALWAYS_ON -> Sampler.parentBased(Sampler.alwaysOn());
            case PARENT_BASED_TRACE_ID_RATIO -> Sampler.parentBased(Sampler.traceIdRatioBased(samplerParam.doubleValue()));
        };
    }

    private SpanProcessor configInspiredSpanProcessor() {

    }

    private SpanExporter spanExporter(ExporterProtocol exporterProtocol) {
        return switch (exporterProtocol) {

            case GRPC -> OtlpGrpcSpanExporter.builder()
                    .setCompression(compression);
            case HTTP_PROTOBUF -> OtlpHttpSpanExporter.builder().build();
        };
    }

    /**
     * Valid values for OpenTelemetry exporter protocol.
     */
    public enum ExporterProtocol {
        /**
         * grpc OpenTelemetry protocol.
         */
        GRPC("grpc"),

        /**
         * http/protobuf OpenTelemetry protocol.
         */
        HTTP_PROTOBUF("http/protobuf");

        static final String DEFAULT_STRING = "grpc";
        private final String protocol;

        ExporterProtocol(String protocol) {
            this.protocol = protocol;
        }

        static ExporterProtocol create(String protocol) {
            for (ExporterProtocol exporterProtocol : ExporterProtocol.values()) {
                if (exporterProtocol.protocol.equals(protocol)) {
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

        static final EnumSet<PropagationFormat> DEFAULT = EnumSet.of(TRACE_CONTEXT, BAGGAGE);
        static final String DEFAULT_STRING = "tracecontext,baggage";
        private final String format;
        private final Supplier<TextMapPropagator> propagatorSupplier;

        PropagationFormat(String format, Supplier<TextMapPropagator> propagatorSupplier) {
            this.format = format;
            this.propagatorSupplier = propagatorSupplier;
        }

        static PropagationFormat create(String value) {
            for (PropagationFormat propagationFormat : PropagationFormat.values()) {
                if (propagationFormat.format.equals(value)) {
                    return propagationFormat;
                }
            }
            throw new IllegalArgumentException("Unknown propagation format: " + value + "; expected one or more of " +
                                                       Arrays.toString(PropagationFormat.values()));
        }

        TextMapPropagator propagator() {
            return propagatorSupplier.get();
        }
    }

    /**
     * Sampler types valid for OpenTelemetry tracing.
     * <p>
     * This enum intentionally omits {@code jaeger-remote} as that requires an additional library.
     * Users who want to use that sampler can add the dependency themselves and prepare the sample and pass it to
     * the builder's {@link #sampler(io.opentelemetry.sdk.trace.samplers.Sampler)} method.
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
                if (samplerType.config.equals(value)) {
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
        SIMPLE,
        /**
         * Batch Span Processor.
         */
        BATCH;

        static SpanProcessorType create(String value) {
            return SpanProcessorType.valueOf(value.toUpperCase(Locale.ROOT));
        }
    }

    /**
     * Span exporter type.
     */
    public enum SpanExporterType {
        /**
         * OpenTelemetry protocol.
         */
        OTLP("otlp");

        private final String value;

        SpanExporterType(String value) {
            this.value = value;
        }

        static SpanExporterType create(String value) {
            for (SpanExporterType spanExporterType : SpanExporterType.values()) {
                if (spanExporterType.value.equals(value)) {
                    return spanExporterType;
                }
            }
            throw new IllegalArgumentException("Unknown span exporter type: " + value + "; expected one of "
                                                       + Arrays.toString(SpanExporterType.values()));
        }
    }
}
