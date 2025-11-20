/*
 * Copyright (c) 2025 Oracle and/or its affiliates.
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
package io.helidon.webclient.discovery;

import java.net.URI;
import java.util.Map;

import io.helidon.builder.api.Option;
import io.helidon.builder.api.Prototype;
import io.helidon.discovery.Discovery;

/**
 * A {@linkplain Prototype.Api prototype} and {@linkplain Prototype.Factory factory} for {@link WebClientDiscovery}
 * instances.
 *
 * @see WebClientDiscovery
 * @see WebClientDiscovery#create(WebClientDiscoveryConfig)
 * @see <a href="https://helidon.io/docs/latest/se/builder#_specification">Helidon Builder</a>
 */
@Prototype.Blueprint
@Prototype.Configured
interface WebClientDiscoveryConfigBlueprint extends Prototype.Factory<WebClientDiscovery> {

    /**
     * A {@link Discovery} (normally sourced from the service registry).
     *
     * @return a {@link Discovery}
     */
    @Option.RegistryService
    Discovery discovery();

    /**
     * A {@link Map} of {@link URI} prefixes indexed under discovery names.
     *
     * @return the {@link Map}
     */
    @Option.Configured
    Map<String, URI> prefixUris();

    /**
     * The name to assign to the runtime type ({@code discovery} by default).
     *
     * @return a name
     * @see io.helidon.common.config.NamedService#name()
     */
    @Option.Configured
    @Option.Default("discovery")
    String name();

}
