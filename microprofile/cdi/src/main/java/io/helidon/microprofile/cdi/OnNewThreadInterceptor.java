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

import io.helidon.common.LazyValue;
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
@OnNewThread
@Priority(Interceptor.Priority.PLATFORM_AFTER + 10)
class OnNewThreadInterceptor {

    private static final String ON_NEW_THREAD = "on-new-thread";
    private static final String EXECUTOR_SERVICE_CONFIG = "helidon.on-new-thread.executor-service";

    private static final LazyValue<ExecutorService> PLATFORM_EXECUTOR_SERVICE
            = LazyValue.create(() -> {
                    Config mpConfig = ConfigProvider.getConfig();
                    io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);
                    return ThreadPoolSupplier.builder()
                                             .threadNamePrefix(ON_NEW_THREAD)
                                             .config(config.get(EXECUTOR_SERVICE_CONFIG))
                                             .virtualThreads(false)        // overrides to platform threads
                                             .build()
                                             .get();
            });

    private static final LazyValue<ExecutorService> VIRTUAL_EXECUTOR_SERVICE
            = LazyValue.create(() -> {
                Config mpConfig = ConfigProvider.getConfig();
                io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);
                return ThreadPoolSupplier.builder()
                                         .threadNamePrefix(ON_NEW_THREAD)
                                         .config(config.get(EXECUTOR_SERVICE_CONFIG))
                                         .virtualThreads(true)              // overrides to virtual threads
                                         .build()
                                         .get();
            });

    @Inject
    private OnNewThreadExtension extension;

    /**
     * Intercepts a call to bean method annotated by {@code @OnNewThread}.
     *
     * @param context Invocation context.
     * @return Whatever the intercepted method returns.
     * @throws Throwable If a problem occurs.
     */
    @AroundInvoke
    public Object runOnNewThread(InvocationContext context) throws Throwable {
        OnNewThread onNewThread = extension.getAnnotation(context.getMethod());
        return switch (onNewThread.value()) {
            case PLATFORM -> PLATFORM_EXECUTOR_SERVICE.get()
                    .submit(context::proceed)
                    .get(onNewThread.timeout(), onNewThread.unit());
            case VIRTUAL -> VIRTUAL_EXECUTOR_SERVICE.get()
                    .submit(context::proceed)
                    .get(onNewThread.timeout(), onNewThread.unit());
        };
    }
}
