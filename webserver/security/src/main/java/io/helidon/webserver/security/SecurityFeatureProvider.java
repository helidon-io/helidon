package io.helidon.webserver.security;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation of {@link io.helidon.webserver.spi.ServerFeatureProvider}
 * for security.
 */
@Weight(SecurityFeature.WEIGHT)
public class SecurityFeatureProvider implements ServerFeatureProvider<SecurityFeature> {
    @Override
    public String configKey() {
        return SecurityFeature.SECURITY_ID;
    }

    @Override
    public SecurityFeature create(Config config, String name) {
        return SecurityFeature.builder()
                .name(name)
                .config(config)
                .build();
    }
}
