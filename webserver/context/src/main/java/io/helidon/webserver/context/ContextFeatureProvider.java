package io.helidon.webserver.context;

import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for context feature for {@link io.helidon.webserver.WebServer}.
 */
public class ContextFeatureProvider implements ServerFeatureProvider<ContextFeature> {
    /**
     * Required for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public ContextFeatureProvider() {
    }

    @Override
    public String configKey() {
        return "context";
    }

    @Override
    public ContextFeature create(Config config, String name) {
        return ContextFeature.builder()
                .config(config)
                .name(name)
                .build();
    }
}
