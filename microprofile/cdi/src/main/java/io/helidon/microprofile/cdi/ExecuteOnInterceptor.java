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
import jakarta.enterprise.inject.literal.NamedLiteral;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.CDI;
import jakarta.inject.Inject;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

/**
 * Intercepts calls to bean methods to be executed on a new thread.
 */
@Interceptor
@ExecuteOn
@Priority(Interceptor.Priority.PLATFORM_AFTER + 10)
class ExecuteOnInterceptor {

    private static final String EXECUTE_ON = "execute-on";
    private static final String RUN_ON_VIRTUAL_THREAD = EXECUTE_ON + ".virtual";
    private static final String RUN_ON_PLATFORM_THREAD = EXECUTE_ON + ".platform";

    private static final LazyValue<ExecutorService> PLATFORM_EXECUTOR_SERVICE
            = LazyValue.create(() -> {
                    Config mpConfig = ConfigProvider.getConfig();
                    io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);
                    return ThreadPoolSupplier.builder()
                                             .threadNamePrefix(EXECUTE_ON)
                                             .config(config.get(RUN_ON_PLATFORM_THREAD))
                                             .virtualThreads(false)        // overrides to platform threads
                                             .build()
                                             .get();
            });

    private static final LazyValue<ExecutorService> VIRTUAL_EXECUTOR_SERVICE
            = LazyValue.create(() -> {
                Config mpConfig = ConfigProvider.getConfig();
                io.helidon.config.Config config = MpConfig.toHelidonConfig(mpConfig);
                String threadNamePrefix = config.get(RUN_ON_VIRTUAL_THREAD)
                                                .get("thread-name-prefix")
                                                .asString()
                                                .asOptional()
                                                .orElse(EXECUTE_ON);
                return ThreadPoolSupplier.builder()
                                         .threadNamePrefix(threadNamePrefix)
                                         .virtualThreads(true)
                                         .build()
                                         .get();
            });

    @Inject
    private ExecuteOnExtension extension;

    /**
     * Intercepts a call to bean method annotated by {@code @OnNewThread}.
     *
     * @param context Invocation context.
     * @return Whatever the intercepted method returns.
     * @throws Throwable If a problem occurs.
     */
    @AroundInvoke
    public Object executeOn(InvocationContext context) throws Throwable {
        ExecuteOn executeOn = extension.getAnnotation(context.getMethod());
        return switch (executeOn.value()) {
            case PLATFORM -> PLATFORM_EXECUTOR_SERVICE.get()
                    .submit(context::proceed)
                    .get(executeOn.timeout(), executeOn.unit());
            case VIRTUAL -> VIRTUAL_EXECUTOR_SERVICE.get()
                    .submit(context::proceed)
                    .get(executeOn.timeout(), executeOn.unit());
            case EXECUTOR -> findExecutor(executeOn.executorName())
                    .submit(context::proceed)
                    .get(executeOn.timeout(), executeOn.unit());
        };
    }

    /**
     * Find executor by name. Validation in {@link ExecuteOnExtension#validateAnnotations(BeanManager, Object)}.
     *
     * @param executorName name of executor
     * @return executor instance looked up via CDI
     */
    private static ExecutorService findExecutor(String executorName) {
        return CDI.current().select(ExecutorService.class, NamedLiteral.of(executorName)).get();
    }
}
