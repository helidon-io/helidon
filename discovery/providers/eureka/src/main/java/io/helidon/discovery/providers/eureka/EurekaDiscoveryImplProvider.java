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
package io.helidon.discovery.providers.eureka;

import io.helidon.common.config.Config;
import io.helidon.common.config.ConfiguredProvider;
import io.helidon.service.registry.Service.Singleton;

/**
 * A {@link ConfiguredProvider} implementation that {@linkplain #create(Config, String) creates} {@link
 * EurekaDiscoveryImpl} instances.
 *
 * @see #create(Config, String)
 *
 * @see EurekaDiscoveryImplFactory#get()
 */
@Singleton
final class EurekaDiscoveryImplProvider implements ConfiguredProvider<EurekaDiscoveryImpl> {

    /**
     * Creates a new {@link EurekaDiscoveryImplProvider}.
     *
     * @deprecated Invoked by the {@linkplain io.helidon.service.registry.ServiceRegistry service registry} only.
     */
    @Deprecated
    EurekaDiscoveryImplProvider() {
        super();
    }

    /**
     * Returns {@code eureka} when invoked, as part of implementing the {@link ConfiguredProvider
     * ConfiguredProvider&lt;EurekaDiscoveryImpl&gt;} contract.
     *
     * @return "{@code eureka}" when invoked
     *
     * @see ConfiguredProvider#configKey()
     *
     * @see EurekaDiscoveryImpl#TYPE
     */
    @Override // DiscoveryProvider<Discovery> (ConfiguredProvider<Discovery & NamedService>)
    public String configKey() {
        // In general, given a (YAML) configuration like this:
        //
        //   whatever:
        //     providers:
        //       - type: "x"
        //         foo: "bar"
        //
        // Or:
        //
        //   whatever:
        //     x:
        //       foo: "bar"
        //
        // ...return "x".
        //
        // See ConfiguredProvider javadoc for all the details of the contract.
        return EurekaDiscoveryImpl.TYPE; // "eureka"; same as EurekaDiscoveryImpl#type()
    }

    /**
     * Implements the {@link ConfiguredProvider ConfiguredProvider&lt;EurekaDiscoveryImpl&gt;} contract by {@linkplain
     * EurekaDiscoveryConfig.Builder#build() building} a new {@link EurekaDiscovery} {@linkplain
     * EurekaDiscoveryConfig.Builder#config(Config) configured with} values sourced from the supplied {@link Config} and
     * {@linkplain EurekaDiscoveryConfig.Builder#name(String) with} the supplied {@code name} and returns it.
     *
     * @param config a {@link Config}; must not be {@code null}
     *
     * @param name a name; must not be {@code null}
     *
     * @return a new {@link EurekaDiscovery} instance
     *
     * @exception NullPointerException if any argument is {@code null}
     *
     * @see ConfiguredProvider#create(Config, String)
     */
    @Override // ConfiguredProvider<EurekaDiscoveryImpl>
    public EurekaDiscoveryImpl create(Config config, String name) {
        return (EurekaDiscoveryImpl) EurekaDiscoveryConfig.builder()
            .config(config)
            .name(name)
            .build();
    }

}
