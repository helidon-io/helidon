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

import io.helidon.common.config.Config;
import io.helidon.webclient.spi.WebClientServiceProvider;

/**
 * A {@link WebClientServiceProvider} that {@linkplain #create(Config, String) creates} {@link WebClientDiscovery}
 * instances.
 *
 * <p>Instances of this class are normally used only by Helidon internals.</p>
 *
 * @see #create(Config, String)
 * @see WebClientDiscovery
 * @see WebClientDiscoveryConfig#builder()
 */
public final class WebClientDiscoveryProvider implements WebClientServiceProvider {

    /**
     * Creates a new {@link WebClientDiscoveryProvider}.
     */
    public WebClientDiscoveryProvider() {
        super();
    }

    /**
     * Returns {@code discovery} when invoked.
     *
     * @return {@code discovery} when invoked
     */
    @Override // WebClientServiceProvider
    public String configKey() {
        return "discovery";
    }

    /**
     * {@linkplain WebClientDiscoveryConfig#builder() Builds}, {@linkplain
     * WebClientDiscoveryConfig.BuilderBase#config(io.helidon.config.Config) configures}, and returns a new {@link
     * WebClientDiscovery} that will cause any affiliated {@link io.helidon.webclient.api.WebClient} to automatically
     * use a {@link io.helidon.discovery.Discovery} client to {@linkplain io.helidon.discovery.Discovery#uris(String,
     * java.net.URI) discover} URIs.
     *
     * @param config a {@link Config}
     * @param name a service name; normally {@code discovery}
     * @return a new, non-{@code null} {@link WebClientDiscovery} implementation enabling discovery for its affiliated
     * {@link io.helidon.webclient.api.WebClient} instance
     * @exception NullPointerException if {@code config} or {@code name} is {@code null}
     * @see WebClientDiscovery#handle(io.helidon.webclient.spi.WebClientService.Chain,
     * io.helidon.webclient.api.WebClientServiceRequest)
     * @see WebClientDiscoveryConfig#builder()
     */
    @Override // WebClientServiceProvider
    @SuppressWarnings("removal") // Config is deprecated for removal, but our contract (WebClientServiceProvider) mandates its use
    public WebClientDiscovery create(Config config, String name) {
        return WebClientDiscoveryConfig.builder()
            .config(config)
            .name(name) // after config(config) so that the name argument is always honored
            .build();
    }

}
