package io.helidon.openapi;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for OpenAPI feature for {@link io.helidon.webserver.WebServer}.
 */
@Weight(OpenApiFeature.WEIGHT)
public class OpenApiFeatureProvider implements ServerFeatureProvider<OpenApiFeature> {
    /**
     * Required for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public OpenApiFeatureProvider() {
    }

    @Override
    public String configKey() {
        return OpenApiFeature.OPENAPI_ID;
    }

    @Override
    public OpenApiFeature create(Config config, String name) {
        return OpenApiFeature.builder()
                .config(config)
                .name(name)
                .build();
    }
}
