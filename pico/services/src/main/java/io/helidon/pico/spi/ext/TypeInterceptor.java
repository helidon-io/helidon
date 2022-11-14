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

package io.helidon.pico.spi.ext;

import io.helidon.pico.spi.Contract;
import io.helidon.pico.InjectionException;

import jakarta.inject.Provider;

/**
 * A simple contract that can be used for interception and decoration of an entire type. The supported use case involves
 * an intercepted type is a {@link io.helidon.pico.spi.Contract} registered to a service in the {@link io.helidon.pico.Services}
 * registry. Note that the service type implementation must also implement the contract of the type T being intercepted, as
 * well as be of higher weight that the service implementation being implemented. Once these conditions are met,
 * then the interceptor implementation then can perform before, after processing for all methods of type T delegation.
 * Generally speaking, the tools module provides automation around interception usage patterns, so refer to that module
 * for further details.
 *
 * @param <T> the type to intercept
 */
@Contract
public interface TypeInterceptor<T> {

    /**
     * Called prior to the method call to the delegate provider.
     *
     * @param delegate      the delegate provider
     * @param methodName    the method name about to be called
     * @param methodArgs    the arguments to the method name to be called.
     */
    default void beforeCall(Provider<T> delegate, String methodName, Object... methodArgs) {
        // nop
    }

    /**
     * Called after the method call to the delegate provider.
     *
     * @param delegate      the delegate provider
     * @param result        the result of the call after delegation
     * @param methodName    the method name about to be called
     * @param methodArgs    the arguments to the method name to be called.
     */
    default void afterCall(Provider<T> delegate, Object result, String methodName, Object... methodArgs) {
        // nop
    }

    /**
     * Called if the method invocation to the provider failed with a throwable error.
     *
     * @param failure       the throwable from the call to the delegate
     * @param delegate      the delegate provider
     * @param methodName    the method name about to be called
     * @param methodArgs    the arguments to the method name to be called.
     *
     * @return the runtime exception that should be thrown if the failure type is not able to be handled directly
     */
    default RuntimeException afterFailedCall(Throwable failure, Provider<T> delegate, String methodName, Object... methodArgs) {
        if (failure instanceof RuntimeException) {
            return ((RuntimeException) failure);
        }
        return new InjectionException("interceptor failure for " + delegate, failure, null);
    }

    /**
     * Determines the literal interceptor to apply for a particular provider type delegate.
     *
     * @param delegate      the delegate provider
     *
     * @return the default implementation returns this instance
     */
    default TypeInterceptor<T> interceptorFor(Provider<T> delegate) {
        return this;
    }

    /**
     * Determines the literal provider to apply for a particular provider type delegate.
     *
     * @param delegate      the delegate provider
     * @param methodName    the method name about to be called
     * @param methodArgs    the arguments to the method name to be called.
     *
     * @return the default implementation returns this delegate that was passed in
     */
    default Provider<T> providerFor(Provider<T> delegate, String methodName, Object... methodArgs) {
        return delegate;
    }

}
