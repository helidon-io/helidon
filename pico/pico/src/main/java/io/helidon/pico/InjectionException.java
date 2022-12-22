/*
 * Copyright (c) 2022, 2023 Oracle and/or its affiliates.
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

import java.util.Objects;
import java.util.Optional;

/**
 * Represents an injection exception. These might be thrown either at compile time or at runtime depending upon how the
 * application is built.
 */
public class InjectionException extends PicoServiceProviderException {

    /**
     * The optional activation log (configure to enabled).
     *
     * @see PicoServicesConfig#activationLogs()
     */
    private final ActivationLog activationLog;

    /**
     * Injection, or a required service lookup related exception.
     *
     * @param msg the message
     */
    public InjectionException(String msg) {
        super(msg);
        this.activationLog = null;
    }

    /**
     * Injection, or a required service lookup related exception.
     *
     * @param msg               the message
     * @param cause             the root cause
     * @param serviceProvider   the service provider
     */
    public InjectionException(String msg,
                              Throwable cause,
                              ServiceProvider<?> serviceProvider) {
        super(msg, cause, serviceProvider);
        this.activationLog = null;
    }

    /**
     * Injection, or a required service lookup related exception.
     *
     * @param msg               the message
     * @param serviceProvider   the service provider
     * @param log               the activity log
     */
    public InjectionException(String msg,
                              ServiceProvider<?> serviceProvider,
                              ActivationLog log) {
        super(msg, serviceProvider);
        Objects.requireNonNull(log);
        this.activationLog = log;
    }

    /**
     * Injection, or a required service lookup related exception.
     *
     * @param msg               the message
     * @param cause             the root cause
     * @param serviceProvider   the service provider
     * @param log               the activity log
     */
    public InjectionException(String msg,
                              Throwable cause,
                              ServiceProvider<?> serviceProvider,
                              ActivationLog log) {
        super(msg, cause, serviceProvider);
        Objects.requireNonNull(log);
        this.activationLog = log;
    }

    /**
     * Returns the activation log if available.
     *
     * @return the optional activation log
     */
    public Optional<ActivationLog> activationLog() {
        return Optional.ofNullable(activationLog);
    }

}
