/*
 * Copyright (c) 2018 Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import io.helidon.microprofile.faulttolerance.MethodAntn.LookupResult;

import com.netflix.hystrix.HystrixCommandProperties;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

import static io.helidon.microprofile.faulttolerance.FaultToleranceParameter.getParameter;
import static io.helidon.microprofile.faulttolerance.MethodAntn.lookupAnnotation;

/**
 * Class MethodIntrospector.
 */
class MethodIntrospector {

    private final Method method;

    private final Class<?> beanClass;

    private Retry retry;

    private Fallback fallback;

    private CircuitBreaker circuitBreaker;

    private Timeout timeout;

    private Bulkhead bulkhead;

    /**
     * Constructor.
     *
     * @param method The method to introspect.
     */
    MethodIntrospector(Class<?> beanClass, Method method) {
        this.beanClass = beanClass;
        this.method = method;

        // Only process annotations if FT is enabled
        if (FaultToleranceExtension.isFaultToleranceEnabled()) {
            this.retry = isAnnotationEnabled(Retry.class) ? new RetryAntn(beanClass, method) : null;
            this.circuitBreaker = isAnnotationEnabled(CircuitBreaker.class)
                    ? new CircuitBreakerAntn(beanClass, method) : null;
            this.timeout = isAnnotationEnabled(Timeout.class) ? new TimeoutAntn(beanClass, method) : null;
            this.bulkhead = isAnnotationEnabled(Bulkhead.class) ? new BulkheadAntn(beanClass, method) : null;
            validate();
        }

        // Fallback is always enabled
        this.fallback = isAnnotationEnabled(Fallback.class) ? new FallbackAntn(beanClass, method) : null;
    }

    Method getMethod() {
        return method;
    }

    /**
     * Validates that use of annotations matches specification.
     *
     * @throws FaultToleranceDefinitionException If validation fails.
     */
    private void validate() {
        if (isAsynchronous()) {
            final Class<?> returnType = method.getReturnType();
            if (!Future.class.isAssignableFrom(returnType)) {
                throw new FaultToleranceDefinitionException("Asynchronous method '" + method.getName()
                                                            + "' must return Future");
            }
        }
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
     * Returns a collection of Hystrix properties needed to configure
     * commands. These properties are derived from the set of annotations
     * found on a method or its class.
     *
     * @return The collection of Hystrix properties.
     */
    Map<String, Object> getHystrixProperties() {
        final HashMap<String, Object> result = new HashMap<>();

        // Isolation strategy
        result.put("execution.isolation.strategy", HystrixCommandProperties.ExecutionIsolationStrategy.THREAD);

        // Circuit breakers
        result.put("circuitBreaker.enabled", hasCircuitBreaker());
        if (hasCircuitBreaker()) {
            final CircuitBreaker circuitBreaker = getCircuitBreaker();
            result.put("circuitBreaker.requestVolumeThreshold",
                       circuitBreaker.requestVolumeThreshold());
            result.put("circuitBreaker.errorThresholdPercentage",
                       (int) (circuitBreaker.failureRatio() * 100));
            result.put("circuitBreaker.sleepWindowInMilliseconds",
                       TimeUtil.convertToMillis(circuitBreaker.delay(), circuitBreaker.delayUnit()));
        }

        // Timeouts
        result.put("execution.timeout.enabled", hasTimeout());
        if (hasTimeout()) {
            final Timeout timeout = getTimeout();
            result.put("execution.isolation.thread.timeoutInMilliseconds",
                       TimeUtil.convertToMillis(timeout.value(), timeout.unit()));
        }

        return result;
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
            return Boolean.valueOf(value);
        }

        // Check if property defined at class level
        value = getParameter(method.getDeclaringClass().getName(), annotationType, "enabled");
        if (value != null) {
            return Boolean.valueOf(value);
        }

        // Check if property defined at global level
        value = getParameter(annotationType, "enabled");
        if (value != null) {
            return Boolean.valueOf(value);
        }

        // Default is enabled
        return true;
    }
}
