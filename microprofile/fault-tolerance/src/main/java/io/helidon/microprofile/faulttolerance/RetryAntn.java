/*
 * Copyright (c) 2018, 2021 Oracle and/or its affiliates. All rights reserved.
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
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;

/**
 * Class RetryAntn.
 */
class RetryAntn extends MethodAntn implements Retry {

    /**
     * Constructor.
     *
     * @param beanClass Bean class.
     * @param method The method.
     */
    RetryAntn(Class<?> beanClass, Method method) {
        super(beanClass, method);
    }

    @Override
    public void validate() {
        if (maxRetries() < -1) {
            throw new FaultToleranceDefinitionException("Invalid @Retry annotation, "
                                                        + "maxRetries must be >= -1");
        }
        if (delay() < 0) {
            throw new FaultToleranceDefinitionException("Invalid @Retry annotation, "
                                                        + "delay must be >= 0");
        }
        Duration delay = Duration.of(delay(), delayUnit());
        Duration maxDuration = Duration.of(maxDuration(), durationUnit());
        if (maxDuration.compareTo(delay) < 0) {
            throw new FaultToleranceDefinitionException("Invalid @Retry annotation, "
                                                        + "maxDuration must be >= delay");
        }
        if (jitter() < 0) {
            throw new FaultToleranceDefinitionException("Invalid @Retry annotation, "
                                                        + "jitter must be >= 0");
        }
    }

    @Override
    public int maxRetries() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("maxRetries", lookupResult.getType());
        return override != null ? Integer.parseInt(override) : lookupResult.getAnnotation().maxRetries();
    }

    @Override
    public long delay() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("delay", lookupResult.getType());
        return override != null ? Long.parseLong(override) : lookupResult.getAnnotation().delay();
    }

    @Override
    public ChronoUnit delayUnit() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("delayUnit", lookupResult.getType());
        return override != null ? ChronoUnit.valueOf(override) : lookupResult.getAnnotation().delayUnit();
    }

    @Override
    public long maxDuration() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("maxDuration", lookupResult.getType());
        return override != null ? Long.parseLong(override) : lookupResult.getAnnotation().maxDuration();
    }

    @Override
    public ChronoUnit durationUnit() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("durationUnit", lookupResult.getType());
        return override != null ? ChronoUnit.valueOf(override) : lookupResult.getAnnotation().durationUnit();
    }

    @Override
    public long jitter() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("jitter", lookupResult.getType());
        return override != null ? Long.parseLong(override) : lookupResult.getAnnotation().jitter();
    }

    @Override
    public ChronoUnit jitterDelayUnit() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("jitterDelayUnit", lookupResult.getType());
        return override != null ? ChronoUnit.valueOf(override) : lookupResult.getAnnotation().jitterDelayUnit();
    }

    @Override
    public Class<? extends Throwable>[] retryOn() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("retryOn", lookupResult.getType());
        return override != null ? parseThrowableArray(override) : lookupResult.getAnnotation().retryOn();
    }

    @Override
    public Class<? extends Throwable>[] abortOn() {
        LookupResult<Retry> lookupResult = lookupAnnotation(Retry.class);
        final String override = getParamOverride("abortOn", lookupResult.getType());
        return override != null ? parseThrowableArray(override) : lookupResult.getAnnotation().abortOn();
    }
}
