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

import io.helidon.common.Api;

/**
 * Configuration of a listener transport binding.
 * Add a transport configuration through
 * {@link io.helidon.webserver.ListenerConfig.BuilderBase#addBinding(TransportConfig)}.
 * The listener supplies the endpoint, TLS, routing, and limits shared by its bindings.
 */
@Api.Incubating
public interface TransportConfig {
    /**
     * Transport type, which is the binding's sole identity and configuration key.
     *
     * @return transport type
     */
    String type();

    /**
     * Whether this binding is enabled.
     *
     * @return whether this binding is enabled
     */
    boolean enabled();

    /**
     * Whether this binding is required to become active.
     *
     * @return whether this binding is required
     */
    boolean required();
}
