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
package io.helidon.integrations.micrometer.cdi;

import java.time.Duration;

import javax.annotation.Priority;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

import io.helidon.integrations.micrometer.cdi.MicrometerCdiExtension.MeterWorkItem;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Timer;

@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 8)
final class InterceptorTimed extends MicrometerInterceptorBase<Timer> {

    private long startTimeNanos;

    InterceptorTimed() {
        super(Timed.class, Timer.class);
    }

    @Override
    protected void preInvoke(Timer timer) {
        startTimeNanos = System.nanoTime();
    }

    public void postComplete(Timer timer) {
        timer.record(Duration.ofNanos(System.nanoTime() - startTimeNanos));
    }
}
