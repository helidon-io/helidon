/*
 * Copyright (c) 2024 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.helidon.integrations.oci;

import java.util.Optional;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.integrations.oci.spi.OciAtnStrategy;
import io.helidon.service.registry.ServiceRegistry;
import io.helidon.service.registry.ServiceRegistryManager;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static io.helidon.common.testing.junit5.OptionalMatcher.optionalEmpty;
import static io.helidon.common.testing.junit5.OptionalMatcher.optionalValue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;

class OciIntegrationTest {

    private ServiceRegistryManager registryManager;
    private ServiceRegistry registry;

    void setUp(Config config) {
        OciConfigProvider.config(OciConfig.create(config.get("helidon.oci")));
        registryManager = ServiceRegistryManager.create();
        registry = registryManager.registry();
    }

    @AfterEach
    void tearDown() {
        registry = null;
        if (registryManager != null) {
            registryManager.shutdown();
        }
    }

    @Test
    void testNoStrategyAvailable() {
        Config config = Config.empty();
        setUp(config);

        OciAtnStrategy atnStrategy = registry.get(AtnStrategyConfig.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyConfig.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        atnStrategy = registry.get(AtnStrategyConfigFile.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyConfigFile.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        atnStrategy = registry.get(AtnStrategyInstancePrincipal.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyInstancePrincipal.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        atnStrategy = registry.get(AtnStrategyResourcePrincipal.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyResourcePrincipal.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        assertThat(registry.first(Region.class), is(Optional.empty()));
        assertThat(registry.first(AbstractAuthenticationDetailsProvider.class), is(Optional.empty()));
    }

    @Test
    void testRegionFromConfig() {
        String yamlConfig = """
                helidon.oci.region: us-phoenix-1
                """;
        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        setUp(config);

        assertThat(registry.first(Region.class), optionalValue(is(Region.US_PHOENIX_1)));
    }

    @Test
    void testConfigStrategyAvailable() {
        String yamlConfig = """
                helidon.oci:
                  config-strategy:
                    # region must be real, so it can be parsed
                    region: us-phoenix-1
                    fingerprint: fp
                    passphrase: passphrase
                    tenant-id: tenant
                    user-id: user
                """;
        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        setUp(config);

        OciAtnStrategy atnStrategy = registry.get(AtnStrategyConfig.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyConfig.STRATEGY));
        assertThat(atnStrategy.provider(), not(Optional.empty()));

        atnStrategy = registry.get(AtnStrategyConfigFile.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyConfigFile.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        atnStrategy = registry.get(AtnStrategyInstancePrincipal.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyInstancePrincipal.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        atnStrategy = registry.get(AtnStrategyResourcePrincipal.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyResourcePrincipal.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        AbstractAuthenticationDetailsProvider provider = registry.get(AbstractAuthenticationDetailsProvider.class);

        assertThat(provider, instanceOf(SimpleAuthenticationDetailsProvider.class));
        SimpleAuthenticationDetailsProvider auth = (SimpleAuthenticationDetailsProvider) provider;
        assertThat(auth.getTenantId(),
                   equalTo("tenant"));
        assertThat(auth.getUserId(),
                   equalTo("user"));
        assertThat(auth.getRegion(),
                   equalTo(Region.US_PHOENIX_1));
        assertThat(new String(auth.getPassphraseCharacters()),
                   equalTo("passphrase"));

        assertThat(registry.first(Region.class), optionalValue(is(Region.US_PHOENIX_1)));
    }

    @Test
    void testConfigFileStrategyAvailable() {
        String yamlConfig = """
                helidon.oci:
                  config-file-strategy:
                    path: src/test/resources/test-oci-config
                    profile: MY_PROFILE
                """;
        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        setUp(config);

        OciAtnStrategy atnStrategy = registry.get(AtnStrategyConfig.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyConfig.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        atnStrategy = registry.get(AtnStrategyConfigFile.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyConfigFile.STRATEGY));
        assertThat(atnStrategy.provider(), not(Optional.empty()));

        atnStrategy = registry.get(AtnStrategyInstancePrincipal.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyInstancePrincipal.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        atnStrategy = registry.get(AtnStrategyResourcePrincipal.class);
        assertThat(atnStrategy.strategy(), is(AtnStrategyResourcePrincipal.STRATEGY));
        assertThat(atnStrategy.provider(), optionalEmpty());

        AbstractAuthenticationDetailsProvider provider = registry.get(AbstractAuthenticationDetailsProvider.class);

        assertThat(provider, instanceOf(ConfigFileAuthenticationDetailsProvider.class));
        ConfigFileAuthenticationDetailsProvider auth = (ConfigFileAuthenticationDetailsProvider) provider;
        assertThat(auth.getTenantId(),
                   equalTo("tenant"));
        assertThat(auth.getUserId(),
                   equalTo("user"));
        assertThat(auth.getRegion(),
                   equalTo(Region.US_PHOENIX_1));
        assertThat(new String(auth.getPassphraseCharacters()),
                   equalTo("passphrase"));

        assertThat(registry.first(Region.class), optionalValue(is(Region.US_PHOENIX_1)));
    }
}
