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

import io.helidon.config.ConfiguredProvider;
import io.helidon.webserver.BindingPlanContext;
import io.helidon.webserver.TransportBindingContext;

/**
 * Provider of transport bindings.
 *
 * @param <T> transport binding configuration type
 */
public interface TransportBindingProvider<T extends TransportBindingConfig> extends ConfiguredProvider<T> {
    /**
     * Supported binding config type.
     *
     * @return binding config class
     */
    Class<T> configType();

    /**
     * Whether this binding configuration can bind with the current listener endpoint configuration.
     *
     * @param context listener binding planning view
     * @param config binding configuration
     * @return whether this binding configuration can bind for the listener
     */
    boolean canBind(BindingPlanContext context, T config);

    /**
     * Create a binding for the transport.
     * <p>
     * This method is called while the listener plans bindings, before listener capability validation has completed.
     * Implementations must not bind sockets, allocate transport runtime resources, or start background work here. Resource
     * allocation belongs to {@link TransportBinding#start()}; otherwise a later planning failure would have no lifecycle
     * path to release those resources.
     *
     * @param context logical listener context
     * @param config binding configuration
     * @return created transport binding, never {@code null}
     */
    TransportBinding create(TransportBindingContext context, T config);
}
