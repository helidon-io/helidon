/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;
import javax.ws.rs.Path;

import io.helidon.common.OptionalHelper;
import io.helidon.common.context.Context;
import io.helidon.common.context.Contexts;
import io.helidon.tracing.jersey.client.ClientTracingFilter;

import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.eclipse.microprofile.opentracing.Traced;

import static io.helidon.common.CollectionsHelper.mapOf;

/**
 * Interceptor for {@link org.eclipse.microprofile.opentracing.Traced} annotation.
 */
@Interceptor
@Traced
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 7)
public class MpTracingInterceptor {
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
            try {
                return context.proceed();
            } catch (Exception e) {
                span.setTag("error", true);
                span.log(mapOf("error.object", e.getClass().getName(),
                               "event", "error"));
                throw e;
            } finally {
                span.finish();
            }
        } else {
            return context.proceed();
        }
    }

    private <E extends Member & AnnotatedElement> String spanName(Class<?> declaringClass, E element) {
        return declaringClass.getName() + "." + element.getName();
    }

    private Optional<SpanContext> locateParent() {
        Optional<Context> context = Contexts.context();

        return OptionalHelper.from(context.flatMap(ctx -> ctx.get(SpanContext.class)))
                .or(() -> context.flatMap(ctx -> ctx.get(ClientTracingFilter.class, SpanContext.class)))
                .asOptional();
    }

    private Tracer locateTracer() {
        return Contexts.context()
                .flatMap(ctx -> ctx.get(Tracer.class))
                .orElseGet(GlobalTracer::get);
    }

}
