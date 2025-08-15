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

import java.lang.System.Logger;
import java.util.Optional;
import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.service.registry.Service.Singleton;
import io.helidon.service.registry.ServiceRegistry;

import static io.helidon.common.config.ConfigBuilderSupport.discoverService;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.System.getLogger;
import static java.util.Objects.requireNonNull;

/**
 * A {@link Supplier} of {@link Optional} {@link EurekaDiscoveryImpl} instances that conforms to the requirements of a
 * <a href="https://helidon.io/docs/latest/se/injection#_factories">Helidon Inject <dfn>factory</dfn></a>.
 *
 * @see EurekaDiscoveryImpl
 */
@Singleton // the scope of the *product*
final class EurekaDiscoveryImplFactory implements Supplier<Optional<EurekaDiscoveryImpl>> {

    /**
     * A {@link Logger} for this class.
     *
     * <p>The {@link Logger}'s {@linkplain Logger#getName() name} is {@code
     * io.helidon.discovery.providers.eureka.EurekaDiscoveryImplFactory}.</p>
     */
    private static final Logger LOGGER = getLogger(EurekaDiscoveryImpl.class.getName());

    /**
     * A {@link ServiceRegistry}.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see #EurekaDiscoveryImplFactory(ServiceRegistry, Config)
     */
    private final ServiceRegistry sr;

    /**
     * A {@link Config}.
     *
     * <p>This field is never {@code null}.</p>
     *
     * @see #EurekaDiscoveryImplFactory(ServiceRegistry, Config)
     */
    private final Config config;

    /**
     * Creates a new {@link EurekaDiscoveryImplFactory}.
     *
     * @param sr a {@link ServiceRegistry}; must not be {@code null}
     *
     * @param config a {@link Config}; must not be {@code null}
     *
     * @exception NullPointerException if any argument is {@code null}
     */
    EurekaDiscoveryImplFactory(ServiceRegistry sr, Config config) {
        super();
        this.sr = requireNonNull(sr, "sr");
        this.config = requireNonNull(config, "config");
        if (LOGGER.isLoggable(DEBUG)) {
            LOGGER.log(DEBUG, "config: " + config);
        }
    }

    @Override // Supplier<Optional<EurekaDiscoveryImpl>>
    public Optional<EurekaDiscoveryImpl> get() {
        // Look for configuration like:
        //
        //   discovery:
        //     eureka: # see EurekaDiscoveryImpl#TYPE
        //       client: # see EurekaDiscoveryConfig#client()
        //         base-uri: "http://localhost:8761/eureka" # see Http1ClientConfig#baseUri()
        //
        // This also handles "enabled" properly, since that logic lives nowhere else but in ConfiguredBuilderSupport. So
        // given:
        //
        //   discovery:
        //     eureka:
        //       enabled: false
        //       client:
        //         base-uri: "http://localhost:8761/eureka"
        //
        // ...discoverService() will return Optional.empty() (enabled is false). Then the lowest weight Discovery
        // implementation, usually a "no-op" implementation, will be used instead.
        return discoverService(this.config,
                               "discovery",
                               Optional.of(this.sr),
                               EurekaDiscoveryImplProvider.class,
                               EurekaDiscoveryImpl.class,
                               true,
                               Optional.empty());
    }

}
