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

import io.helidon.common.Api;
import io.helidon.webserver.http.AltSvcRuntimeRegistry;

/**
 * Internal listener runtime state.
 */
@Api.Internal
public interface ListenerRuntimeContext extends ListenerContext {
    /**
     * Obtain internal runtime state from a listener context.
     *
     * @param listenerContext listener context
     * @return internal listener runtime state
     * @throws IllegalStateException if the listener does not expose runtime state
     */
    static ListenerRuntimeContext get(ListenerContext listenerContext) {
        if (listenerContext instanceof ListenerRuntimeContext runtimeContext) {
            return runtimeContext;
        }
        throw new IllegalStateException("Listener does not expose runtime state: " + listenerContext.getClass().getName());
    }

    /**
     * Alternative service runtimes owned by this listener.
     *
     * @return alternative service runtime registry
     */
    AltSvcRuntimeRegistry altSvcRuntimeRegistry();
}
