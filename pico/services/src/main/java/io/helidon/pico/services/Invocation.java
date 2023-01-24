/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.pico.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Supplier;

import io.helidon.pico.Interceptor;
import io.helidon.pico.InvocationContext;
import io.helidon.pico.ServiceProvider;

import jakarta.inject.Provider;

/**
 * Handles the code generation in support of {@link Interceptor}.
 *
 * @see io.helidon.pico.InvocationContext
 * @param <V> the invocation type
 */
public class Invocation<V> implements Interceptor.Chain<V> {

    private final InvocationContext ctx;
    private final ListIterator<Provider<Interceptor>> interceptorIterator;
    private Supplier<V> call;
    private Runnable runnableCall;

    private Invocation(
            InvocationContext ctx,
            Supplier<V> call) {
        this.ctx = ctx;
        this.call = Objects.requireNonNull(call);
        this.runnableCall = null;
        this.interceptorIterator = ctx.interceptors().listIterator();
    }

    private Invocation(
            InvocationContext ctx,
            Runnable call) {
        this.ctx = ctx;
        this.call = null;
        this.runnableCall = Objects.requireNonNull(call);
        this.interceptorIterator = ctx.interceptors().listIterator();
    }

    /**
     * Creates an instance of {@link Invocation} and invokes it in this context.
     *
     * @param ctx   the invocation context
     * @param call  the call to the base service provider's method
     * @param <V>   the type returned from the method element
     * @return the invocation instance
     */
    @SuppressWarnings("unchecked")
    public static <V> V createInvokeAndSupply(
            InvocationContext ctx,
            Supplier<V> call) {
        if (ctx.interceptors().isEmpty()) {
            return call.get();
        }

        return (V) new Invocation(ctx, call).proceed();
    }

    /**
     * Creates an instance of {@link Invocation} and invokes it in this context.
     *
     * @param ctx   the invocation context
     * @param call  the call to the base service provider's method
     */
    @SuppressWarnings("rawtypes")
    public static void createAndInvoke(
            InvocationContext ctx,
            Runnable call) {
        if (ctx.interceptors().isEmpty()) {
            call.run();
        } else {
            new Invocation(ctx, call).proceed();
        }
    }

    /**
     * Merges a variable number of lists together, where the net result is the merged set of non-null providers
     * ranked in proper weight order, or null if the list would have otherwise been empty.
     *
     * @param lists the lists to merge
     * @param <T>   the type of the provider
     * @return the merged result, or null instead of empty lists
     */
    @SuppressWarnings("unchecked")
    public static <T> List<Provider<T>> mergeAndCollapse(
            List<Provider<T>>... lists) {
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
    public V proceed() {
        if (!interceptorIterator.hasNext()) {
            if (this.call != null) {
                Supplier<V> call = this.call;
                this.call = null;
                return call.get();
            } else if (this.runnableCall != null) {
                Runnable call = this.runnableCall;
                this.runnableCall = null;
                call.run();
                return null;
            } else {
                throw new IllegalStateException("unknown call type: " + this);
            }
        } else {
            return interceptorIterator.next()
                    .get()
                    .proceed(ctx, this);
        }
    }

}
