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

package io.helidon.webclient.api;

import java.util.Optional;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;

/**
 * Client-side TLS SNI configuration.
 */
@Prototype.Configured
@Prototype.Blueprint(decorator = SniConfigSupport.BuilderDecorator.class)
interface SniConfigBlueprint {
    /**
     * TLS peer host source mode for SNI and endpoint identification: {@code uri-host}, {@code host-header},
     * {@code explicit}, or {@code disabled}.
     * <p>
     * DNS names are sent as SNI server names. IP literals are used for endpoint identification without a DNS SNI
     * server name. {@code disabled} clears the SNI server name without disabling endpoint identification.
     *
     * @return SNI mode
     */
    @Option.Configured
    @Option.Default("URI_HOST")
    SniMode mode();

    /**
     * Explicit TLS peer host used when {@code mode} is {@code explicit}.
     * DNS hosts are sent as SNI server names and used for endpoint identification. IP literals are used for endpoint
     * identification without a DNS SNI server name.
     *
     * @return explicit TLS peer host
     */
    @Option.Configured
    Optional<String> host();
}
