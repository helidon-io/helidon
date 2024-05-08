package io.helidon.integrations.oci;

import java.util.function.Supplier;

import io.helidon.common.config.Config;
import io.helidon.service.registry.Service;

@Service.Provider
class OciConfigProvider implements Supplier<OciConfig> {
    private final OciConfig ociConfig;

    OciConfigProvider(Config config) {
        this.ociConfig = config.get("oci").map(OciConfig::create)
                .orElseGet(OciConfig::create);
    }

    @Override
    public OciConfig get() {
        return ociConfig;
    }
}
