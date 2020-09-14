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

import java.lang.reflect.Method;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

/**
 * Class CircuitBreakerAntn.
 */
class CircuitBreakerAntn extends MethodAntn implements CircuitBreaker {

    /**
     * Constructor.
     *
     * @param beanClass The bean class.
     * @param method The method.
     */
    CircuitBreakerAntn(Class<?> beanClass, Method method) {
        super(beanClass, method);
    }

    @Override
    public void validate() {
        if (delay() < 0) {
            throw new FaultToleranceDefinitionException("Invalid @CircuitBreaker annotation, "
                                                        + "delay must be >= 0");
        }
        if (requestVolumeThreshold() < 1) {
            throw new FaultToleranceDefinitionException("Invalid @CircuitBreaker annotation, "
                                                        + "requestVolumeThreshold must be >= 1");
        }
        double failureRatio = failureRatio();
        if (failureRatio < 0 || failureRatio > 1) {
            throw new FaultToleranceDefinitionException("Invalid @CircuitBreaker annotation, "
                                                        + "failureRatio must be >= 0 and <= 1");
        }
        if (successThreshold() < 1) {
            throw new FaultToleranceDefinitionException("Invalid @CircuitBreaker annotation, "
                                                        + "successThreshold must be >= 1");
        }
    }

    @Override
    public long delay() {
        LookupResult<CircuitBreaker> lookupResult = lookupAnnotation(CircuitBreaker.class);
        final String override = getParamOverride("delay", lookupResult.getType());
        return override != null ? Long.parseLong(override) : lookupResult.getAnnotation().delay();
    }

    @Override
    public ChronoUnit delayUnit() {
        LookupResult<CircuitBreaker> lookupResult = lookupAnnotation(CircuitBreaker.class);
        final String override = getParamOverride("delayUnit", lookupResult.getType());
        return override != null ? ChronoUnit.valueOf(override) : lookupResult.getAnnotation().delayUnit();
    }

    @Override
    public int requestVolumeThreshold() {
        LookupResult<CircuitBreaker> lookupResult = lookupAnnotation(CircuitBreaker.class);
        final String override = getParamOverride("requestVolumeThreshold", lookupResult.getType());
        return override != null ? Integer.parseInt(override) : lookupResult.getAnnotation().requestVolumeThreshold();
    }

    @Override
    public double failureRatio() {
        LookupResult<CircuitBreaker> lookupResult = lookupAnnotation(CircuitBreaker.class);
        final String override = getParamOverride("failureRatio", lookupResult.getType());
        return override != null ? Double.parseDouble(override) : lookupResult.getAnnotation().failureRatio();
    }

    @Override
    public int successThreshold() {
        LookupResult<CircuitBreaker> lookupResult = lookupAnnotation(CircuitBreaker.class);
        final String override = getParamOverride("successThreshold", lookupResult.getType());
        return override != null ? Integer.parseInt(override) : lookupResult.getAnnotation().successThreshold();
    }

    @Override
    public Class<? extends Throwable>[] failOn() {
        LookupResult<CircuitBreaker> lookupResult = lookupAnnotation(CircuitBreaker.class);
        final String override = getParamOverride("failOn", lookupResult.getType());
        return override != null ? parseThrowableArray(override) : lookupResult.getAnnotation().failOn();
    }

    @Override
    public Class<? extends Throwable>[] skipOn() {
        LookupResult<CircuitBreaker> lookupResult = lookupAnnotation(CircuitBreaker.class);
        final String override = getParamOverride("skipOn", lookupResult.getType());
        return override != null ? parseThrowableArray(override) : lookupResult.getAnnotation().skipOn();
    }
}
