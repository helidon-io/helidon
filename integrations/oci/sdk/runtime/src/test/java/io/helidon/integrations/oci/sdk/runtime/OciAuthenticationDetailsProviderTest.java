/*
 * Copyright (c) 2023 Oracle and/or its affiliates.
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

package io.helidon.integrations.oci.sdk.runtime;

import java.util.Objects;

import io.helidon.config.Config;

import com.oracle.bmc.Region;
import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

class OciAuthenticationDetailsProviderTest {

    @BeforeEach
    @AfterEach
    void reset() {
        OciExtension.ociConfigFileName(null);
    }

    @Test
    void testCanReadPath() {
        MatcherAssert.assertThat(OciAuthenticationDetailsProvider.canReadPath("./target"),
                                 is(true));
        MatcherAssert.assertThat(OciAuthenticationDetailsProvider.canReadPath("./~bogus~"),
                                 is(false));
    }

    @Test
    void testUserHomePrivateKeyPath() {
        OciConfig ociConfig = Objects.requireNonNull(OciExtension.ociConfig());
        MatcherAssert.assertThat(OciAuthenticationDetailsProvider.userHomePrivateKeyPath(ociConfig),
                                 endsWith("/.oci/oci_api_key.pem"));

        ociConfig = OciConfig.builder(ociConfig)
                .configPath("/decoy/path")
                .authKeyFile("key.pem")
                .build();
        MatcherAssert.assertThat(OciAuthenticationDetailsProvider.userHomePrivateKeyPath(ociConfig),
                                 endsWith("/.oci/key.pem"));
    }

    @Test
    void authStrategiesAvailability() {
        Config config = OciExtensionTest.createTestConfig(
                        OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                        OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", null, null, "region"))
                .get(OciConfig.CONFIG_KEY);
        OciConfig cfg = OciConfig.create(config);
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.AUTO.isAvailable(cfg),
                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.CONFIG.isAvailable(cfg),
                   is(false));
        // this code is dependent upon whether and OCI config-file is present - so leaving this commented out intentionally
//        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.CONFIG_FILE.isAvailable(cfg),
//                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.INSTANCE_PRINCIPALS.isAvailable(cfg),
                   is(false));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.RESOURCE_PRINCIPAL.isAvailable(cfg),
                   is(false));

        config = OciExtensionTest.createTestConfig(
                        OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                        OciExtensionTest.ociAuthConfigFile("./target", null),
                        OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "phrase", "fp", "pk", "pkp", null))
                .get(OciConfig.CONFIG_KEY);
        cfg = OciConfig.create(config);
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.AUTO.isAvailable(cfg),
                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.CONFIG.isAvailable(cfg),
                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.CONFIG_FILE.isAvailable(cfg),
                   is(true));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.INSTANCE_PRINCIPALS.isAvailable(cfg),
                   is(false));
        assertThat(OciAuthenticationDetailsProvider.AuthStrategy.RESOURCE_PRINCIPAL.isAvailable(cfg),
                   is(false));
    }

    @Test
    void selectionWhenNoConfigIsSet() {
        Config config = Config.create();
        assertThat(OciExtension.isSufficientlyConfigured(config),
                   is(false));
    }

    @Test
    void selectionWhenFileConfigIsSetWithAuto() {
        Config config = OciExtensionTest.createTestConfig(
                OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                OciExtensionTest.ociAuthConfigFile("./target", "profile"))
                .get("oci");
        assertThat(OciExtension.isSufficientlyConfigured(config),
                   is(true));
    }

    @Test
    void selectionWhenSimpleConfigIsSetWithAuto() {
        Config config = OciExtensionTest.createTestConfig(
                OciExtensionTest.ociAuthConfigStrategies(OciAuthenticationDetailsProvider.VAL_AUTO),
                OciExtensionTest.ociAuthSimpleConfig("tenant", "user", "passphrase", "fp", "privKey", null, "us-phoenix-1"))
                .get("oci");
        assertThat(OciExtension.isSufficientlyConfigured(config),
                   is(true));

        OciExtension.configSupplier(() -> config);
        AbstractAuthenticationDetailsProvider authProvider = OciExtension.ociAuthenticationProvider().get();
        assertThat(authProvider.getClass(),
                   equalTo(SimpleAuthenticationDetailsProvider.class));
        SimpleAuthenticationDetailsProvider auth = (SimpleAuthenticationDetailsProvider) authProvider;
        assertThat(auth.getTenantId(),
                   equalTo("tenant"));
        assertThat(auth.getUserId(),
                   equalTo("user"));
        assertThat(auth.getRegion(),
                   equalTo(Region.US_PHOENIX_1));
        assertThat(new String(auth.getPassphraseCharacters()),
                   equalTo("passphrase"));
    }

}
