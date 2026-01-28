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

package io.helidon.tracing.providers.opentelemetry;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.helidon.builder.api.Prototype;
import io.helidon.config.Config;
import io.helidon.tracing.SamplerType;
import io.helidon.tracing.SpanListener;
import io.helidon.tracing.SpanProcessorType;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.ServiceAttributes;

class OpenTelemetryTracerBlueprintSupport {

    static final String PROPAGATORS_DEFAULT = "new java.util.ArrayList<>(io.helidon.tracing.providers.opentelemetry"
            + ".ContextPropagationType.DEFAULT_PROPAGATORS)";

    private static final String DEFAULT_EXPORTER_SCHEME = "http";
    private static final String DEFAULT_EXPORTER_HOST = "localhost";
    private static final int DEFAULT_EXPORTER_PORT = 4317;

    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryTracerBlueprintSupport.class.getName());

    private OpenTelemetryTracerBlueprintSupport() {
    }

    static class Decorator implements Prototype.BuilderDecorator<OpenTelemetryTracerConfig.BuilderBase<?, ?>> {

        @Override
        public void decorate(OpenTelemetryTracerConfig.BuilderBase<?, ?> target) {
            /*
            See the constructor of OpenTelemetryTracer for some further initialization. It is done there because we want to
            wait for validation to run first before doing that work, and this decorator is invoked before validation.
             */

            if (target.propagator().isEmpty()) {
                target.propagator(TextMapPropagator.composite(target.propagators()));
            }

            /*
            This method is invoked before the builder's values are validated. We need the service name so check it
            explicitly here.
             */
            String serviceName = target.serviceName()
                    .orElseThrow(() -> new IllegalStateException("Property \"service\" must not be null, but not set"));

            /*
            Set the openTelemetry and delegate if they were not explicitly assigned.
             */
            if (target.openTelemetry().isEmpty()) {
                target.openTelemetry(openTelemetryFromSettings(target));
            }
            if (target.delegate().isEmpty()) {
                target.delegate(target.openTelemetry().get().getTracer(serviceName));
            }

        }

        private static OpenTelemetry openTelemetryFromSettings(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder) {
            if (!builder.enabled()) {
                return OpenTelemetry.noop();
            }

            if (builder.openTelemetry().isPresent()) {
                return builder.openTelemetry().get();
            }

            var openTelemetrySdkBuilder = OpenTelemetrySdk.builder();

            var sdkTracerProviderBuilder = SdkTracerProvider.builder();

            var propagator = builder.propagator().orElse(TextMapPropagator.composite(builder.propagators()));
            openTelemetrySdkBuilder.setPropagators(ContextPropagators.create(propagator));

            var attributesBuilder = Attributes.builder();
            attributesBuilder.put(ServiceAttributes.SERVICE_NAME, builder.serviceName().get());

            var resource = Resource.getDefault().merge(Resource.create(attributesBuilder.build()));

            sdkTracerProviderBuilder.addSpanProcessor(spanProcessor(builder))
                    .setResource(resource)
                    .setSampler(sampler(builder));

            builder.spanProcessors().forEach(sdkTracerProviderBuilder::addSpanProcessor);

            openTelemetrySdkBuilder.setTracerProvider(sdkTracerProviderBuilder.build());
            return openTelemetrySdkBuilder.build();
        }

        private static SpanProcessor spanProcessor(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder) {

            var spanExporter = spanExporter(builder);
            return switch (builder.spanProcessorType()) {
                case SpanProcessorType.BATCH -> batchProcessor(builder, spanExporter);
                case SpanProcessorType.SIMPLE -> SimpleSpanProcessor.create(spanExporter);
            };
        }

        private static SpanExporter spanExporter(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder) {
            StringBuilder exporterUrlBuilder = new StringBuilder();

            String scheme = builder.collectorProtocol().orElse(DEFAULT_EXPORTER_SCHEME);
            exporterUrlBuilder.append(scheme).append(scheme.endsWith(":") ? "" : ":").append("//");

            String host = builder.collectorHost().orElse(DEFAULT_EXPORTER_HOST);
            exporterUrlBuilder.append(host);

            int port = builder.collectorPort().orElse(DEFAULT_EXPORTER_PORT);
            exporterUrlBuilder.append(":").append(port);

            builder.collectorPath().ifPresent(path -> exporterUrlBuilder.append(path.startsWith("/") ? "" : "/").append(path));

            return switch (builder.exporterType()) {
                case GRPC -> useGrpc(builder, exporterUrlBuilder.toString());
                case HTTP_PROTO -> useHttpProto(builder, exporterUrlBuilder.toString());
            };
        }

        private static SpanExporter useGrpc(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder,
                                            String exporterUrl) {
            var spanExporterBuilder = OtlpGrpcSpanExporter.builder();
            applySpanExporterSettings(builder,
                                      exporterUrl,
                                      spanExporterBuilder::setEndpoint,
                                      spanExporterBuilder::setClientTls,
                                      spanExporterBuilder::setTrustedCertificates);

            return spanExporterBuilder.build();
        }

        private static SpanExporter useHttpProto(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder,
                                                 String exporterUrl) {
            var spanExporterBuilder = OtlpHttpSpanExporter.builder();
            applySpanExporterSettings(builder,
                                      exporterUrl,
                                      spanExporterBuilder::setEndpoint,
                                      spanExporterBuilder::setClientTls,
                                      spanExporterBuilder::setTrustedCertificates);

            return spanExporterBuilder.build();
        }

        private static void applySpanExporterSettings(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder,
                                                      String exporterUrl,
                                                      Consumer<String> endpointSetter,
                                                      BiConsumer<byte[], byte[]> clientTlsSetter,
                                                      Consumer<byte[]> trustedCertsSetter) {
            endpointSetter.accept(exporterUrl);
            if (builder.privateKey().isPresent() && builder.clientCertificate().isPresent()) {
                clientTlsSetter.accept(builder.privateKey().get().bytes(), builder.clientCertificate().get().bytes());
            }
            builder.trustedCertificate().ifPresent(certs -> trustedCertsSetter.accept(certs.bytes()));
        }

        private static SpanProcessor batchProcessor(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder,
                                                    SpanExporter spanExporter) {
            return BatchSpanProcessor.builder(spanExporter)
                    .setMaxExportBatchSize(builder.maxExportBatchSize())
                    .setExporterTimeout(builder.exportTimeout())
                    .setScheduleDelay(builder.scheduleDelay())
                    .setMaxQueueSize(builder.maxQueueSize())
                    .build();
        }

        private static Sampler sampler(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder) {
            return switch (builder.samplerType()) {
                case SamplerType.CONSTANT -> Sampler.alwaysOn();
                case SamplerType.RATIO -> Sampler.traceIdRatioBased(builder.samplerParam());
            };
        }
    }

    static class CustomMethods {

        /**
         * Adds a string-valued tag.
         *
         * @param builder builder
         * @param name    tag name
         * @param value   tag value
         */
        @Prototype.BuilderMethod
        static void addTracerTag(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder, String name, String value) {
            builder.putTracerTag(name, value);
        }

        /**
         * Adds a numeric-valued tag.
         *
         * @param builder builder
         * @param name    tag name
         * @param value   tag value
         */
        @Prototype.BuilderMethod
        static void addTracerTag(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder, String name, Number value) {
            int intValue = value.intValue();
            if (value.doubleValue() % 1 != 0) {
                LOGGER.log(System.Logger.Level.WARNING, "Value for tag $0 of $1 should be an integer; converting to $2",
                           name,
                           intValue);
            }
            builder.putIntTracerTag(name, intValue);
        }

        /**
         * Adds a boolean-valued tag.
         *
         * @param builder builder
         * @param name    tag name
         * @param value   tag value
         */
        @Prototype.BuilderMethod
        static void addTracerTag(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder, String name, boolean value) {
            builder.putBooleanTracerTag(name, value);
        }

        /**
         * Adds a {@link io.helidon.tracing.SpanListener} to the builder for later registration with the resulting
         * {@link io.helidon.tracing.Tracer}.
         *
         * @param builder      {@code Builder} to add the listener to
         * @param spanListener {@code SpanListener} to add to the {@code Tracer} built from the builder
         */
        @Prototype.BuilderMethod
        static void register(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder, SpanListener spanListener) {
            builder.spanListeners().add(spanListener);
        }

        /**
         * Converts a config node for propagators into a list of {@link io.opentelemetry.context.propagation.TextMapPropagator}.
         * <p>
         * As a user convenience, the config node can be either a node list (in which case each node's string value will be
         * used for a propagator name) or the node can be a single string containing a comma-separated list of propagator names.
         *
         * @param config config node (node list of string nodes or a single node)
         * @return list of selected propagators
         */
        @Prototype.ConfigFactoryMethod
        static List<TextMapPropagator> createPropagators(Config config) {

            Stream<String> propagatorNames = config.isList()
                    ? config.asList(String.class).get().stream()
                    : Arrays.stream(config.asString().get().split(","));

            return propagatorNames
                    .map(ContextPropagationType::from)
                    .map(ContextPropagationType::propagator)
                    .toList();
        }
    }

    static class OpenTelemetryDecorator implements Prototype.OptionDecorator<OpenTelemetryTracerConfig.BuilderBase<?, ?>,
            OpenTelemetry> {

        @Override
        public void decorate(OpenTelemetryTracerConfig.BuilderBase<?, ?> builder, OpenTelemetry openTelemetry) {
            /*
            Some code uses the propagators recorded on the Helidon types rather than delegating to the underlying OpenTelemetry
            objects, so set the propagators on the builder from those already set on the OpenTelemetry instance.
             */
            builder.propagator(openTelemetry.getPropagators().getTextMapPropagator());
        }
    }
}
