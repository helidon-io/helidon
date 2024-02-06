package io.helidon.service.test.registry;

import java.util.Map;

import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.service.registry.ServiceRegistryConfig;

import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsEmptyCollection.empty;

public class RegistryConfigTest {
    @Test
    void testDefaults() {
        ServiceRegistryConfig cfg = ServiceRegistryConfig.create();
        assertThat(cfg.config(), is(optionalEmpty()));
        assertThat(cfg.discoverServices(), is(true));
        assertThat(cfg.serviceDescriptors(), is(empty()));
        assertThat(cfg.serviceInstances().size(), is(0));
    }

    @Test
    void testFromConfig() {
        Config config = Config.builder(
                        ConfigSources.create(
                                Map.of("registry.discover-services", "false"
                                ), "config-1"))
                .disableEnvironmentVariablesSource()
                .disableSystemPropertiesSource()
                .build();
        Config injectConfig = config.get("registry");
        ServiceRegistryConfig cfg = ServiceRegistryConfig.create(injectConfig);

        assertThat(cfg.config(), optionalValue(sameInstance(injectConfig)));
        assertThat(cfg.discoverServices(), is(false));
        assertThat(cfg.serviceDescriptors(), is(empty()));
        assertThat(cfg.serviceInstances().size(), is(0));
    }
}
