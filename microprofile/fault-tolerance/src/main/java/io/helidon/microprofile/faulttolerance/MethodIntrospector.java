/*
 * Copyright (c) 2018, 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.faulttolerance;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;

import io.helidon.microprofile.faulttolerance.MethodAntn.LookupResult;

import jakarta.enterprise.inject.spi.AnnotatedMethod;
import jakarta.enterprise.inject.spi.AnnotatedType;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.metrics.Tag;

import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationFallback.APPLIED;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationFallback.NOT_APPLIED;
import static io.helidon.microprofile.faulttolerance.FaultToleranceMetrics.InvocationFallback.NOT_DEFINED;
import static io.helidon.microprofile.faulttolerance.FaultToleranceParameter.getParameter;
import static io.helidon.microprofile.faulttolerance.MethodAntn.lookupAnnotation;

class MethodIntrospector {

    private final AnnotatedMethod<?> annotatedMethod;

    private final Retry retry;

    private final Fallback fallback;

    private final CircuitBreaker circuitBreaker;

    private final Timeout timeout;

    private final Bulkhead bulkhead;

    private final WithExecutor withExecutor;

    private Tag methodNameTag;

    /**
     * Constructor.
     *
     * @param method The method to introspect.
     */
    @SuppressWarnings("unchecked")
    MethodIntrospector(Class<?> beanClass, Method method) {
        BeanManager bm = CDI.current().getBeanManager();
        AnnotatedType<?> annotatedType = bm.createAnnotatedType(beanClass);
        Optional<AnnotatedMethod<?>> annotatedMethodOptional =
                (Optional<AnnotatedMethod<?>>) annotatedType.getMethods()
                        .stream()
                        .filter(am -> am.getJavaMember().equals(method))
                        .findFirst();
        this.annotatedMethod = annotatedMethodOptional.orElse(new FtAnnotatedMethod(method));

        this.retry = isAnnotationEnabled(Retry.class) ? new RetryAntn(annotatedMethod) : null;
        this.circuitBreaker = isAnnotationEnabled(CircuitBreaker.class)
                ? new CircuitBreakerAntn(annotatedMethod) : null;
        this.timeout = isAnnotationEnabled(Timeout.class) ? new TimeoutAntn(annotatedMethod) : null;
        this.bulkhead = isAnnotationEnabled(Bulkhead.class) ? new BulkheadAntn(annotatedMethod) : null;
        this.fallback = isAnnotationEnabled(Fallback.class) ? new FallbackAntn(annotatedMethod) : null;
        this.withExecutor = method.getAnnotation(WithExecutor.class);
    }

    /**
     * Checks if {@code @Retry} is present.
     *
     * @return Outcome of test.
     */
    boolean hasRetry() {
        return retry != null;
    }

    Retry getRetry() {
        return retry;
    }

    /**
     * Checks if {@code @Fallback} is present.
     *
     * @return Outcome of test.
     */
    boolean hasFallback() {
        return fallback != null;
    }

    Fallback getFallback() {
        return fallback;
    }

    boolean isAsynchronous() {
        return isAnnotationEnabled(Asynchronous.class);
    }

    /**
     * Checks if {@code @CircuitBreaker} is present.
     *
     * @return Outcome of test.
     */
    boolean hasCircuitBreaker() {
        return circuitBreaker != null;
    }

    CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Checks if {@code @Timeout} is present.
     *
     * @return Outcome of test.
     */
    boolean hasTimeout() {
        return timeout != null;
    }

    Timeout getTimeout() {
        return timeout;
    }

    /**
     * Checks if {@code @Bulkhead} is present.
     *
     * @return Outcome of test.
     */
    boolean hasBulkhead() {
        return bulkhead != null;
    }

    Bulkhead getBulkhead() {
        return bulkhead;
    }

    boolean hasWithExecutor() {
        return withExecutor != null;
    }

    WithExecutor withExecutor() {
        return withExecutor;
    }

    /**
     * Returns a metric's tag with the fully qualified method name.
     *
     * @return the tag
     */
    Tag getMethodNameTag() {
        if (methodNameTag == null) {
            Method method = annotatedMethod.getJavaMember();
            String name = method.getDeclaringClass().getName() + "." + method.getName();
            methodNameTag = new Tag("method", name);
        }
        return methodNameTag;
    }

    /**
     * Returns a fallback tag based on the {@code fallbackCalled} parameter.
     *
     * @param fallbackCalled indicates if fallback logic was called or not
     * @return the tag
     */
    Tag getFallbackTag(boolean fallbackCalled) {
        return !hasFallback() ? NOT_DEFINED.get()
                : fallbackCalled ? APPLIED.get() : NOT_APPLIED.get();
    }

    /**
     * Determines if annotation type is present and enabled.
     *
     * @param clazz Annotation class to search for.
     * @return Outcome of test.
     */
    private boolean isAnnotationEnabled(Class<? extends Annotation> clazz) {
        BeanManager bm = CDI.current().getBeanManager();
        LookupResult<? extends Annotation> lookupResult = lookupAnnotation(annotatedMethod, clazz, bm);
        if (lookupResult == null) {
            return false;       // not present
        }

        String value;
        final String annotationType = clazz.getSimpleName();

        // Check if property defined at method level
        Method method = annotatedMethod.getJavaMember();
        value = getParameter(method.getDeclaringClass().getName(), method.getName(),
                annotationType, "enabled");
        if (value != null) {
            return Boolean.parseBoolean(value);
        }

        // Check if property defined at class level
        value = getParameter(method.getDeclaringClass().getName(), annotationType, "enabled");
        if (value != null) {
            return Boolean.parseBoolean(value);
        }

        // Check if property defined at global level
        value = getParameter(annotationType, "enabled");
        if (value != null) {
            return Boolean.parseBoolean(value);
        }

        // Default is enabled
        return clazz == Fallback.class || FaultToleranceExtension.isFaultToleranceEnabled();
    }
}
