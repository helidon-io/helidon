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

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import io.helidon.config.Config;
import io.helidon.tracing.Scope;
import io.helidon.tracing.Span;
import io.helidon.tracing.Tracer;
import io.helidon.tracing.providers.opentelemetry.HelidonOpenTelemetry;

import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;



/**
 * Intercept {@code HelidonWithSpan} annotated method and invoke tracer.
 */
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 7)
@HelidonWithSpan
class WithSpanInterceptor {
    private static final System.Logger LOGGER = System.getLogger(WithSpanInterceptor.class.getName());

    private final Tracer tracer;
    private final boolean isAgentPresent;

    @Inject
    WithSpanInterceptor(Tracer tracer, Config config) {
        this.tracer = tracer;
        isAgentPresent = HelidonOpenTelemetry.AgentDetector.isAgentPresent(config);
    }

    /**
     * Span around method invoke.
     *
     * @param context Invocation Context
     * @return Invocation proceed.
     * @throws Exception when something is wrong
     */
    @AroundInvoke
    public Object interceptSpan(InvocationContext context) throws Exception {

        if (isAgentPresent) {
            return context.proceed();
        }

        if (LOGGER.isLoggable(System.Logger.Level.TRACE)) {
            LOGGER.log(System.Logger.Level.TRACE, "Starting new Span on WithSpan annotated method");
        }

        Method method = context.getMethod();

        WithSpan annotation = method.getAnnotation(WithSpan.class);

        String spanName = annotation.value();

        //Process span name. Should be class name, as well as inner classes.
        if (spanName.isEmpty()) {
            String className = method.getDeclaringClass().getName();
            if (className.contains("$")) {
                className = className.substring(className.lastIndexOf("$") + 1);
            }
            spanName = className + "." + method.getName();
        }

        io.helidon.tracing.Span.Builder<?> helidonSpanBuilder = tracer.spanBuilder(spanName)
                .kind(Span.Kind.INTERNAL);
        Span.current().map(Span::context).ifPresent(helidonSpanBuilder::parent);

        for (int i = 0; i < method.getParameters().length; i++) {
            Parameter p = method.getParameters()[i];
            if (p.isAnnotationPresent(SpanAttribute.class)) {
                SpanAttribute spanAttribute = p.getAnnotation(SpanAttribute.class);
                var attrName = spanAttribute.value().isBlank() ? p.getName() : spanAttribute.value();
                Class<?> paramType = p.getType();
                Object pValue = context.getParameters()[i];

                if (String.class.isAssignableFrom(paramType)) {
                    helidonSpanBuilder.tag(attrName, (String) pValue);
                } else if (Long.class.isAssignableFrom(paramType) || long.class.isAssignableFrom(paramType)) {
                    helidonSpanBuilder.tag(attrName, (Long) pValue);
                } else if (Double.class.isAssignableFrom(paramType) || double.class.isAssignableFrom(paramType)) {
                    helidonSpanBuilder.tag(attrName, (Double) pValue);
                } else if (Boolean.class.isAssignableFrom(paramType) || boolean.class.isAssignableFrom(paramType)) {
                    helidonSpanBuilder.tag(attrName, (Boolean) pValue);
                } else {
                    helidonSpanBuilder.tag(attrName, pValue.toString());
                }
            }
        }

        // Start new Span
        io.helidon.tracing.Span helidonSpan = helidonSpanBuilder.start();

        try (Scope ignored = helidonSpan.activate()) {
            Object result = context.proceed();
            helidonSpan.end();
            return result;
        } catch (Exception e) {
            helidonSpan.end(e);
            throw e;
        }
    }

}
