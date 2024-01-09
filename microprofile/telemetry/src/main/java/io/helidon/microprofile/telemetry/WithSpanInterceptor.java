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
import java.util.Arrays;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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

    @Inject
    WithSpanInterceptor(Tracer tracer) {
        this.tracer = tracer;
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

        SpanBuilder spanBuilder = tracer.spanBuilder(spanName)
                .setSpanKind(annotation.kind())
                .setParent(Context.current());

        for (int i = 0; i < method.getParameters().length; i++) {
            Parameter p = method.getParameters()[i];
            if (p.isAnnotationPresent(SpanAttribute.class)) {
                SpanAttribute spanAttribute = p.getAnnotation(SpanAttribute.class);
                var attrName = spanAttribute.value().isBlank() ? p.getName() : spanAttribute.value();
                Class<?> paramType = p.getType();
                Object pValue = context.getParameters()[i];

                if (paramType.isAssignableFrom(String.class)) {
                    spanBuilder.setAttribute(attrName, (String) pValue);
                } else if (paramType.isAssignableFrom(long.class) || paramType.isAssignableFrom(Long.class)) {
                    spanBuilder.setAttribute(attrName, (long) pValue);
                } else if (paramType.isAssignableFrom(double.class) || paramType.isAssignableFrom(Double.class)) {
                    spanBuilder.setAttribute(attrName, (double) pValue);
                } else if (paramType.isAssignableFrom(boolean.class) || paramType.isAssignableFrom(Boolean.class)) {
                    spanBuilder.setAttribute(attrName, (boolean) pValue);
                } else {
                    spanBuilder.setAttribute(attrName, pValue.toString());
                }
            }
        }

        // Start new Span
        Span span = spanBuilder.startSpan();

        try (Scope scope = span.makeCurrent()) {
            Object result = context.proceed();
            span.end();
            return result;
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        }
    }

}
