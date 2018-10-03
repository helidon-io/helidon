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

import com.netflix.hystrix.HystrixCommandProperties;
import org.eclipse.microprofile.faulttolerance.Asynchronous;
import org.eclipse.microprofile.faulttolerance.Bulkhead;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

/**
 * Class MethodIntrospector.
 */
class MethodIntrospector {

    private final Method method;

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
    MethodIntrospector(Method method) {
        this.method = method;
        this.retry = isAnnotationPresent(Retry.class) ? new RetryAntn(method) : null;
        this.fallback = isAnnotationPresent(Fallback.class) ? new FallbackAntn(method) : null;
        this.circuitBreaker = isAnnotationPresent(CircuitBreaker.class) ? new CircuitBreakerAntn(method) : null;
        this.timeout = isAnnotationPresent(Timeout.class) ? new TimeoutAntn(method) : null;
        this.bulkhead = isAnnotationPresent(Bulkhead.class) ? new BulkheadAntn(method) : null;
        validate();
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
        return isAnnotationPresent(Asynchronous.class);
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
     * Search for annotation first on the method and then its class. This method
     * does not check if the annotation's target is of element type {@link
     * java.lang.annotation.ElementType#TYPE}.
     *
     * @param clazz Annotation class to search for.
     * @param <T> Annotation type.
     * @return Annotation instance or {@code null} if not found.
     */
    private <T extends Annotation> T getAnnotation(Class<T> clazz) {
        final T annotation = method.getAnnotation(clazz);
        return annotation != null ? annotation : method.getDeclaringClass().getAnnotation(clazz);
    }

    /**
     * Determines if annotation type is present on the method or its class.
     *
     * @param clazz Annotation class to search for.
     * @return Outcome of test.
     */
    private boolean isAnnotationPresent(Class<? extends Annotation> clazz) {
        return getAnnotation(clazz) != null;
    }
}
