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

package io.helidon.microprofile.metrics;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.helidon.common.metrics.InternalBridge.MetricRegistry;

import org.eclipse.microprofile.metrics.Counter;
import org.eclipse.microprofile.metrics.annotation.Counted;

/**
 * Interceptor for {@link Counted} annotation.
 */
@Counted
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 8)
final class InterceptorCounted extends InterceptorBase<Counter, Counted> {

    @Inject
    InterceptorCounted(MetricRegistry registry) {
        super(registry,
              Counted.class,
              Counted::name,
              Counted::absolute,
              MetricRegistry::getBridgeCounters,
              "counter");
    }

    @Override
    protected Object prepareAndInvoke(Counter counter,
                                      Counted annot,
                                      InvocationContext context) throws Exception {
        counter.inc();
        return context.proceed();
    }

    // TODO -- Metrics 1.1-20 incompatibility: Counter
//    @Override
//    protected void postInvoke(Counter counter,
//                              Counted annot,
//                              InvocationContext context,
//                              Exception ex) {
//        if (!annot.monotonic()) {
//            counter.dec();
//        }
//    }
}
