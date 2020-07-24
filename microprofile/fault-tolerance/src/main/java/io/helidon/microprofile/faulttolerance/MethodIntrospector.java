/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates. All rights reserved.
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
import java.util.logging.Logger;

import io.helidon.microprofile.faulttolerance.MethodAntn.LookupResult;

import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import static io.helidon.microprofile.faulttolerance.MethodAntn.lookupAnnotation;
import static io.helidon.microprofile.faulttolerance.FaultToleranceParameter.getParameter;

/**
 * Class MethodIntrospector.
 */
class MethodIntrospector {
    private static final Logger LOGGER = Logger.getLogger(MethodIntrospector.class.getName());

    private final Method method;

    private final Class<?> beanClass;

    private final Retry retry;

    private final Fallback fallback;

    private final CircuitBreaker circuitBreaker;

    private final Timeout timeout;

    private final Bulkhead bulkhead;

    /**
     * Constructor.
     *
     * @param method The method to introspect.
     */
    MethodIntrospector(Class<?> beanClass, Method method) {
        this.beanClass = beanClass;
        this.method = method;

        this.retry = isAnnotationEnabled(Retry.class) ? new RetryAntn(beanClass, method) : null;
        this.circuitBreaker = isAnnotationEnabled(CircuitBreaker.class)
                ? new CircuitBreakerAntn(beanClass, method) : null;
        this.timeout = isAnnotationEnabled(Timeout.class) ? new TimeoutAntn(beanClass, method) : null;
        this.bulkhead = isAnnotationEnabled(Bulkhead.class) ? new BulkheadAntn(beanClass, method) : null;
        this.fallback = isAnnotationEnabled(Fallback.class) ? new FallbackAntn(beanClass, method) : null;
    }

    Method method() {
        return method;
    }

    /**
     * Checks if {@code clazz} is assignable from the method's return type.
     *
     * @param clazz The class.
     * @return Outcome of test.
     */
    boolean isReturnType(Class<?> clazz) {
        return clazz.isAssignableFrom(method.getReturnType());
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

    /**
     * Determines if annotation type is present and enabled.
     *
     * @param clazz Annotation class to search for.
     * @return Outcome of test.
     */
    private boolean isAnnotationEnabled(Class<? extends Annotation> clazz) {
        LookupResult<? extends Annotation> lookupResult = lookupAnnotation(beanClass, method, clazz);
        if (lookupResult == null) {
            return false;       // not present
        }

        String value;
        final String annotationType = clazz.getSimpleName();

        // Check if property defined at method level
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
