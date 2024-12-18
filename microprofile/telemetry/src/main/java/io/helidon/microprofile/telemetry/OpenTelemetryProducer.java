/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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
package io.helidon.microprofile.telemetry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import io.helidon.config.Config;
import io.helidon.config.mp.MpConfig;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;
import io.helidon.tracing.providers.opentelemetry.OpenTelemetryTracerProvider;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.BaggageBuilder;
import io.opentelemetry.api.baggage.BaggageEntry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.resources.Resource;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * A producer of {@link io.opentelemetry.api.OpenTelemetry}, {@link io.opentelemetry.api.trace.Tracer},
 * {@link io.opentelemetry.api.trace.Span} and {@link io.opentelemetry.api.baggage.Baggage}
 * required for injection into {@code CDI} beans.
 */
@ApplicationScoped
class OpenTelemetryProducer {

    static final String INJECTION_TYPE = "telemetry.injection-type";

    private static final System.Logger LOGGER = System.getLogger(OpenTelemetryProducer.class.getName());
    private static final String HELIDON_SERVICE_NAME = "HELIDON_MICROPROFILE_TELEMETRY";
    private static final String SERVICE_NAME = "service.name";
    private static final String ENV_OTEL_SDK_DISABLED = "OTEL_SDK_DISABLED";
    private static final String OTEL_SDK_DISABLED = "otel.sdk.disabled";
    private static final String OTEL_METRICS_EXPORTER = "otel.metrics.exporter";
    private static final String OTEL_LOGS_EXPORTER = "otel.logs.exporter";
    private static final String SERVICE_NAME_PROPERTY = "otel.service.name";
    private static final String EXPORTER_NAME_PROPERTY = "otel.exporter.name";
    private String exporterName = HELIDON_SERVICE_NAME; // Default if not set

    private OpenTelemetry openTelemetry;
    private Map<String, String> telemetryProperties;

    private final Config config;

    private volatile Tracer tracer;

    private volatile io.helidon.tracing.Tracer helidonTracer;

    private final org.eclipse.microprofile.config.Config mpConfig;

    private Producer producer;

    @Inject
    OpenTelemetryProducer(Config config, org.eclipse.microprofile.config.Config mpConfig) {
        this.config = config;
        this.mpConfig = mpConfig;
    }

    /**
     * Construct OpenTelemetry. If the OTEL Agent is present, everything is configured and prepared.
     * We just reuse objects from the Agent.
     */
    @PostConstruct
    private void init() {

        telemetryProperties  = Collections.unmodifiableMap(getTelemetryProperties());

        mpConfig.getOptionalValue(EXPORTER_NAME_PROPERTY, String.class).ifPresent(e -> exporterName = e);

        // If there is an OTEL Agent – or otherwise we should use a pre-existing global OTel instance - delegate to it.
        if (HelidonOpenTelemetry.AgentDetector.useExistingGlobalOpenTelemetry(config)) {
            openTelemetry =  GlobalOpenTelemetry.get();
        } else {

            //Initialize OpenTelemetry
            if (!isTelemetryDisabled()) {
                openTelemetry = AutoConfiguredOpenTelemetrySdk.builder()
                        .addPropertiesCustomizer(x -> telemetryProperties)
                        .addResourceCustomizer(this::customizeResource)
                        .setServiceClassLoader(Thread.currentThread().getContextClassLoader())
                        .disableShutdownHook()
                        .build()
                        .getOpenTelemetrySdk();

                if (openTelemetry != null) {
                    if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                        LOGGER.log(System.Logger.Level.TRACE, "Telemetry Auto Configured");
                    }
                } else {
                    if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                        LOGGER.log(System.Logger.Level.TRACE, "Telemetry Disabled");
                    }
                    openTelemetry = OpenTelemetry.noop();
                }
            } else {
                if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
                    LOGGER.log(System.Logger.Level.TRACE, "Telemetry Disabled by configuration");
                }
                openTelemetry = OpenTelemetry.noop();
            }
        }

        tracer = openTelemetry.getTracer(exporterName);
        helidonTracer = HelidonOpenTelemetry.create(openTelemetry, tracer, Map.of());
        TelemetryConfig telemetryConfig =
                TelemetryConfig.create(MpConfig.toHelidonConfig(mpConfig).get(TelemetryConfig.TELEMETRY_CONFIG_KEY));
        producer = (telemetryConfig.injectionType() == InjectionType.NEUTRAL)
                ? WrappedProducer.create(helidonTracer)
                : NativeOpenTelemetryProducer.create(tracer);
        OpenTelemetryTracerProvider.globalTracer(helidonTracer);
    }

    /**
     * Get OpenTelemetry.
     *
     * @return OpenTelemetry
     */
    @ApplicationScoped
    @Produces
    OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    /**
     * Provides an instance of the current OpenTelemetry Tracer.
     *
     * @return Tracer.
     */
    @Produces
    Tracer tracer() {
        return producer.tracer();
    }

    /**
     * Provides an instance of the current Helidon API Tracer.
     *
     * @return Tracer.
     */
    @Produces
    io.helidon.tracing.Tracer helidonTracer() {
        return helidonTracer;
    }

    /**
     * Provides an instance of the current Span.
     *
     * @return a {@link io.opentelemetry.api.trace.Span}.
     */
    @Produces
    Span span() {
        return producer.span();
    }

    /**
     * Provides an instance of the current Baggage.
     *
     * @return a {@link io.opentelemetry.api.baggage.Baggage}.
     */
    @Produces
    Baggage baggage() {
        return producer.baggage();
    }

    interface Producer {

        Tracer tracer();

        Span span();

        /**
         * Produces a {@link io.opentelemetry.api.baggage.Baggage} instance for injection.
         * <p>
         *     Note that we do not need to return a wrapper around the baggage because the span listener interface has no
         *     callback method related to baggage.
         *
         * @return OTel baggage instance
         */
        default Baggage baggage() {
            return new Baggage() {
                @Override
                public int size() {
                    return Baggage.current().size();
                }

                @Override
                public void forEach(BiConsumer<? super String, ? super BaggageEntry> consumer) {
                    Baggage.current().forEach(consumer);
                }

                @Override
                public Map<String, BaggageEntry> asMap() {
                    return Baggage.current().asMap();
                }

                @Override
                public String getEntryValue(String entryKey) {
                    return Baggage.current().getEntryValue(entryKey);
                }

                @Override
                public BaggageBuilder toBuilder() {
                    return Baggage.current().toBuilder();
                }
            };
        }
    }

    // Process "otel." properties from microprofile config file.
    private Map<String, String> getTelemetryProperties() {

        HashMap<String, String> telemetryProperties = new HashMap<>();
        for (String propertyName : mpConfig.getPropertyNames()) {
            if (propertyName.startsWith("otel.")) {
                mpConfig.getOptionalValue(propertyName, String.class).ifPresent(
                        value -> telemetryProperties.put(propertyName, value));
            }
        }

        // Metrics and logs exporters should be set to  "none"
        telemetryProperties.putIfAbsent(OTEL_METRICS_EXPORTER, "none");
        telemetryProperties.putIfAbsent(OTEL_LOGS_EXPORTER, "none");
        return telemetryProperties;
    }

    // Check if Telemetry is disabled by checking configuration or environmental variables.
    private boolean isTelemetryDisabled() {
        if (telemetryProperties.get(ENV_OTEL_SDK_DISABLED) != null) {
            return Boolean.parseBoolean(telemetryProperties.get(ENV_OTEL_SDK_DISABLED));
        } else if (telemetryProperties.get(OTEL_SDK_DISABLED) != null) {
            return Boolean.parseBoolean(telemetryProperties.get(OTEL_SDK_DISABLED));
        }
        return true;
    }

    private Resource customizeResource(Resource resource, ConfigProperties c) {
        return resource.toBuilder()
                .put(SERVICE_NAME, getServiceName(c))
                .build();
    }

    private String getServiceName(ConfigProperties c) {
        return c.getString(SERVICE_NAME_PROPERTY, HELIDON_SERVICE_NAME);
    }



}
