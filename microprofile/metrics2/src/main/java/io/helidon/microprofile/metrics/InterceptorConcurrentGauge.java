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

package io.helidon.microprofile.metrics;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.ConcurrentGauge;

import static org.eclipse.microprofile.metrics.MetricType.CONCURRENT_GAUGE;

/**
 * Interceptor for {@link ConcurrentGauge} annotation.
 */
@ConcurrentGauge
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 11)
final class InterceptorConcurrentGauge
        extends InterceptorBase<org.eclipse.microprofile.metrics.ConcurrentGauge, ConcurrentGauge> {

    @Inject
    InterceptorConcurrentGauge(MetricRegistry registry) {
        super(registry,
                ConcurrentGauge.class,
                ConcurrentGauge::name,
                ConcurrentGauge::tags,
                ConcurrentGauge::absolute,
                MetricRegistry::getConcurrentGauges,
                CONCURRENT_GAUGE.toString());
    }

    @Override
    protected Object prepareAndInvoke(org.eclipse.microprofile.metrics.ConcurrentGauge concurrentGauge,
                                      ConcurrentGauge annotation, InvocationContext context) throws Exception {
        concurrentGauge.inc();
        return context.proceed();
    }

    @Override
    protected void postInvoke(org.eclipse.microprofile.metrics.ConcurrentGauge concurrentGauge,
                              ConcurrentGauge annotation, InvocationContext context, Exception ex) throws Exception {
        concurrentGauge.dec();
    }
}
