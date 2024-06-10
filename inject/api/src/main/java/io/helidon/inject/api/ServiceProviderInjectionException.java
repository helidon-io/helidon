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

package io.helidon.inject.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an injection exception. These might be thrown either at compile time or at runtime depending upon how the
 * application is built.
 * @deprecated Helidon inject is deprecated and will be replaced in a future version
 */
@Deprecated(forRemoval = true, since = "4.0.8")
public class ServiceProviderInjectionException extends InjectionServiceProviderException {

    /**
     * The optional activation log (configure to enabled).
     *
     * @see InjectionServicesConfig#activationLogs()
     */
    private ActivationLog activationLog;

    /**
     * Injection, or a required service lookup related exception.
     *
     * @param msg the message
     */
    public ServiceProviderInjectionException(String msg) {
        super(msg);
    }

    /**
     * Injection, or a required service lookup related exception.
     *
     * @param msg               the message
     * @param cause             the root cause
     * @param serviceProvider   the service provider
     */
    public ServiceProviderInjectionException(String msg,
                                             Throwable cause,
                                             ServiceProvider<?> serviceProvider) {
        super(msg, cause, serviceProvider);
    }

    /**
     * Injection, or a required service lookup related exception.
     *
     * @param msg               the message
     * @param serviceProvider   the service provider
     */
    public ServiceProviderInjectionException(String msg,
                                             ServiceProvider<?> serviceProvider) {
        super(msg, serviceProvider);
    }

    /**
     * Returns the activation log if available.
     *
     * @return the optional activation log
     */
    public Optional<ActivationLog> activationLog() {
        return Optional.ofNullable(activationLog);
    }

    /**
     * Sets the activation log on this exception instance.
     *
     * @param log the activation log
     * @return this exception instance
     */
    public ServiceProviderInjectionException activationLog(ActivationLog log) {
        this.activationLog = Objects.requireNonNull(log);
        return this;
    }

}
