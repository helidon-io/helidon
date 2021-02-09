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
package io.helidon.integrations.micrometer;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 8)
final class InterceptorCounted extends InterceptorBase<Counter, Counted> {

    @Inject
    InterceptorCounted(MeterRegistry meterRegistry) {
        super(meterRegistry,
                Counted.class,
                Counted::value,
                Counted::extraTags,
                (reg, name, tags) -> reg.counter(name, tags),
                "counted");
    }

    @Override
    protected Object prepareAndInvoke(Counter counter, Counted annotation, InvocationContext context) throws Exception {

        Throwable throwable = null;

        try {
            return context.proceed();
        } catch (Throwable t) {
            throwable = t;
            throw t;
        } finally {
            if (throwable != null || !annotation.recordFailuresOnly()) {
                counter.increment();
            }
        }
    }
}
