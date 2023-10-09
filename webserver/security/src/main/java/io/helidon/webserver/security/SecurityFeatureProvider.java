package io.helidon.webserver.security;

import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation of {@link io.helidon.webserver.spi.ServerFeatureProvider}
 * for security.
 */
public class SecurityFeatureProvider implements ServerFeatureProvider<SecurityServerFeature> {
    @Override
    public String configKey() {
        return "security";
    }

    @Override
    public SecurityServerFeature create(Config config, String name) {
        return SecurityServerFeature.create(config, name);
    }
}
