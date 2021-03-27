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

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;

import javax.interceptor.InvocationContext;

/**
 * Common behavior for many interceptors, invoking a {@linkplain #preInvoke(InvocationContext, Object)} method before
 * running the intercepted constructor or method.
 * <p>
 *     See also the {@link WithPostComplete} interface.
 * </p>
 * <p>
 *     Concrete classes typically implement the required {@linkplain #aroundConstruct(InvocationContext)} method with the
 *     {@code @AroundConstruct} annotation and it delegates to {@linkplain #aroundConstructBase(InvocationContext)}. Similarly,
 *     the required {@linkplain #aroundInvoke(InvocationContext)} method has the {@code @AroundInvoke} annotation and delegates to
 *     {@linkplain #aroundInvokeBase(InvocationContext)}.
 * </p>
 *
 * @param <W> type of the work item processed by the interceptor
 */
public interface HelidonInterceptor<W> {

    /**
     * Invokes the implementation's {@code preInvoke} logic for a constructor, once for each work item associated with this
     * constructor and the annotation type this interceptor handles.
     *
     * @param context {@code InvocationContext}
     * @return any value returned by the intercepted {@code Executable}
     * @throws Exception when the intercepted code throws an exception
     */
    default Object aroundConstructBase(InvocationContext context) throws Exception {
        InterceptionTargetInfo<W> interceptionTargetInfo = interceptionTargetInfo(context.getConstructor());
        return interceptionTargetInfo.runner().run(
                context,
                interceptionTargetInfo.workItems(annotationType()),
                this::preInvoke);
    }

    /**
     * Invoked during the intercepted constructor invocation.
     * <p>
     *     Typically, annotate the implementation with {@code @AroundConstruct} and simply delegate to {@linkplain
     *     #aroundConstructBase}.
     * </p>
     * @param context the invocation context for the intercepted call
     * @return the value returned from the intercepted constructor
     * @throws Exception if the invoked constructor throws an exception
     */
    Object aroundConstruct(InvocationContext context) throws Exception;

    /**
     * Invoked during the intercepted method invocation.
     * <p>
     *     Typically, annotated the implementation with {@code @AroundInvoke} and simply delegate to {@code aroundInvokeBase}.
     * </p>
     * @param context the invocation context for the intercepted call
     * @return the value returned from the intercepted method
     * @throws Exception if the invoked method throws an exception
     */
    Object aroundInvoke(InvocationContext context) throws Exception;

    /**
     * Invokes the implementation's {@code preInvoke} logic for a method, once for each work item associated with this
     * constructor and the annotation type this interceptor handles.
     *
     * @param context {@code InvocationContext}
     * @return any value returned by the intercepted {@code Executable}
     * @throws Exception when the intercepted code throws an exception
     */
    default Object aroundInvokeBase(InvocationContext context) throws Exception {
        InterceptionTargetInfo<W> interceptionTargetInfo = interceptionTargetInfo(context.getMethod());
        return interceptionTargetInfo.runner().run(
                context,
                interceptionTargetInfo.workItems(annotationType()),
                this::preInvoke);
    }

    /**
     * Returns the type of the annotation this interceptor handles.
     *
     * @return type of the annotation
     */
    Class<? extends Annotation> annotationType();

    /**
     * Returns the correct {@link InterceptionTargetInfo} for the given {@code Executable}.
     *
     * @param executable the constructor or method for which the {@code InterceptInfo} is needed
     * @return the appropriate {@code InterceptInfo}
     */
    InterceptionTargetInfo<W> interceptionTargetInfo(Executable executable);

    /**
     * Performs whatever pre-invocation work is needed for the given context, applied to the specified work item.
     *
     * @param context {@code InvocationContext} for the execution being intercepted
     * @param workItem the work item on which to operate
     */
    void preInvoke(InvocationContext context, W workItem);

    /**
     * Common behavior among interceptors with both pre- and post-invoke behavior.
     *
     * @param <W> type of the work item processed during interception
     */
    interface WithPostComplete<W> extends HelidonInterceptor<W> {

        /**
         * Invokes the implementation's {@code preInvoke} and {@code postInvoke} logic for a constructor, once for each work item
         * associated with this constructor and the annotation type this interceptor handles.
         *
         * @param context {@code InvocationContext}
         * @return any value returned by the intercepted {@code Executable}
         * @throws Exception when the intercepted code throws an exception
         */
        @Override
        default Object aroundConstructBase(InvocationContext context) throws Exception {
            InterceptionTargetInfo<W> interceptionTargetInfo = interceptionTargetInfo(context.getConstructor());
            return interceptionTargetInfo.runner().run(
                    context,
                    interceptionTargetInfo.workItems(annotationType()),
                    this::preInvoke,
                    this::postComplete);
        }

        /**
         * Invokes the implementation's {@code preInvoke} and {@code postInvoke} logic for a constructor, once for each work item
         * associated with this constructor and the annotation type this interceptor handles.
         *
         * @param context {@code InvocationContext}
         * @return any value returned by the intercepted {@code Executable}
         * @throws Exception when the intercepted code throws an exception
         */
        @Override
        default Object aroundInvokeBase(InvocationContext context) throws Exception {
            InterceptionTargetInfo<W> interceptionTargetInfo = interceptionTargetInfo(context.getMethod());
            return interceptionTargetInfo.runner().run(
                    context,
                    interceptionTargetInfo.workItems(annotationType()),
                    this::preInvoke,
                    this::postComplete);
        }

        /**
         * Performs whatever post-completion work is needed for the given context, applied to the specified work item.
         *
         * @param context {@code InvocationContext} for the execution being intercepted
         * @param throwable throwable from the intercepted method; null if the method returned normally
         * @param workItem the work item on which to operate
         */
        void postComplete(InvocationContext context, Throwable throwable, W workItem);
    }
}
