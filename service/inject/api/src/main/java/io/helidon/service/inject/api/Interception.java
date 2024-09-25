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

package io.helidon.service.inject.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.helidon.service.registry.Service;

/**
 * Interception annotations and types.
 * This is the entry point for any annotation and type related to interception in Helidon Inject.
 */
public final class Interception {
    private Interception() {
    }

    /**
     * Meta-annotation for an annotation that will trigger services annotated with it to become intercepted.
     * This will intercept any method in an annotated type, or an annotated method.
     *
     * @see Interception.Interceptor
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.ANNOTATION_TYPE)
    @Inherited
    public @interface Trigger {
    }

    /**
     * Use this annotation to force generation of an interception delegate for an interface
     * (cannot be applied to classes).
     * The interception delegate can be used from service provider to wrap an instance and support interception.
     * <p>
     * The delegate implements the annotated interface, and has a static method {@code create} with the following parameters:
     * <ul>
     *     <li>{@link io.helidon.service.inject.api.GeneratedInjectService.InterceptionMetadata} - can be injected</li>
     *     <li>{@link io.helidon.service.inject.api.GeneratedInjectService.Descriptor} - descriptor of the service,
     *              to have the correct type annotations, contracts, scope, qualifiers etc.</li>
     *     <li>An instance of the annotated interface to delegate calls to</li>
     * </ul>
     *
     * Why this cannot be applied to classes:
     * Delegation requires an existing delegate instance (which may be obtained in any way, which may have private or
     * inaccessible constructor(s), may be final, may require constructor parameters that are not known to the registry). As a
     * result, we could not create the delegate instance if it extended a class. Implementing an interface is on the other
     * hand a clean approach.
     * <p>
     * If you feel you need interception for classes that you know how to construct, you can create a service that extends
     * the desired class and provide it as a service to the registry. Such services are automatically intercepted as expected.
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Delegate {
    }

    /**
     * Implementors of this contract must be {@link io.helidon.service.inject.api.Injection.Named}
     * according to the {@link Interception.Trigger} annotation they support.
     */
    @Service.Contract
    public interface Interceptor {

        /**
         * Called during interception of the target V. The implementation typically should finish with the call to
         * {@link Interception.Interceptor.Chain#proceed}.
         *
         * @param ctx   the invocation context
         * @param chain the chain to call proceed on
         * @param args  the arguments to the call
         * @param <V>   the return value type (or {@link Void} for void method elements)
         * @return the return value to the caller
         * @throws Exception if there are any checked exceptions thrown by the underlying method, or any runtime exception thrown
         */
        <V> V proceed(InvocationContext ctx, Chain<V> chain, Object... args) throws Exception;

        /**
         * Represents the next in line for interception, terminating with a call to the wrapped service provider.
         *
         * @param <V> the return value
         */
        interface Chain<V> {
            /**
             * Call the next interceptor in line, or finish with the call to the service being intercepted.
             * Note that that arguments are passed by reference to each interceptor ultimately leading up to the final
             * call to the underlying intercepted target. Callers can mutate the arguments passed directly on the provided array
             * instance.
             *
             * @param args the arguments passed
             * @return the result of the call
             * @throws Exception may throw any checked exceptions thrown by the underlying method, or any runtime exception
             *                   thrown
             */
            V proceed(Object[] args) throws Exception;
        }
    }
}
