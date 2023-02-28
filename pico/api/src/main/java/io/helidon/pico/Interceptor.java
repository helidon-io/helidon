/*
 * Copyright (c) 2022 Oracle and/or its affiliates.
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

package io.helidon.pico;

/**
 * Implementors of this contract must be {@link jakarta.inject.Named} according to the {@link Intercepted}
 * annotation they support.
 */
@Contract
public interface Interceptor {

    /**
     * Called during interception of the target V. The implementation typically should finish with the call to
     * {@link Interceptor.Chain#proceed()}.
     *
     * @param ctx   the invocation context
     * @param chain the chain to call proceed on
     * @param <V>   the return value type (or {@link Void} for void method elements)
     * @return the return value to the caller
     */
    <V> V proceed(InvocationContext ctx, Chain<V> chain);


    /**
     * Represents the next in line for interception, terminating with a call to the wrapped service provider.
     *
     * @param <V> the return value
     */
    interface Chain<V> {
        /**
         * Call the next interceptor in line, or finishing with the call to the service provider.
         *
         * @return the result of the call.
         */
        V proceed();
    }

}
