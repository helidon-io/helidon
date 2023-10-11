package io.helidon.webserver.observe;

import io.helidon.common.Weight;
import io.helidon.common.config.Config;
import io.helidon.webserver.spi.ServerFeatureProvider;

/**
 * {@link java.util.ServiceLoader} provider implementation for observe feature for {@link io.helidon.webserver.WebServer}.
 */
@Weight(ObserveFeature.WEIGHT)
public class ObserveFeatureProvider implements ServerFeatureProvider<ObserveFeature> {
    /**
     * Required for {@link java.util.ServiceLoader}.
     *
     * @deprecated only for {@link java.util.ServiceLoader}
     */
    @Deprecated
    public ObserveFeatureProvider() {
    }

    @Override
    public String configKey() {
        return ObserveFeature.OBSERVE_ID;
    }

    @Override
    public ObserveFeature create(Config config, String name) {
        return ObserveFeature.builder()
                .config(config)
                .name(name)
                .build();
    }
}
