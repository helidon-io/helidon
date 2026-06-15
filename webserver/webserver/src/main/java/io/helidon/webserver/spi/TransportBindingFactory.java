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

package io.helidon.webserver.spi;

import io.helidon.config.NamedService;
import io.helidon.webserver.BindingPlanContext;
import io.helidon.webserver.TransportBindingContext;

/**
 * Factory for a configured transport binding.
 */
public interface TransportBindingFactory extends NamedService {
    /**
     * Whether this binding factory is enabled.
     *
     * @return whether this binding factory is enabled
     */
    default boolean enabled() {
        return true;
    }

    /**
     * Whether this binding factory is required to become active.
     *
     * @return whether this binding factory is required
     */
    default boolean required() {
        return false;
    }

    /**
     * Whether this binding factory can bind with the current listener configuration.
     *
     * @param context listener binding planning view
     * @return whether this binding factory can bind for the listener
     */
    boolean canBind(BindingPlanContext context);

    /**
     * Create a binding for the transport.
     * <p>
     * This method is called while the listener plans bindings, before listener capability validation has completed.
     * Implementations must not bind sockets, allocate transport runtime resources, or start background work here. Resource
     * allocation belongs to {@link TransportBinding#start()}; otherwise a later planning failure would have no lifecycle
     * path to release those resources.
     *
     * @param context logical listener context
     * @return created transport binding, never {@code null}
     */
    TransportBinding create(TransportBindingContext context);
}
