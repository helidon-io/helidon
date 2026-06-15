/*
 * Copyright (c) 2026 Oracle and/or its affiliates.
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

package io.helidon.webserver;

import java.util.OptionalInt;
import java.util.Timer;

import io.helidon.common.concurrency.limits.Limit;
import io.helidon.webserver.spi.TransportBinding;

/**
 * Context shared with listener transport bindings.
 */
public interface TransportBindingContext {
    /**
     * Logical listener context.
     *
     * @return listener context
     */
    ListenerContext listenerContext();

    /**
     * Router shared by the logical listener.
     *
     * @return listener router
     */
    Router router();

    /**
     * Timer shared by transport bindings for listener work.
     *
     * @return listener timer
     */
    Timer timer();

    /**
     * Listener-wide request concurrency limit.
     *
     * @return listener request concurrency limit
     */
    Limit requestLimit();

    /**
     * First bound listener port, if any.
     * <p>
     * Port-capable bindings should use this value when present and when their own endpoint requests a random port, so all
     * port-capable bindings under the same listener converge on one runtime port.
     *
     * @return bound listener port
     */
    default OptionalInt boundPort() {
        return OptionalInt.empty();
    }

    /**
     * Report an unrecoverable runtime failure in a transport binding.
     *
     * @param binding failed binding
     * @param cause failure cause
     */
    void fatalBindingFailure(TransportBinding binding, Throwable cause);
}
