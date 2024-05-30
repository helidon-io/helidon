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
import io.helidon.common.types.TypeName;
import io.helidon.config.Config;
import io.helidon.config.ConfigSources;
import io.helidon.integrations.oci.spi.OciAtnMethod;
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

/*
This class MUST be in the same package, to be able to reset OciConfig before each test
 */
class AtnDetailsProvidersTest {
    private static final TypeName INSTANCE_PRINCIPAL_METHOD_IMPL = TypeName.create(
            "io.helidon.integrations.oci.authentication.instance.AuthenticationMethodInstancePrincipal");
    private static final TypeName RESOURCE_PRINCIPAL_METHOD_IMPL = TypeName.create(
            "io.helidon.integrations.oci.authentication.resource.AuthenticationMethodResourcePrincipal");

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
    void testNoMethodAvailable() {
        String yamlConfig = """
                helidon.oci.authentication:
                  config-file:
                    # we must use a file that does not exist, if this machine has actual oci config file
                    path: src/test/resources/test-oci-config-not-there
                """;
        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        setUp(config);

        OciAtnMethod atnMethod = registry.get(AuthenticationMethodConfig.class);
        assertThat(atnMethod.method(), is(AuthenticationMethodConfig.METHOD));
        assertThat(atnMethod.provider(), optionalEmpty());

        atnMethod = registry.get(AuthenticationMethodConfigFile.class);
        assertThat(atnMethod.method(), is(AuthenticationMethodConfigFile.METHOD));
        assertThat(atnMethod.provider(), optionalEmpty());

        atnMethod = registry.get(INSTANCE_PRINCIPAL_METHOD_IMPL);
        assertThat(atnMethod.method(), is("instance-principal"));
        assertThat(atnMethod.provider(), optionalEmpty());

        atnMethod = registry.get(RESOURCE_PRINCIPAL_METHOD_IMPL);
        assertThat(atnMethod.method(), is("resource-principal"));
        assertThat(atnMethod.provider(), optionalEmpty());

        assertThat(registry.first(Region.class), is(Optional.empty()));
        assertThat(registry.first(AbstractAuthenticationDetailsProvider.class), is(Optional.empty()));
    }

    @Test
    void testRegionFromConfig() {
        String yamlConfig = """
                helidon.oci:
                  region: us-phoenix-1
                  authentication:
                    config-file:
                    # we must use a file that does not exist, if this machine has actual oci config file
                    path: src/test/resources/test-oci-config-not-there
                """;
        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        setUp(config);

        assertThat(registry.first(Region.class), optionalValue(is(Region.US_PHOENIX_1)));
    }

    @Test
    void testConfigMethodAvailable() {
        String yamlConfig = """
                helidon.oci.authentication:
                  config:
                    # region must be real, so it can be parsed
                    region: us-phoenix-1
                    fingerprint: fp
                    passphrase: passphrase
                    tenant-id: tenant
                    user-id: user
                  config-file:
                    # we must use a file that does not exist, if this machine has actual oci config file
                    path: src/test/resources/test-oci-config-not-there
                """;
        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        setUp(config);

        OciAtnMethod atnMethod = registry.get(AuthenticationMethodConfig.class);
        assertThat(atnMethod.method(), is(AuthenticationMethodConfig.METHOD));
        assertThat(atnMethod.provider(), not(Optional.empty()));

        atnMethod = registry.get(AuthenticationMethodConfigFile.class);
        assertThat(atnMethod.method(), is(AuthenticationMethodConfigFile.METHOD));
        assertThat(atnMethod.provider(), optionalEmpty());

        atnMethod = registry.get(INSTANCE_PRINCIPAL_METHOD_IMPL);
        assertThat(atnMethod.method(), is("instance-principal"));
        assertThat(atnMethod.provider(), optionalEmpty());

        atnMethod = registry.get(RESOURCE_PRINCIPAL_METHOD_IMPL);
        assertThat(atnMethod.method(), is("resource-principal"));
        assertThat(atnMethod.provider(), optionalEmpty());

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
    void testConfigFileMethodAvailable() {
        String yamlConfig = """
                helidon.oci.authentication:
                  config-file:
                    path: src/test/resources/test-oci-config
                    profile: MY_PROFILE
                """;
        Config config = Config.just(ConfigSources.create(yamlConfig, MediaTypes.APPLICATION_YAML));
        setUp(config);

        OciAtnMethod atnMethod = registry.get(AuthenticationMethodConfig.class);
        assertThat(atnMethod.method(), is(AuthenticationMethodConfig.METHOD));
        assertThat(atnMethod.provider(), optionalEmpty());

        atnMethod = registry.get(AuthenticationMethodConfigFile.class);
        assertThat(atnMethod.method(), is(AuthenticationMethodConfigFile.METHOD));
        assertThat(atnMethod.provider(), not(Optional.empty()));

        atnMethod = registry.get(INSTANCE_PRINCIPAL_METHOD_IMPL);
        assertThat(atnMethod.method(), is("instance-principal"));
        assertThat(atnMethod.provider(), optionalEmpty());

        atnMethod = registry.get(RESOURCE_PRINCIPAL_METHOD_IMPL);
        assertThat(atnMethod.method(), is("resource-principal"));
        assertThat(atnMethod.provider(), optionalEmpty());

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
