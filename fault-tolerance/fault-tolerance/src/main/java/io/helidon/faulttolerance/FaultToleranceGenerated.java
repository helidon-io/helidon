/*
 * Copyright (c) 2024, 2025 Oracle and/or its affiliates.
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

package io.helidon.faulttolerance;

import io.helidon.common.Functions;
import io.helidon.common.UncheckedException;
import io.helidon.service.registry.Interception;
import io.helidon.service.registry.InterceptionContext;

/**
 * Types used only in generated code (and of course when reading the generated code).
 */
public final class FaultToleranceGenerated {
    private FaultToleranceGenerated() {
    }

    private static Exception findCause(UncheckedException e) {
        Throwable cause = e.getCause();

        if (cause instanceof Exception ex) {
            return ex;
        }

        return e;
    }

    /**
     * A generated service common interface.
     */
    public interface FtMethod {
    }

    /**
     * A generated service to create or get retry.
     */
    public abstract static class RetryMethod implements Interception.ElementInterceptor {
        /**
         * Constructor with no side effects.
         */
        protected RetryMethod() {
        }

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            try {
                return retry().invoke(Functions.unchecked(() -> chain.proceed(args)));
            } catch (UncheckedException e) {
                throw findCause(e);
            }
        }

        /**
         * Provide a retry instance that should be used with this method.
         * If the retry annotation contains a name, we will attempt to obtain the named instance from
         * registry. If such a named instance does not exist a new retry will be created from the annotation.
         *
         * @return retry instance
         */
        protected abstract Retry retry();
    }

    /**
     * A generated service to create or get timeout.
     */
    public abstract static class TimeoutMethod implements Interception.ElementInterceptor {
        /**
         * Constructor with no side effects.
         */
        protected TimeoutMethod() {
        }

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            try {
                return timeout().invoke(Functions.unchecked(() -> chain.proceed(args)));
            } catch (UncheckedException e) {
                throw findCause(e);
            }
        }

        /**
         * Provide a timeout instance that should be used with this method.
         * If the timeout annotation contains a name, we will attempt to obtain the named instance from
         * registry. If such a named instance does not exist a new instance would be created from the annotation.
         *
         * @return timeout instance
         */
        protected abstract Timeout timeout();
    }

    /**
     * A generated service to support circuit breaker without resorting to Class.forName() for exception types.
     */
    public abstract static class CircuitBreakerMethod implements Interception.ElementInterceptor {
        /**
         * Constructor with no side effects.
         */
        protected CircuitBreakerMethod() {
        }

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            try {
                return circuitBreaker().invoke(Functions.unchecked(() -> chain.proceed(args)));
            } catch (UncheckedException e) {
                throw findCause(e);
            }
        }

        /**
         * Provide a circuit breaker instance that should be used with this method.
         * If the {@link io.helidon.faulttolerance.Ft.CircuitBreaker} annotation contains a name, we will attempt to obtain
         * the named instance from registry. If such a named instance does not exist a new instance would be created
         * from the annotation.
         *
         * @return circuit breaker instance
         */
        protected abstract CircuitBreaker circuitBreaker();
    }

    /**
     * A generated service to support bulkhead.
     */
    public abstract static class BulkheadMethod implements Interception.ElementInterceptor {
        /**
         * Constructor with no side effects.
         */
        protected BulkheadMethod() {
        }

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            try {
                return bulkhead().invoke(Functions.unchecked(() -> chain.proceed(args)));
            } catch (UncheckedException e) {
                throw findCause(e);
            }
        }

        /**
         * Provide a bulkhead instance that should be used with this method.
         * If the {@link io.helidon.faulttolerance.Ft.Bulkhead} annotation contains a name, we will attempt to obtain
         * the named instance from registry. If such a named instance does not exist a new instance would be created
         * from the annotation.
         *
         * @return bulkhead instance
         */
        protected abstract Bulkhead bulkhead();
    }

    /**
     * A generated service to support async.
     */
    public abstract static class AsyncMethod implements Interception.ElementInterceptor {
        /**
         * Constructor with no side effects.
         */
        protected AsyncMethod() {
        }

        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception {
            try {
                return async().invoke(Functions.unchecked(() -> chain.proceed(args))).get();
            } catch (UncheckedException e) {
                throw findCause(e);
            }
        }

        /**
         * Provide an async instance that should be used with this method.
         * If the {@link io.helidon.faulttolerance.Ft.Async} annotation contains a name, we will attempt to obtain
         * the named instance from registry. If such a named instance does not exist a new instance would be created
         * from the annotation.
         *
         * @return async instance
         */
        protected abstract Async async();
    }

    /**
     * A generated service to support {@link io.helidon.faulttolerance.Fallback}.
     *
     * @param <T> service type
     */
    public abstract static class FallbackMethod<T> implements Interception.ElementInterceptor {
        /**
         * Constructor with no side effects.
         */
        protected FallbackMethod() {
        }

        @SuppressWarnings("unchecked")
        @Override
        public <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) {
            try {
                return chain.proceed(args);
            } catch (Throwable t) {
                try {
                    return (V) fallback((T) ctx.serviceInstance().orElse(null), t, args);
                } catch (RuntimeException e) {
                    e.addSuppressed(t);
                    throw e;
                } catch (Throwable x) {
                    x.addSuppressed(t);
                    throw new SupplierException("Failed to invoke fallback method for: " + ctx.elementInfo(),
                                                x);
                }
            }
        }

        /**
         * Generated implementation of this method will invoke the fallback for exceptions that are valid.
         *
         * @param service service instance
         * @param t       throwable that was thrown
         * @param params  parameters of the method
         * @return the expected result
         * @throws Throwable throwable in case the provided {@code t} should be skipped
         */
        protected abstract Object fallback(T service, Throwable t, Object... params) throws Throwable;
    }
}
