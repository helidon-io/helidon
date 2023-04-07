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

package io.helidon.pico.runtime;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import io.helidon.common.types.AnnotationAndValue;
import io.helidon.common.types.TypeName;
import io.helidon.common.types.TypedElementName;
import io.helidon.pico.api.DefaultInvocationContext;
import io.helidon.pico.api.Interceptor;
import io.helidon.pico.api.InvocationContext;
import io.helidon.pico.api.InvocationException;
import io.helidon.pico.api.ServiceProvider;

import jakarta.inject.Provider;

/**
 * Base class, used in {@link io.helidon.pico.api.Interceptor} generated code. One of these instances will be created for each
 * intercepted method.
 *
 * @param <I> the intercepted type
 * @param <V> the intercepted method return type
 */
public abstract class InterceptedMethod<I, V> implements Function<Object[], V> {
    private final I impl;
    private final InvocationContext ctx;

    /**
     * The constructor.
     *
     * @param interceptedImpl         the intercepted instance
     * @param serviceProvider         the service provider for the intercepted type
     * @param serviceTypeName         the service type name
     * @param serviceLevelAnnotations the service level annotations
     * @param interceptors            the interceptors for the method
     * @param methodInfo              the method element info
     * @param methodArgInfo           the method args element info
     */
    protected InterceptedMethod(I interceptedImpl,
                                ServiceProvider<?> serviceProvider,
                                TypeName serviceTypeName,
                                Collection<AnnotationAndValue> serviceLevelAnnotations,
                                Collection<Provider<Interceptor>> interceptors,
                                TypedElementName methodInfo,
                                TypedElementName[] methodArgInfo) {
        this.impl = Objects.requireNonNull(interceptedImpl);
        this.ctx =  DefaultInvocationContext.builder()
                .serviceProvider(serviceProvider)
                .serviceTypeName(serviceTypeName)
                .classAnnotations(serviceLevelAnnotations)
                .interceptors(interceptors)
                .elementInfo(methodInfo)
                .elementArgInfo(methodArgInfo)
                .build();
    }

    /**
     * The constructor.
     *
     * @param interceptedImpl         the intercepted instance
     * @param serviceProvider         the service provider for the intercepted type
     * @param serviceTypeName         the service type name
     * @param serviceLevelAnnotations the service level annotations
     * @param interceptors            the interceptors for the method
     * @param methodInfo              the method element info
     */
    protected InterceptedMethod(I interceptedImpl,
                                ServiceProvider<?> serviceProvider,
                                TypeName serviceTypeName,
                                Collection<AnnotationAndValue> serviceLevelAnnotations,
                                Collection<Provider<Interceptor>> interceptors,
                                TypedElementName methodInfo) {
        this.impl = Objects.requireNonNull(interceptedImpl);
        this.ctx =  DefaultInvocationContext.builder()
                .serviceProvider(serviceProvider)
                .serviceTypeName(serviceTypeName)
                .classAnnotations(serviceLevelAnnotations)
                .interceptors(interceptors)
                .elementInfo(methodInfo)
                .build();
    }

    /**
     * The intercepted instance.
     *
     * @return the intercepted instance
     */
    public I impl() {
        return impl;
    }

    /**
     * The intercepted invocation context.
     *
     * @return the intercepted invocation context
     */
    public InvocationContext ctx() {
        return ctx;
    }

    /**
     * Make the invocation to an interceptor method.
     *
     * @param args arguments
     * @return the result of the call to the intercepted method
     * @throws java.lang.Throwable if there is any throwables encountered as part of the invocation
     */
    public abstract V invoke(Object... args) throws Throwable;

    @Override
    public V apply(Object... args) {
        try {
            return invoke(args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new InvocationException(t.getMessage(), t, ctx.serviceProvider());
        }
    }

}
