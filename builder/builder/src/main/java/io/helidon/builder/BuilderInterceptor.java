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

package io.helidon.builder;

/**
 * Provides a contract by which the {@link Builder}-annotated builder type can be intercepted (i.e., including decoration or
 * mutation).
 * <p>
 * This type is used when {@link Builder#requireLibraryDependencies} is used. When it is turned off, however, an equivalent
 * type will be code-generated into each generated bean.
 * Note also that in this situation your interceptor implementation does not need to implement this interface contract,
 * but instead must adhere to the following:
 * <ul>
 *     <li>The implementation class type must provide a no-arg accessible constructor available to the generated class, unless
 *          the {@link io.helidon.builder.Builder#interceptorCreateMethod()} is used.
 *     <li>The implementation class type must provide a method-compatible (lambda) signature to the {@link #intercept} method.
 *     <li>Any exceptions that might be thrown from the {@link #intercept} method must be an unchecked exception type.
 * </ul>
 *
 * @param <T> the type of the bean builder to intercept
 *
 * @see io.helidon.builder.Builder#interceptor()
 */
@FunctionalInterface
public interface BuilderInterceptor<T> {

    /**
     * Provides the ability to intercept (i.e., including decoration or mutation) the target.
     *
     * @param target  the target being intercepted
     * @return the mutated or replaced target (must not be null)
     */
    T intercept(T target);

}
