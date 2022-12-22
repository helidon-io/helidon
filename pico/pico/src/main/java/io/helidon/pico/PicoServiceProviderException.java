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
 * An exception relative to a {@link io.helidon.pico.ServiceProvider}.
 */
public class PicoServiceProviderException extends PicoException {

    /**
     * The service provider this exception pertains.
     */
    private final ServiceProvider<?> serviceProvider;

    /**
     * A general purpose exception from Pico.
     *
     * @param msg the message
     */
    public PicoServiceProviderException(String msg) {
        super(msg);
        this.serviceProvider = null;
    }

    /**
     * A general purpose exception from Pico.
     *
     * @param msg               the message
     * @param cause             the root cause
     */
    public PicoServiceProviderException(String msg,
                                        Throwable cause) {
        super(msg, cause);

        if (cause instanceof PicoServiceProviderException) {
           this.serviceProvider = ((PicoServiceProviderException) cause).serviceProvider().orElse(null);
        } else {
           this.serviceProvider = null;
        }
    }

    /**
     * A general purpose exception from Pico.
     *
     * @param msg               the message
     * @param serviceProvider   the service provider
     */
    public PicoServiceProviderException(String msg,
                                        ServiceProvider<?> serviceProvider) {
        super(msg);
        Objects.requireNonNull(serviceProvider);
        this.serviceProvider = serviceProvider;
    }

    /**
     * A general purpose exception from Pico.
     *
     * @param msg               the message
     * @param cause             the root cause
     * @param serviceProvider   the service provider
     */
    public PicoServiceProviderException(String msg,
                                        Throwable cause,
                                        ServiceProvider<?> serviceProvider) {
        super(msg, cause);
        Objects.requireNonNull(serviceProvider);
        this.serviceProvider = serviceProvider;
    }

    /**
     * The service provider that this exception pertains to, or empty if not related to any particular provider.
     *
     * @return the optional / contextual service provider
     */
    public Optional<ServiceProvider<?>> serviceProvider() {
        return Optional.ofNullable(serviceProvider);
    }

    @Override
    public String getMessage() {
        return super.getMessage()
                + (Objects.isNull(serviceProvider)
                           ? "" : (": service provider: " + serviceProvider));
    }

}
