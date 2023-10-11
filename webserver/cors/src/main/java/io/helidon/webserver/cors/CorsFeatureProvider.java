package io.helidon.webserver.cors;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for context feature for {@link io.helidon.webserver.WebServer}.
 */
@Weight(CorsFeature.WEIGHT)
public class CorsFeatureProvider implements ServerFeatureProvider<CorsFeature> {
    /**
     * Required for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public CorsFeatureProvider() {
    }

    @Override
    public String configKey() {
        return CorsFeature.CORS_ID;
    }

    @Override
    public CorsFeature create(Config config, String name) {
        return CorsFeature.builder()
                .config(config)
                .name(name)
                .build();
    }
}
