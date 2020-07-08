/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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
package io.helidon.microprofile.faulttolerance;

import javax.interceptor.InvocationContext;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

import io.helidon.common.reactive.Single;
import io.helidon.faulttolerance.Async;
import io.helidon.faulttolerance.Fallback;
import io.helidon.faulttolerance.FaultTolerance;
import io.helidon.faulttolerance.FtHandlerTyped;
import io.helidon.faulttolerance.Retry;
import static io.helidon.microprofile.faulttolerance.AsynchronousUtil.toCompletionStageSupplier;

/**
 * Runs a FT method.
 */
public class CommandRunner implements FtSupplier<Object> {

    private final Method method;

    private final InvocationContext context;

    private final MethodIntrospector introspector;

    static final ConcurrentHashMap<Method, FtHandlerTyped<Object>> ftHandlers = new ConcurrentHashMap<>();

    /**
     * Constructor.
     *
     * @param context The invocation context.
     * @param introspector The method introspector.
     */
    public CommandRunner(InvocationContext context, MethodIntrospector introspector) {
        this.context = context;
        this.introspector = introspector;
        this.method = context.getMethod();
    }

    /**
     * Invokes the FT method.
     *
     * @return Value returned by method.
     */
    @Override
    public Object get() throws Throwable {
        // Lookup or create handler for this method
        FtHandlerTyped<Object> handler = ftHandlers.computeIfAbsent(method, method -> createHandler());

        Single<Object> single;
        if (introspector.isAsynchronous()) {
            // Invoke method in new thread and call get() to unwrap singles
            single = Async.create().invoke(() ->
                    handler.invoke(toCompletionStageSupplier(context::proceed))).get();

            // If return type is CompletionStage, convert it
            if (introspector.isReturnType(CompletionStage.class)) {
                return single.toStage();
            }

            // Otherwise, must be CompletableFuture or Future
            if (introspector.isReturnType(Future.class)) {
                return single.toCompletableFuture();
            }

            // Oops, something went wrong during validation
            throw new InternalError("Validation failed, return type must be Future or CompletionStage");
        } else {
            // Invoke method and wait on result
            single = handler.invoke(toCompletionStageSupplier(context::proceed));
            return single.get();
        }
    }

    /**
     * Creates a FT handler for a given method by inspecting annotations.
     *
     * @return Newly created FT handler.
     */
    private FtHandlerTyped<Object> createHandler() {
        FaultTolerance.TypedBuilder<Object> builder = FaultTolerance.typedBuilder();

        // Create retry handler first for priority
        if (introspector.hasRetry()) {
            Retry retry = Retry.builder()
                    .retryPolicy(Retry.JitterRetryPolicy.builder()
                            .calls(introspector.getRetry().maxRetries() + 1)
                            .delay(Duration.ofMillis(introspector.getRetry().delay()))
                            .jitter(Duration.ofMillis(introspector.getRetry().jitter()))
                            .build())
                    .overallTimeout(Duration.ofDays(1))     // TODO
                    .build();
            builder.addRetry(retry);
        }

        // Create fallback handler
        if (introspector.hasFallback()) {
            Fallback<Object> fallback = Fallback.builder()
                    .fallback(throwable -> {
                        CommandFallback cfb = new CommandFallback(context, introspector, throwable);
                        return toCompletionStageSupplier(cfb::execute).get();
                    })
                    .build();
            builder.addFallback(fallback);
        }

        return builder.build();
    }
}
