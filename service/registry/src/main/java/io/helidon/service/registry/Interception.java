/*
 * Copyright (c) 2023, 2025 Oracle and/or its affiliates.
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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Interception annotations and types.
 * This is the entry point for any annotation and type related to interception in Helidon Service Registry.
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
    public @interface Intercepted {
    }

    /**
     * Use this annotation to mark a class ready for interception delegation.
     * The delegates are code generated automatically if a service factory (such as a {@link java.util.function.Supplier})
     * provides an instance of a class (or provides an interface implementation) that has methods
     * annotated with interception trigger(s).
     * <p>
     * Classes are by default not good candidates for interception, so they MUST be annotated either with
     * this annotation, or referenced via {@link Interception.ExternalDelegate}.
     * <p>
     * Implementing a delegate for a class introduces several problems, the biggest one being
     * construction side-effects.
     * <p>
     * If you want delegation for classes to support interception:
     * <ul>
     *     <li>The class must have accessible no-arg constructor (at least package local)</li>
     *     <li>The constructor should have no side effects, as the instance will act only as a wrapper for the delegate</li>
     *     <li>All invoked methods must be accessible (at least package local)</li>
     * </ul>
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface Delegate {
    }

    /**
     * Use this annotation to mark an external class ready for interception delegation.
     * This annotations must be added to the service factory (such as a {@link java.util.function.Supplier})
     * that provides an instance of a class.
     * <p>
     * If the factory provides an interface, this annotation is not needed, as interfaces are safe to delegate.
     *
     * @see Interception.Delegate
     */
    @Documented
    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    public @interface ExternalDelegate {
        /**
         * Type provided by this service factory that is a class and should support interception.
         *
         * @return type that should be intercepted, see {@link Interception.Delegate}
         */
        Class<?> value();
    }

    /**
     * Implementors of this contract must be {@link io.helidon.service.registry.Service.Named}
     * according to the {@link Interception.Intercepted} annotation they support.
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
        <V> V proceed(InterceptionContext ctx, Chain<V> chain, Object... args) throws Exception;

        /**
         * Represents the next in line for interception, terminating with a call to the service instance.
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

    /**
     * Interceptor for a specific element and annotation.
     * Implementations of this interface are {@link io.helidon.service.registry.Service.Named} by the
     * fully qualified target type + "." + element signature.
     */
    public interface ElementInterceptor extends Interceptor {
    }
}
