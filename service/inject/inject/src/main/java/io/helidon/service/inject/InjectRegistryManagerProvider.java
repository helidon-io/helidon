package io.helidon.service.inject;

import io.helidon.service.registry.ServiceDiscovery;
import io.helidon.service.registry.ServiceRegistryConfig;
import io.helidon.service.registry.spi.ServiceRegistryManagerProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for
 * {@link io.helidon.service.registry.spi.ServiceRegistryManagerProvider} to provide a service registry
 * with injection and interception support.
 */
public class InjectRegistryManagerProvider implements ServiceRegistryManagerProvider {
    /**
     * Required public constructor.
     *
     * @deprecated required for Java {@link java.util.ServiceLoader}
     */
    @Deprecated
    public InjectRegistryManagerProvider() {
    }

    @Override
    public io.helidon.service.registry.ServiceRegistryManager create(ServiceRegistryConfig config,
                                                                     ServiceDiscovery serviceDiscovery,
                                                                     io.helidon.service.registry.ServiceRegistryManager coreRegistryManager) {
        InjectConfig injectConfig;
        if (config instanceof InjectConfig ic) {
            injectConfig = ic;
        } else {
            injectConfig = InjectConfig.builder()
                    // we need to add appropriate configured options from config (if present)
                    .update(it -> config.config().ifPresent(it::config))
                    .from(config)
                    .build();
        }
        return new InjectRegistryManager(injectConfig, serviceDiscovery);
    }
}
