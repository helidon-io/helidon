/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
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

package io.helidon.microprofile.cdi;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import io.helidon.common.configurable.ThreadPoolSupplier;

import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;

/**
 * Intercepts calls to bean methods to be asynchronously executed on a separate
 * platform thread.
 */
@Interceptor
@AsyncPlatform
@Priority(Interceptor.Priority.PLATFORM_AFTER + 10)
class AsyncPlatformInterceptor {

    private static final AtomicReference<ExecutorService> EXECUTOR_SERVICE = new AtomicReference<>();

    AsyncPlatformInterceptor() {
        if (EXECUTOR_SERVICE.get() == null) {
            EXECUTOR_SERVICE.compareAndSet(null,
                    ThreadPoolSupplier.builder()        // TODO config
                                      .virtualThreads(false)
                                      .build()
                                      .get());
        }
    }

    /**
     * Intercepts a call to bean method annotated by {@code @AsyncPlatform}.
     *
     * @param context Invocation context.
     * @return Whatever the intercepted method returns.
     * @throws Throwable If a problem occurs.
     */
    @AroundInvoke
    public Object runWithExecutor(InvocationContext context) throws Throwable {
        AsyncPlatform asyncPlatform = context.getMethod().getAnnotation(AsyncPlatform.class);
        Future<Object> future = EXECUTOR_SERVICE.get().submit(context::proceed);
        return future.get(asyncPlatform.value(), asyncPlatform.unit());
    }
}
