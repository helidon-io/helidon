package io.helidon.webserver.accesslog;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for context feature for {@link io.helidon.webserver.WebServer}.
 */
@Weight(AccessLogFeature.WEIGHT)
public class AccessLogFeatureProvider implements ServerFeatureProvider<AccessLogFeature> {
    /**
     * Required for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public AccessLogFeatureProvider() {
    }

    @Override
    public String configKey() {
        return "context";
    }

    @Override
    public AccessLogFeature create(Config config, String name) {
        return AccessLogFeature.builder()
                .config(config)
                .name(name)
                .build();
    }
}
