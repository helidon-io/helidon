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
package io.helidon.microprofile.tracing;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.jersey.client.ClientTracingFilter;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.opentracing.Traced;

/**
 * Interceptor for {@link org.eclipse.microprofile.opentracing.Traced} annotation.
 */
@Interceptor
@Traced
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 7)
public class MpTracingInterceptor {
    @Inject
    private Tracer tracer;

    @AroundInvoke
    private Object aroundMethod(InvocationContext context) throws Exception {
        Method method = context.getMethod();

        return trace(context, method, method.getDeclaringClass());
    }

    private <E extends Member & AnnotatedElement> Object trace(InvocationContext context,
                                                               E element,
                                                               Class<?> declaringClass) throws Exception {
        if (null != declaringClass.getAnnotation(Path.class)) {
            return context.proceed();
        }

        Traced annotation = element.getAnnotation(Traced.class);
        if (null == annotation) {
            annotation = declaringClass.getAnnotation(Traced.class);
            if (null == annotation) {
                return context.proceed();
            }
        }

        if (annotation.value()) {
            String newName = annotation.operationName();
            if (newName.isEmpty()) {
                newName = spanName(declaringClass, element);
            }
            Tracer tracer = locateTracer();
            Optional<SpanContext> parentSpan = locateParent();

            Tracer.SpanBuilder spanBuilder = tracer.buildSpan(newName);

            parentSpan.ifPresent(spanBuilder::asChildOf);

            Span span = spanBuilder.start();
            Scope scope = tracer.scopeManager().activate(span);

            try {
                return context.proceed();
            } catch (Exception e) {
                span.setTag("error", true);
                span.log(Map.of("error.object", e.getClass().getName(),
                                "event", "error"));
                throw e;
            } finally {
                span.finish();
                scope.close();
            }
        } else {
            return context.proceed();
        }
    }

    private <E extends Member & AnnotatedElement> String spanName(Class<?> declaringClass, E element) {
        return declaringClass.getName() + "." + element.getName();
    }

    private Optional<SpanContext> locateParent() {
        // first check if we are in an active span
        Span active = tracer.activeSpan();
        if (null != active) {
            // in case there is an active span (such as from JAX-RS resource or from another bean), use it as a parent
            return Optional.of(active.context());
        }
        Optional<Context> context = Contexts.context();

        return context.flatMap(ctx -> ctx.get(SpanContext.class))
                .or(() -> context.flatMap(ctx -> ctx.get(ClientTracingFilter.class, SpanContext.class)));
    }

    private Tracer locateTracer() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(Tracer.class))
                .orElseGet(GlobalTracer::get);
    }

}
