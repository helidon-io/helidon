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

import java.lang.reflect.Method;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

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
     * Intercepts a call to bean method annotated by {@link io.helidon.microprofile.cdi.ExecuteOn}.
     *
     * @param context Invocation context.
     * @return Whatever the intercepted method returns.
     * @throws Throwable If a problem occurs.
     */
    @AroundInvoke
    @SuppressWarnings("unchecked")
    public Object executeOn(InvocationContext context) throws Throwable {
        Method method = context.getMethod();
        ExecuteOn executeOn = extension.getAnnotation(method);

        // find executor service to use
        ExecutorService executorService = switch (executeOn.value()) {
            case PLATFORM -> PLATFORM_EXECUTOR_SERVICE.get();
            case VIRTUAL -> VIRTUAL_EXECUTOR_SERVICE.get();
            case EXECUTOR -> findExecutor(executeOn.executorName());
        };

        switch (extension.getMethodType(method)) {
        case BLOCKING:
            // block until call completes
            return executorService.submit(context::proceed).get(executeOn.timeout(), executeOn.unit());
        case NON_BLOCKING:
            // execute call asynchronously
            CompletableFuture<?> supplyFuture = CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return context.proceed();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, executorService);

            // return new, cancellable completable future
            AtomicBoolean mayInterrupt = new AtomicBoolean(false);
            CompletableFuture<Object> resultFuture = new CompletableFuture<>() {
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) {
                    mayInterrupt.set(mayInterruptIfRunning);
                    return super.cancel(mayInterruptIfRunning);
                }
            };

            // link completion of supplyFuture with resultFuture
            supplyFuture.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    // result must be CompletionStage or CompletableFuture
                    CompletableFuture<Object> cfResult = !(result instanceof CompletableFuture<?>)
                            ? ((CompletionStage<Object>) result).toCompletableFuture()
                            : (CompletableFuture<Object>) result;
                    cfResult.whenComplete((r, t) -> {
                        if (t == null) {
                            resultFuture.complete(r);
                        } else {
                            resultFuture.completeExceptionally(unwrapThrowable(t));
                        }
                    });
                } else {
                    resultFuture.completeExceptionally(unwrapThrowable(throwable));
                }
            });

            // if resultFuture is cancelled, then cancel supplyFuture
            resultFuture.exceptionally(t -> {
                if (t instanceof CancellationException) {
                    supplyFuture.cancel(mayInterrupt.get());
                }
                return null;
            });

            return resultFuture;
        default:
            throw new IllegalStateException("Unrecognized ExecuteOn method type");
        }
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

    /**
     * Extract underlying throwable.
     *
     * @param t the throwable
     * @return the wrapped throwable
     */
    private static Throwable unwrapThrowable(Throwable t) {
        return t instanceof ExecutionException ? t.getCause() : t;
    }
}
