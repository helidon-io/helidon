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

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.interceptor.Interceptor;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.instrument.Counter;

@Interceptor
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 8)
final class InterceptorCounted extends MicrometerInterceptorBase<Counter> {

    @Inject
    InterceptorCounted() {
        super(Counted.class,
                Counter.class);
    }

    @Override
    void preInvoke(Counter counter) {
        counter.increment();
    }
}
