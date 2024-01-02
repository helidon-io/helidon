/*
 * Copyright (c) 2022, 2024 Oracle and/or its affiliates.
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

package io.helidon.inject;

/**
 * Wraps any checked exceptions that are thrown during the {@link io.helidon.inject.service.Interception.Interceptor} invocations.
 */
public class InvocationException extends InjectionServiceProviderException {

    /**
     * Tracks whether the target being intercepted was called once successfully - meaning that the target was called and it
     * did not result in any exception being thrown.
     */
    private final boolean targetWasCalled;

    /**
     * Constructor.
     *
     * @param msg             the message
     * @param targetWasCalled set to true if the target of interception was ultimately called successfully
     */
    public InvocationException(String msg,
                               boolean targetWasCalled) {
        super(msg);
        this.targetWasCalled = targetWasCalled;
    }

    /**
     * Constructor.
     *
     * @param msg             the message
     * @param cause           the root cause
     * @param targetWasCalled set to true if the target of interception was ultimately called successfully
     */
    public InvocationException(String msg,
                               Throwable cause,
                               boolean targetWasCalled) {
        super(msg, cause);
        this.targetWasCalled = targetWasCalled;
    }

    /**
     * Constructor.
     *
     * @param msg             the message
     * @param cause           the root cause
     * @param serviceProvider the service provider
     * @param targetWasCalled set to true if the target of interception was ultimately called successfully
     */
    public InvocationException(String msg,
                               Throwable cause,
                               RegistryServiceProvider<?> serviceProvider,
                               boolean targetWasCalled) {
        super(msg, cause, serviceProvider);
        this.targetWasCalled = targetWasCalled;
    }

    /**
     * Returns true if the final target of interception was ultimately called.
     *
     * @return if the target being intercepted was ultimately called successfully
     */
    public boolean targetWasCalled() {
        return targetWasCalled;
    }

}
