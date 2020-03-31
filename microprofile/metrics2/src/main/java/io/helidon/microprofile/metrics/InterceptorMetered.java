/*
 * Copyright (c) 2018, 2020 Oracle and/or its affiliates.
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

import org.eclipse.microprofile.metrics.Meter;
import org.eclipse.microprofile.metrics.MetricRegistry;
import org.eclipse.microprofile.metrics.annotation.Metered;

/**
 * Interceptor for {@link Metered} annotation.
 */
@Metered
@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 9)
final class InterceptorMetered extends InterceptorBase<Meter, Metered> {

    @Inject
    InterceptorMetered(MetricRegistry registry) {
        super(registry,
              Metered.class,
              Metered::name,
              Metered::tags,
              Metered::absolute,
              "meter",
              org.eclipse.microprofile.metrics.Meter.class);
    }

    @Override
    protected Object prepareAndInvoke(Meter meter, Metered annotation, InvocationContext context) throws Exception {
        meter.mark();
        return context.proceed();
    }
}

