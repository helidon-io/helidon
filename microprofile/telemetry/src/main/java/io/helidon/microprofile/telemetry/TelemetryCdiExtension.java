/*
 * Copyright (c) 2023, 2026 Oracle and/or its affiliates.
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

import java.lang.annotation.Annotation;

import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.Tracing;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Initialized;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.BeforeBeanDiscovery;
import jakarta.enterprise.inject.spi.Extension;
import jakarta.enterprise.inject.spi.ProcessAnnotatedType;
import jakarta.enterprise.inject.spi.WithAnnotations;
import jakarta.enterprise.inject.spi.configurator.AnnotatedMethodConfigurator;
import jakarta.enterprise.inject.spi.configurator.AnnotatedParameterConfigurator;
import jakarta.interceptor.Interceptor;

/**
 * CDI extension for Microprofile Telemetry implementation.
 */
public class TelemetryCdiExtension implements Extension {

    private static final System.Logger LOGGER = System.getLogger(TelemetryCdiExtension.class.getName());

    /**
     * For service loading.
     */
    public TelemetryCdiExtension() {
    }

    /**
     * Add {@code HelidonWithSpan} annotation with interceptor.
     *
     * @param discovery BeforeBeanDiscovery
     */
    void before(@Observes BeforeBeanDiscovery discovery) {
        LOGGER.log(System.Logger.Level.TRACE, () -> "Before Telemetry bean discovery " + discovery);

        // Register annotations, interceptors and producers.
        discovery.addAnnotatedType(HelidonWithSpan.class, HelidonWithSpan.class.getName());
        discovery.addAnnotatedType(WithSpanInterceptor.class, WithSpanInterceptor.class.getName());
        discovery.addAnnotatedType(OpenTelemetryProducer.class, OpenTelemetryProducer.class.getName());
    }

    void processSeAnnotations(@Observes @WithAnnotations(Tracing.Traced.class) ProcessAnnotatedType<?> pat) {
        var configurator = pat.configureAnnotatedType();
        var topLevel = configurator.getAnnotated().getAnnotation(Tracing.Traced.class);
        configurator.remove(it -> it.annotationType().equals(Tracing.Traced.class));

        boolean trace = false;
        String template = "%1$s.%2$s";
        Span.Kind kind = Span.Kind.INTERNAL;

        if (topLevel != null) {
            trace = true;
            template = topLevel.value();
            kind = topLevel.kind();
        }

        for (AnnotatedMethodConfigurator<?> method : configurator.methods()) {
            var methodLevel = method.getAnnotated().getAnnotation(Tracing.Traced.class);
            if (methodLevel == null && !trace) {
                continue;
            }
            if (methodLevel != null) {
                String value = methodLevel.value();
                if (!value.equals("%1$s.%2$s")) {
                    template = value;
                }
                if (methodLevel.kind() != Span.Kind.INTERNAL) {
                    kind = methodLevel.kind();
                }
            }
            var finalTemplate = template;
            var finalKind = kind;

            method.remove(it -> it.annotationType().equals(Tracing.Traced.class));
            method.add(new WithSpan() {
                @Override
                public String value() {
                    return String.format(finalTemplate,
                                         configurator.getAnnotated().getJavaClass().getName(),
                                         method.getAnnotated().getJavaMember().getName());
                }

                @Override
                public SpanKind kind() {
                    return switch (finalKind) {
                        case INTERNAL -> SpanKind.INTERNAL;
                        case SERVER -> SpanKind.SERVER;
                        case CLIENT -> SpanKind.CLIENT;
                        case PRODUCER -> SpanKind.PRODUCER;
                        case CONSUMER -> SpanKind.CONSUMER;
                    };
                }

                @Override
                public Class<? extends Annotation> annotationType() {
                    return WithSpan.class;
                }

                @Override
                public boolean inheritContext() {
                    return true;
                }
            });

            for (AnnotatedParameterConfigurator<?> param : method.params()) {
                var tagParam = param.getAnnotated().getAnnotation(Tracing.ParamTag.class);
                if (tagParam == null) {
                    continue;
                }
                String value;
                if (tagParam.value().isEmpty()) {
                    // not great, but we do warn in the javadoc...
                    value = param.getAnnotated().getJavaParameter().getName();
                } else {
                    value = tagParam.value();
                }
                param.add(new SpanAttribute() {
                    @Override
                    public String value() {
                        return value;
                    }

                    @Override
                    public Class<? extends Annotation> annotationType() {
                        return SpanAttribute.class;
                    }
                });
            }
        }
    }

    /**
     * Add {@code HelidonWithSpan} annotation on all methods with {@code WithSpan} annotation.
     *
     * @param pat ProcessAnnotatedType
     */
    void processAnnotations(@Observes @WithAnnotations(WithSpan.class) ProcessAnnotatedType<?> pat) {
        LOGGER.log(System.Logger.Level.TRACE, () -> "Process WithSpan annotation and add binding" + pat);

        var configurator = pat.configureAnnotatedType();

        // Add HelidonWithSpan annotation along to WithSpan.
        for (AnnotatedMethodConfigurator<?> method : configurator.methods()) {
            WithSpan withSpan = method.getAnnotated().getAnnotation(WithSpan.class);
            if (withSpan != null) {
                method.add(HelidonWithSpan.Literal.INSTANCE);
            }
        }
    }

    void finish(@Observes @Priority(Interceptor.Priority.LIBRARY_BEFORE) @Initialized(ApplicationScoped.class) Object startup,
                Tracer tracer) {
        // Forcing CDI to get us a tracer and then invoking one of the bean's methods triggers the producer to do its
        // initialization, including setting the global tracer as part of start up.
        tracer.enabled();
        LOGGER.log(System.Logger.Level.TRACE,
                   () -> "Global tracer set to " + tracer.unwrap(io.opentelemetry.api.trace.Tracer.class));
    }
}
