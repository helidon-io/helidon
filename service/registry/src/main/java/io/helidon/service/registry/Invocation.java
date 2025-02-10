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

package io.helidon.service.registry;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Handles the invocation of {@link Interception.Interceptor} methods.
 * Note that upon a successful call to the
 * {@link Interception.Interceptor.Chain#proceed(Object[])}
 * or to the ultimate target, the invocation will be prevented from being executed again.
 *
 * @param <V> the invocation type
 * @see io.helidon.service.registry.InterceptionContext
 */
class Invocation<V> implements Interception.Interceptor.Chain<V> {
    private final InterceptionContext ctx;
    private final List<Supplier<Interception.Interceptor>> interceptors;
    private final Set<Class<? extends Throwable>> checkedExceptions;
    private int interceptorPos;
    private InterceptionInvoker<V> call;

    private Invocation(InterceptionContext ctx,
                       List<Supplier<Interception.Interceptor>> interceptors,
                       InterceptionInvoker<V> call,
                       Set<Class<? extends Throwable>> checkedExceptions) {
        this.ctx = ctx;
        this.call = call;
        this.interceptors = List.copyOf(interceptors);
        this.checkedExceptions = checkedExceptions;
    }

    /**
     * Creates an instance of {@link Invocation} and invokes it in this context.
     *
     * @param ctx               the invocation context
     * @param call              the call to the base service provider's method
     * @param args              the call arguments
     * @param checkedExceptions expected exception types
     * @param <V>               the type returned from the method element
     * @return the invocation instance
     * @throws io.helidon.service.registry.InterceptionException if there are errors during invocation chain processing
     * @throws Exception                                         any checked exception declared by the method itself
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static <V> V invokeInterception(InterceptionContext ctx,
                                    List<? extends Supplier<Interception.Interceptor>> interceptors,
                                    InterceptionInvoker<V> call,
                                    Object[] args,
                                    Set<Class<? extends Throwable>> checkedExceptions) throws Exception {
        Objects.requireNonNull(ctx);
        Objects.requireNonNull(call);
        Objects.requireNonNull(args);
        Objects.requireNonNull(checkedExceptions);

        if (interceptors.isEmpty()) {
            try {
                return call.invoke(args);
            } catch (Throwable t) {
                if (shouldThrow(checkedExceptions, t.getClass())) {
                    throw t;
                }
                throw new InterceptionException("Error in interceptor chain processing", t, true);
            }
        } else {
            return (V) new Invocation(ctx, interceptors, call, checkedExceptions).proceed(args);
        }
    }

    @Override
    public String toString() {
        return String.valueOf(ctx.elementInfo());
    }

    @Override
    public V proceed(Object... args) throws Exception {
        if (this.call == null) {
            throw new InterceptionException("Duplicate invocation, or unknown call type: " + this, true);
        }

        if (interceptorPos < interceptors.size()) {
            Supplier<Interception.Interceptor> interceptorProvider = interceptors.get(interceptorPos);
            Interception.Interceptor interceptor = interceptorProvider.get();
            interceptorPos++;
            try {
                return interceptor.proceed(ctx, this, args);
            } catch (RuntimeException e) {
                interceptorPos--;
                throw e;
            } catch (Throwable t) {
                interceptorPos--;

                if (shouldThrow(checkedExceptions, t.getClass())) {
                    throw t;
                }

                String message = "Error in interceptor chain processing";
                boolean called = call == null;

                throw new InterceptionException(message, t, called);
            }
        }

        InterceptionInvoker<V> call = this.call;
        this.call = null;

        try {
            return call.invoke(args);
        } catch (InterceptionException e) {
            if (e.targetWasCalled()) {
                // allow the call to happen again
                this.call = call;
            }
            throw e;
        } catch (RuntimeException e) {
            this.call = call;
            throw e;
        } catch (Throwable t) {
            // allow the call to happen again
            this.call = call;
            if (shouldThrow(checkedExceptions, t.getClass())) {
                // do not wrap, declared checked exception
                throw t;
            }
            // wrap, unexpected exception/throwable
            throw new InterceptionException("Error in interceptor chain processing", t, true);
        }
    }

    private static boolean shouldThrow(Set<Class<? extends Throwable>> checked, Class<? extends Throwable> t) {
        if (checked.contains(t)) {
            return true;
        }
        for (Class<? extends Throwable> aClass : checked) {
            if (aClass.isAssignableFrom(t)) {
                return true;
            }
        }
        return false;
    }
}
