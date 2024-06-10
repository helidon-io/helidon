/*
 * Copyright (c) 2023, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import io.helidon.inject.api.Interceptor;
import io.helidon.inject.api.InvocationContext;
import io.helidon.inject.api.InvocationException;
import io.helidon.inject.api.ServiceProvider;

import jakarta.inject.Provider;

/**
 * Handles the invocation of {@link Interceptor} methods.
 * Note that upon a successful call to the {@link Interceptor.Chain#proceed(Object[])} or to the ultimate
 * target, the invocation will be prevented from being executed again.
 *
 * @see InvocationContext
 * @param <V> the invocation type
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class Invocation<V> implements Interceptor.Chain<V> {
    private final InvocationContext ctx;
    private final List<Provider<Interceptor>> interceptors;
    private int interceptorPos;
    private Function<Object[], V> call;

    private Invocation(InvocationContext ctx,
                       Function<Object[], V> call) {
        this.ctx = ctx;
        this.call = Objects.requireNonNull(call);
        this.interceptors = List.copyOf(ctx.interceptors());
    }

    @Override
    public String toString() {
        return String.valueOf(ctx.elementInfo());
    }

    /**
     * Creates an instance of {@link Invocation} and invokes it in this context.
     *
     * @param ctx   the invocation context
     * @param call  the call to the base service provider's method
     * @param args  the call arguments
     * @param <V>   the type returned from the method element
     * @return the invocation instance
     * @throws InvocationException if there are errors during invocation chain processing
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <V> V createInvokeAndSupply(InvocationContext ctx,
                                              Function<Object[], V> call,
                                              Object[] args) {
        if (ctx.interceptors().isEmpty()) {
            try {
                return call.apply(args);
            } catch (Throwable t) {
                throw new InvocationException("Error in interceptor chain processing", t, true);
            }
        } else {
            return (V) new Invocation(ctx, call).proceed(args);
        }
    }

    /**
     * The degenerate case for {@link #mergeAndCollapse(List[])}. This is here only to eliminate the unchecked varargs compiler
     * warnings that would otherwise be issued in code that does not have any interceptors on a method.
     *
     * @param <T>   the type of the provider
     * @return an empty list
     * @deprecated this method should only be called by generated code
     */
    @Deprecated
    public static <T> List<Provider<T>> mergeAndCollapse() {
        return List.of();
    }

    /**
     * Merges a variable number of lists together, where the net result is the merged set of non-null providers
     * ranked in proper weight order, or else empty list.
     *
     * @param lists the lists to merge
     * @param <T>   the type of the provider
     * @return the merged result or empty list if there is o interceptor providers
     */
    @SuppressWarnings("unchecked")
    public static <T> List<Provider<T>> mergeAndCollapse(List<Provider<T>>... lists) {
        List<Provider<T>> result = null;

        for (List<Provider<T>> list : lists) {
            if (list == null) {
                continue;
            }

            for (Provider<T> p : list) {
                if (p == null) {
                    continue;
                }

                if (p instanceof ServiceProvider
                        && VoidServiceProvider.serviceTypeName().equals(
                                ((ServiceProvider<?>) p).serviceInfo().serviceTypeName())) {
                    continue;
                }

                if (result == null) {
                    result = new ArrayList<>();
                }
                if (!result.contains(p)) {
                    result.add(p);
                }
            }
        }

        if (result != null && result.size() > 1) {
            result.sort(DefaultServices.serviceProviderComparator());
        }

        return (result != null) ? Collections.unmodifiableList(result) : List.of();
    }

    @Override
    public V proceed(Object... args) {
        if (this.call == null) {
            throw new InvocationException("Duplicate invocation, or unknown call type: " + this, true);
        }

        if (interceptorPos < interceptors.size()) {
            Provider<Interceptor> interceptorProvider =  interceptors.get(interceptorPos);
            Interceptor interceptor = interceptorProvider.get();
            interceptorPos++;
            try {
                return interceptor.proceed(ctx, this, args);
            } catch (Throwable t) {
                interceptorPos--;

                if (t instanceof InvocationException) {
                    throw t;
                }

                throw (interceptorProvider instanceof ServiceProvider)
                        ? new InvocationException("Error in interceptor chain processing",
                                                  t,
                                                  (ServiceProvider<?>) interceptorProvider,
                                                  call == null)
                        : new InvocationException("Error in interceptor chain processing",
                                                  t,
                                                  call == null);
            }
        }

        Function<Object[], V> call = this.call;
        this.call = null;

        try {
            return call.apply(args);
        } catch (Throwable t) {
            if (t instanceof InvocationException) {
                if (!((InvocationException) t).targetWasCalled()) {
                    // allow the call to happen again
                    this.call = call;
                }
                throw t;
            }

            // allow the call to happen again
            this.call = call;
            throw new InvocationException("Error in interceptor chain processing", t, true);
        }
    }

}
