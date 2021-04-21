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
package io.helidon.servicecommon.restcdi;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.interceptor.InvocationContext;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.CompletionCallback;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.container.TimeoutHandler;

/**
 * A general-purpose implementation of {@link InterceptionRunner}, supporting asynchronous JAX-RS endpoints as indicated by the
 * presence of a {@code @Suspended AsyncResponse} parameter.
 */
class InterceptionRunnerImpl implements InterceptionRunner {

    private static final Logger LOGGER = Logger.getLogger(InterceptionRunnerImpl.class.getName());
    private static final String POST_INVOCATION_HANDLER_FAILURE = "Interceptor post-invocation handler failed; continuing";
    private static final String ERROR_DURING_INTERCEPTION = "Error during interception";

    /*
     * In this impl, constructor runners and synchronous method runners are identical and have no saved context at all, so we
     * can use the same instance for all except async method runners.
     */
    private static final InterceptionRunner INSTANCE = new InterceptionRunnerImpl();

    /**
     * Returns the appropriate {@code InterceptionRunner} for the executable.
     *
     * @param executable the {@code Constructor} or {@code Method} requiring interceptor support
     * @return the {@code InterceptionRunner}
     */
    static InterceptionRunner create(Executable executable) {
        if (executable instanceof Constructor<?>) {
            return INSTANCE;
        }
        if (executable instanceof Method) {
            final int asyncResponseSlot = InterceptionRunnerImpl.asyncResponseSlot((Method) executable);
            return asyncResponseSlot >= 0
                    ? AsyncMethodRunnerImpl.create(asyncResponseSlot)
                    : INSTANCE;
        }
        throw new IllegalArgumentException("Executable " + executable.getName() + " is not a constructor or method");
    }

    @Override
    public <T> Object run(
            InvocationContext context,
            Iterable<T> workItems,
            PreInvocationHandler<T> preInvocationHandler) throws Exception {
        workItems.forEach(workItem -> preInvocationHandler.accept(context, workItem));
        return context.proceed();
    }

    @Override
    public <T> Object run(
            InvocationContext context,
            Iterable<T> workItems,
            PreInvocationHandler<T> preInvocationHandler,
            PostCompletionHandler<T> postCompletionHandler) throws Exception {
        workItems.forEach(workItem -> preInvocationHandler.accept(context, workItem));

        Object result = null;
        Exception exceptionFromContextProceed = null;
        Exception escapingException;

        try {
            result = context.proceed();
        } catch (Exception e) {
            exceptionFromContextProceed = e;
        } finally {
            escapingException = processPostInvocationHandlers(context,
                    exceptionFromContextProceed,
                    workItems,
                    postCompletionHandler,
                    RuntimeException::new);
        }
        if (escapingException != null) {
            LOGGER.log(Level.WARNING, ERROR_DURING_INTERCEPTION, escapingException);
            throw escapingException;
        }
        return result;
    }

    /**
     * An {@code InterceptionRunner} which supports JAX-RS asynchronous methods.
     */
    private static class AsyncMethodRunnerImpl extends InterceptionRunnerImpl {
        private final int asyncResponseSlot;

        static InterceptionRunner create(int asyncResponseSlot) {
            return new AsyncMethodRunnerImpl(asyncResponseSlot);
        }

        private AsyncMethodRunnerImpl(int asyncResponseSlot) {
            this.asyncResponseSlot = asyncResponseSlot;
        }

        @Override
        public <T> Object run(
                InvocationContext context,
                Iterable<T> workItems,
                PreInvocationHandler<T> preInvocationHandler,
                PostCompletionHandler<T> postCompletionHandler) throws Exception {

            // Check the post-completion handler now because we don't want an NPE thrown from some other call stack when we try to
            // use it in the completion callback. Any other null argument would trigger an NPE from the current call stack.
            Objects.requireNonNull(postCompletionHandler, "postCompletionHandler");

            workItems.forEach(workItem -> preInvocationHandler.accept(context, workItem));

            Object[] params = context.getParameters();
            AsyncResponse asyncResponse = AsyncResponse.class.cast(context.getParameters()[asyncResponseSlot]);
            ThrowableCapturingAsyncResponse throwableCapturingAsyncResponse = new ThrowableCapturingAsyncResponse(asyncResponse);
            params[asyncResponseSlot] = throwableCapturingAsyncResponse;
            context.setParameters(params);

            throwableCapturingAsyncResponse.register(
                    FinishCallback.create(context, throwableCapturingAsyncResponse, postCompletionHandler, workItems));
            return context.proceed();
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", AsyncMethodRunnerImpl.class.getSimpleName() + "[", "]")
                    .add("asyncResponseSlot=" + asyncResponseSlot)
                    .toString();
        }
    }

    /**
     * A delegating implementation of {@code AsyncResponse} which wraps an {@code AsyncResponse}, capturing any exception the
     * intercepted method passes to {@code AsyncResponse.resume}.
     */
    private static class ThrowableCapturingAsyncResponse implements AsyncResponse {

        private final AsyncResponse delegate;
        private Throwable throwable = null;

        private ThrowableCapturingAsyncResponse(AsyncResponse delegate) {
            this.delegate = delegate;
        }

        Throwable throwable() {
            return throwable;
        }

        @Override
        public boolean resume(Object response) {
            return delegate.resume(response);
        }

        @Override
        public boolean resume(Throwable response) {
            throwable = response;
            return delegate.resume(response);
        }

        @Override
        public boolean cancel() {
            return delegate.cancel();
        }

        @Override
        public boolean cancel(int retryAfter) {
            return delegate.cancel(retryAfter);
        }

        @Override
        public boolean cancel(Date retryAfter) {
            return delegate.cancel(retryAfter);
        }

        @Override
        public boolean isSuspended() {
            return delegate.isSuspended();
        }

        @Override
        public boolean isCancelled() {
            return delegate.isCancelled();
        }

        @Override
        public boolean isDone() {
            return delegate.isDone();
        }

        @Override
        public boolean setTimeout(long time, TimeUnit unit) {
            return delegate.setTimeout(time, unit);
        }

        @Override
        public void setTimeoutHandler(TimeoutHandler handler) {
            delegate.setTimeoutHandler(handler);
        }

        @Override
        public Collection<Class<?>> register(Class<?> callback) {
            return delegate.register(callback);
        }

        @Override
        public Map<Class<?>, Collection<Class<?>>> register(Class<?> callback, Class<?>... callbacks) {
            return delegate.register(callback, callbacks);
        }

        @Override
        public Collection<Class<?>> register(Object callback) {
            return delegate.register(callback);
        }

        @Override
        public Map<Class<?>, Collection<Class<?>>> register(Object callback, Object... callbacks) {
            return delegate.register(callback, callbacks);
        }
    }

    private static class FinishCallback<T> implements CompletionCallback {

        private static final Logger LOGGER = Logger.getLogger(FinishCallback.class.getName());

        private final InvocationContext context;
        private final ThrowableCapturingAsyncResponse throwableCapturingAsyncResponse;
        private final PostCompletionHandler<T> postCompletionHandler;
        private final Iterable<T> workItems;

        static <T> FinishCallback<T> create(InvocationContext context,
                ThrowableCapturingAsyncResponse throwableCapturingAsyncResponse,
                PostCompletionHandler<T> postCompletionHandler,
                Iterable<T> workItems) {
            return new FinishCallback<>(context, throwableCapturingAsyncResponse, postCompletionHandler, workItems);
        }
        private FinishCallback(InvocationContext context,
                ThrowableCapturingAsyncResponse throwableCapturingAsyncResponse,
                PostCompletionHandler<T> postCompletionHandler,
                Iterable<T> workItems) {
            this.context = context;
            this.throwableCapturingAsyncResponse = throwableCapturingAsyncResponse;
            this.postCompletionHandler = postCompletionHandler;
            this.workItems = workItems;
        }

        @Override
        public void onComplete(Throwable throwable) {
            if (throwable != null) {
                LOGGER.log(Level.FINE, "Unmapped throwable detected by interceptor async callback", throwable);
            } else if (throwableCapturingAsyncResponse.throwable() != null) {
                throwable = throwableCapturingAsyncResponse.throwable();
                LOGGER.log(Level.FINE, "Mapped throwable detected by interceptor async callback", throwable);
            }
            Throwable reportingThrowable = processPostInvocationHandlers(context,
                    throwable,
                    workItems,
                    postCompletionHandler,
                    RuntimeException::new);
            if (reportingThrowable != null) {
                LOGGER.log(Level.WARNING, POST_INVOCATION_HANDLER_FAILURE, reportingThrowable);
            }
        }
    }

    // Invokes the post-completion handler for each work item. Adds any exceptions from running the handler
    // as suppressed exceptions on either (1) the exception/throwable from context.proceed, or (2) a new umbrella exception.
    //
    // Because synchronous runners deal with exceptions and async with throwables, we have to parameterize this
    // and provide a factory for creating a new umbrella exception if we need one.
    private static <T, X extends Throwable> X processPostInvocationHandlers(InvocationContext context,
            X fromContextProceed,
            Iterable<T> workItems,
            PostCompletionHandler<T> postCompletionHandler,
            Function<String, ? extends X> factory) {
        X escaping = fromContextProceed;
        for (T workItem : workItems) {
            try {
                // Pass the original problem, if any, to the handler.
                postCompletionHandler.accept(context, fromContextProceed, workItem);
            } catch (Exception handlerException) {
                if (escaping == null) {
                    escaping = factory.apply("Exception(s) invoking post-completion handler(s)");
                }
                escaping.addSuppressed(handlerException);
            }
        }
        return escaping;
    }

    private static int asyncResponseSlot(Method interceptedMethod) {
        int result = 0;

        for (Parameter p : interceptedMethod.getParameters()) {
            if (AsyncResponse.class.isAssignableFrom(p.getType()) && p.getAnnotation(Suspended.class) != null) {
                return result;
            }
            result++;
        }
        return -1;
    }
}
