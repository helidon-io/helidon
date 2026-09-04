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

package io.helidon.webclient.spi;

import java.util.concurrent.CompletionStage;

import io.helidon.common.Api;
import io.helidon.http.HttpTransportObserver;

/**
 * Provides a per-WebClient registration for observing HTTP transport lifecycle.
 */
@Api.Internal
public interface WebClientTransportObserverProvider {
    /**
     * Identity which scopes observations and shared transport caches.
     *
     * <p>Providers returning the same object instance are registered once for a WebClient and may share physical
     * connections. The identity is compared by reference.
     *
     * @return observer identity
     */
    default Object transportObserverIdentity() {
        return this;
    }

    /**
     * Opens a new registration owned by one WebClient runtime.
     *
     * @return transport observer registration
     */
    Registration openTransportObserver();

    /**
     * Per-WebClient transport observer registration.
     */
    interface Registration extends AutoCloseable {
        /**
         * Observer used by the WebClient transports.
         *
         * @return transport observer
         */
        HttpTransportObserver observer();

        /**
         * Starts non-blocking release of this registration.
         */
        @Override
        void close();

        /**
         * Completion of asynchronous registration release.
         *
         * @return release completion
         */
        CompletionStage<Void> completion();
    }
}
