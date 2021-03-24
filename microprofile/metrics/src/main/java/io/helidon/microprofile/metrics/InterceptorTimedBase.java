/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
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
 *
 */
package io.helidon.microprofile.metrics;

import java.lang.annotation.Annotation;
import java.time.Duration;

import org.eclipse.microprofile.metrics.Metric;

/**
 * Common behavior for interceptors that handle timing, specifically ones which need to capture a start time before we
 * invoke the intercepted method.
 *
 * @param <T> type of the metric the interceptor updates
 */
abstract class InterceptorTimedBase<T extends Metric> extends MetricsInterceptorBase.WithPostComplete<T> {

    private long startNanos;

    InterceptorTimedBase(Class<? extends Annotation> annotationType, Class<T> metricType) {
        super(annotationType, metricType);
    }

    void preInvoke(T metric) {
        startNanos = System.nanoTime();
    }

    Duration duration() {
        return Duration.ofNanos(durationNanoseconds());
    }

    long durationNanoseconds() {
        return System.nanoTime() - startNanos;
    }
}
