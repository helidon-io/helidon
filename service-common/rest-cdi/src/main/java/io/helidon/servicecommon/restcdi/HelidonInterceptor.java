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

import java.lang.reflect.Executable;

import javax.enterprise.context.Dependent;
import javax.interceptor.AroundConstruct;
import javax.interceptor.AroundInvoke;
import javax.interceptor.InvocationContext;

/**
 * Common behavior for interceptors, invoking a {@linkplain #preInvocation(InvocationContext, Object) preInvocation} method
 * before running an intercepted {@code Executable}.
 * <p>
 *     Implementing classes typically extend {@link HelidonInterceptor.Base}.
 * </p>
 * <p>
 *     See also the {@link WithPostCompletion} interface.
 * </p>
 *
 * @param <W> type of the work item processed by the interceptor
 */
public interface HelidonInterceptor<W> {

    /**
     * Invokes the implementation's {@code preInvocation} logic for a constructor, once for each work item associated with the
     * constructor.
     *
     * @param context {@code InvocationContext}
     * @return any value returned by the intercepted {@code Executable}
     * @throws Exception when the intercepted code throws an exception
     */
    default Object aroundConstruction(InvocationContext context) throws Exception {
        return InterceptionRunnerImpl.create(context.getConstructor())
                .run(
                    context,
                    workItems(context.getConstructor()),
                    this::preInvocation);
    }

    /**
     * Invoked during the intercepted constructor invocation.
     * <p>
     *     Typically, concrete implementations should extend {@link Base} which implements this method with
     *     {@code @AroundConstruct}. Annotation processing for {@code @AroundConstruct} does not recognize the annotation on a
     *     {@code default} method implementation defined on the interface.
     * </p>
     * @param context the invocation context for the intercepted call
     * @return the value returned from the intercepted constructor
     * @throws Exception if the invoked constructor throws an exception
     */
    Object aroundConstruct(InvocationContext context) throws Exception;

    /**
     * Invokes the implementation's {@linkplain #preInvocation(InvocationContext, Object) preInvocation} logic for a method, once
     * for each work item associated with the method.
     *
     * @param context {@code InvocationContext}
     * @return any value returned by the intercepted {@code Executable}
     * @throws Exception when the intercepted code throws an exception
     */
    default Object aroundInvocation(InvocationContext context) throws Exception {
        return InterceptionRunnerImpl.create(context.getMethod()).run(
                context,
                workItems(context.getMethod()),
                this::preInvocation);
    }

    /**
     * Invoked during the intercepted method invocation.
     * <p>
     *     Typically, concrete implementations should extend {@link Base} which implements this method with
     *     {@code @AroundInvoke}. Annotation processing for {@code @AroundInvoke} does not recognize the annotation on a
     *     {@code default} method implementation defined on the interface.
     * </p>
     * @param context the invocation context for the intercepted call
     * @return the value returned from the intercepted method
     * @throws Exception if the invoked method throws an exception
     */
    Object aroundInvoke(InvocationContext context) throws Exception;

    /**
     * Returns the work items the specific interceptor instance should process.
     *
     * @param executable the specific {@code Executable} being intercepted
     * @return the work items pertinent to the specified {@code Executable}
     */
    Iterable<W> workItems(Executable executable);

    /**
     * Performs whatever pre-invocation work is needed for the given context, applied to the specified work item.
     * <p>
     *     If the pre-invocation handler throws an exception, any other pre-invocation
     * </p>
     *
     * @param context {@code InvocationContext} for the execution being intercepted
     * @param workItem the work item on which to operate
     */
    void preInvocation(InvocationContext context, W workItem);

    /**
     * {@code HelidonInterceptor} implementation providing as much logic as possible.
     * <p>
     *     The two methods implemented here cannot be {@code default} methods on the interface because annotation processing
     *     for {@code @AroundConstruct} and {@code @AroundInvoke} does not recognize their placement on {@code default} interface
     *     methods.
     * </p>
     * @param <W> type of work items processed by the interceptor implementation
     */
    @Dependent
    abstract class Base<W> implements HelidonInterceptor<W> {

        @AroundConstruct
        @Override
        public Object aroundConstruct(InvocationContext context) throws Exception {
            return aroundConstruction(context);
        }

        @AroundInvoke
        @Override
        public Object aroundInvoke(InvocationContext context) throws Exception {
            return aroundInvocation(context);
        }
    }

    /**
     * Common behavior among interceptors with both pre-invocation and post-completion behavior.
     * <p>
     *     Implementing classes should extend {@link HelidonInterceptor} to inherit the provided behavior.
     * </p>
     *
     * @param <W> type of the work item processed during interception
     */
    interface WithPostCompletion<W> extends HelidonInterceptor<W> {

        /**
         * Invokes the implementation's {@code preInvocation} and {@code postCompletion} logic for a constructor, once for each
         * work item associated with the constructor.
         *
         * @param context {@code InvocationContext}
         * @return any value returned by the intercepted {@code Executable}
         * @throws Exception when the intercepted code throws an exception
         */
        @Override
        default Object aroundConstruction(InvocationContext context) throws Exception {
            return InterceptionRunnerImpl.create(context.getConstructor())
                    .run(
                        context,
                        workItems(context.getConstructor()),
                        this::preInvocation,
                        this::postCompletion);
        }

        /**
         * Invokes the implementation's {@code preInvocation} and {@code postCompletion} logic for a constructor, once for each
         * work item associated with the method.
         *
         * @param context {@code InvocationContext}
         * @return any value returned by the intercepted {@code Executable}
         * @throws Exception when the intercepted code throws an exception
         */
        @Override
        default Object aroundInvocation(InvocationContext context) throws Exception {
            return InterceptionRunnerImpl.create(context.getMethod())
                    .run(
                        context,
                        workItems(context.getMethod()),
                        this::preInvocation,
                        this::postCompletion);
        }

        /**
         * Performs whatever post-completion work is needed for the given context, applied to the specified work item.
         *
         * @param context {@code InvocationContext} for the execution being intercepted
         * @param throwable throwable from the intercepted method; null if the method returned normally
         * @param workItem the work item on which to operate
         */
        void postCompletion(InvocationContext context, Throwable throwable, W workItem);
    }
}
