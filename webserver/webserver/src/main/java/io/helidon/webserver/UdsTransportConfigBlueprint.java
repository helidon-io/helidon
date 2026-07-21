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

import java.net.UnixDomainSocketAddress;
import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.common.Api;
import io.helidon.webserver.spi.TransportBindingFactoryProvider;

/**
 * Configuration of the built-in Unix domain socket listener transport binding.
 */
@Api.Incubating
@Prototype.Blueprint
@Prototype.Configured(root = false, value = TransportBindingTypes.UDS)
@Prototype.Provides(TransportBindingFactoryProvider.class)
@Prototype.CustomMethods(UdsTransportConfigSupport.CustomMethods.class)
interface UdsTransportConfigBlueprint {
    /**
     * Whether this binding is enabled.
     *
     * @return whether this binding is enabled
     */
    @Option.Configured
    @Option.DefaultBoolean(true)
    boolean enabled();

    /**
     * Whether this binding is required to become active.
     *
     * @return whether this binding is required
     */
    @Option.Configured
    @Option.DefaultBoolean(false)
    boolean required();

    /**
     * Unix domain socket address to bind.
     *
     * @return Unix domain socket address
     */
    @Option.Configured
    Optional<UnixDomainSocketAddress> socket();

}
