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

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.webserver.spi.TransportBindingConfig;
import io.helidon.webserver.spi.TransportBindingProvider;

/**
 * Configuration of the built-in TCP listener transport binding.
 */
@Prototype.Blueprint
@Prototype.Configured(root = false, value = TcpTransportBinding.TYPE)
@Prototype.Provides(TransportBindingProvider.class)
interface TcpTransportConfigBlueprint extends TransportBindingConfig {
    /**
     * Name of this binding.
     *
     * @return binding name
     */
    @Override
    @Option.Default(TcpTransportBinding.TYPE)
    String name();

    /**
     * Whether this binding is enabled.
     *
     * @return whether this binding is enabled
     */
    @Override
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Whether this binding is required to become active.
     *
     * @return whether this binding is required
     */
    @Override
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean required();

    /**
     * Binding type.
     *
     * @return binding type
     */
    @Override
    default String type() {
        return TcpTransportBinding.TYPE;
    }
}
