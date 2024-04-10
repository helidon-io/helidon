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
import io.helidon.config.mp.MpConfig;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Intercepts calls to bean methods to be asynchronously executed on a separate
 * platform thread.
 */
@Interceptor
@AsyncPlatform
@Priority(Interceptor.Priority.PLATFORM_AFTER + 10)
class AsyncPlatformInterceptor {

    private static final String ASYNC_PLATFORM_THREAD = "async-platform-thread";
    private static final String EXECUTOR_SERVICE_CONFIG = "helidon.async-platform.executor-service";

    private static final AtomicReference<ExecutorService> EXECUTOR_SERVICE = new AtomicReference<>();

    AsyncPlatformInterceptor() {
        if (EXECUTOR_SERVICE.get() == null) {
            Config mpConfig = ConfigProvider.getConfig();
            io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);
            EXECUTOR_SERVICE.compareAndSet(null,
                    ThreadPoolSupplier.builder()
                                      .threadNamePrefix(ASYNC_PLATFORM_THREAD)
                                      .config(config.get(EXECUTOR_SERVICE_CONFIG))
                                      .virtualThreads(false)        // platform threads
                                      .build()
                                      .get());
        }
    }

    @Inject
    private AsyncPlatformExtension extension;

    /**
     * Intercepts a call to bean method annotated by {@code @AsyncPlatform}.
     *
     * @param context Invocation context.
     * @return Whatever the intercepted method returns.
     * @throws Throwable If a problem occurs.
     */
    @AroundInvoke
    public Object runWithExecutor(InvocationContext context) throws Throwable {
        AsyncPlatform asyncPlatform = extension.getAnnotation(context.getMethod());
        Future<Object> future = EXECUTOR_SERVICE.get().submit(context::proceed);
        return future.get(asyncPlatform.value(), asyncPlatform.unit());
    }
}
