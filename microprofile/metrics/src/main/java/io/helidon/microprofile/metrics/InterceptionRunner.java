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
package io.helidon.microprofile.metrics;

import java.util.function.BiConsumer;

import javax.interceptor.InvocationContext;

/**
 * Abstraction of processing around an interception point, independent from the details of any
 * particular interceptor or the specific type of work done (e.g., updating metrics) before the intercepted invocation runs and
 * after it completes.
 * <p>
 *     To use {@code InterceptionRunner}, clients:
 *     <ul>
 *         <li>Create an instance of a class which implements {@code InterceptionRunner}.</li>
 *         <li>From the interceptor's {@code @AroundConstruct} and {@code @AroundInvoke} methods, invoke one of the variants of
 *         the runner's {@link #run(InvocationContext, Iterable, BiConsumer) run} method. Which variant depends on whether the
 *         specific interceptor needs to operate on the work items
 *         <ul>
 *             <li>only before (e.g., to increment a counter metric), or</li>
 *             <li>both
 *         before and after (e.g., to update a metric that measures time spent in the intercepted method)</li>
 *         </ul>
 *         the intercepted
 *         method
 *         runs.
 *         <p>
 *             The interceptor passes the {@code run} method:
 *             <ul>
 *                <li>a {@code Supplier<Iterable<>>} of the work items,</li>
 *                <li>a pre-invocation {@code Consumer} of work item which performs an action on each work item before the
 *                intercepted invocation runs, and</li>
 *                <li>an post-completion {@code Consumer} of work item which performs an action on each work item after the
 *                intercepted invocation has finished, only for the "before-and-after"
 *                {@link #run(InvocationContext, Iterable, BiConsumer, BiConsumer) run} variant.</li>
 *             </ul>
 *         </p>
 *         </li>
 *     </ul>
 * </p>
 * <p>
 *      The runner
 *      <ol>
 *          <li>invokes the pre-invocation consumer for all work items,</li>
 *          <li>invokes the intercepted executable, then</li>
 *          <li>(if provided) invokes the post-completion consumer for all work
 *          items.</li>
 *      </ol>
 * </p>
 * <p>
 *     The interface requires a {@code Iterable<>} for work items because, in the before-and-after case, the runner
 *     might need to process the work items twice. In those cases, the {@code Iterable} can furnish two {@code Iterators}.
 * </p>
 */
interface InterceptionRunner {

    /**
     * Invokes the intercepted executable represented by the {@code InvocationContext}, performing the pre-invocation
     * operation on each work item.
     *
     * @param context {@code InvocationContext} for the intercepted invocation
     * @param workItems the work items the interceptor will operate on
     * @param preInvocationHandler the pre-invoke operation to perform on each work item
     * @param <T> type of the work items
     * @return the return value from the invoked executable
     * @throws Exception for any error thrown by the {@code Iterable} of work items or the invoked executable itself
     */
     <T> Object run(
            InvocationContext context,
            Iterable<T> workItems,
            BiConsumer<InvocationContext, T> preInvocationHandler) throws Exception;

    /**
     * Invokes the intercepted executable represented by the {@code InvocationContext}, performing the pre-invocation
     * and post-completion operation on each work item.
     *
     * @param context {@code InvocationContext} for the intercepted invocation
     * @param workItems the work items the interceptor will operate on
     * @param preInvocationHandler the pre-invoke operation to perform on each work item
     * @param postCompletionHandler the post-completion operation to perform on each work item
     * @param <T> type of the work items
     * @return the return value from the invoked executable
     * @throws Exception for any error thrown by the {@code Iterable} of work items or the invoked executable itself
     */
    <T> Object run(
            InvocationContext context,
            Iterable<T> workItems,
            BiConsumer<InvocationContext, T> preInvocationHandler,
            BiConsumer<InvocationContext, T> postCompletionHandler) throws Exception;
}
